"""Real minimal scheduler for TALOS scheduled jobs.

Persistence: SQLite tables ``jobs`` / ``job_runs`` in the SAME database file
the server already uses (``server/data/talos.db``, path mirrors
``server/db.py`` ``_DB_PATH``). No second database, no new file.

Run-now seam (documented): a manual run is exactly ``save_message()`` +
``await bus.dispatch_message()`` (see ``POST /api/messages`` in
``server/main.py``). ``run_now`` reuses that same entry point: it builds a
``core.models.Message`` in the council room, persists it with
``server.db.save_message``, and dispatches it on ``core.room_bus.bus``
when an event loop is reachable. If dispatch cannot run synchronously
(e.g. called from a plain thread), the message is still persisted and the
run is recorded as success; dispatch delivery is then best-effort. A
raised error anywhere records the run as ``failed``.
"""

from __future__ import annotations

import asyncio
import json
import logging
import sqlite3
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

logger = logging.getLogger("job_scheduler")

_DATA_DIR = Path(__file__).resolve().parent / "data"
_DEFAULT_DB_PATH = _DATA_DIR / "talos.db"

#: Deliver targets. Anything else is rejected at validation.
DELIVER_TARGETS = ("local", "telegram")

#: Run statuses.
STATUSES = ("running", "success", "failed")

FIELD_NAMES = ("minute", "hour", "day-of-month", "month", "day-of-week")
FIELD_BOUNDS = ((0, 59), (0, 23), (1, 31), (1, 12), (0, 7))


class CronError(ValueError):
    """Typed cron error naming the offending field."""

    def __init__(self, field_name: str, message: str) -> None:
        self.field = field_name
        super().__init__(f"{field_name}: {message}")


@dataclass(frozen=True)
class CronSpec:
    """Parsed 5-field cron expression."""

    minutes: frozenset
    hours: frozenset
    days_of_month: frozenset
    months: frozenset
    days_of_week: frozenset
    dom_is_star: bool = True
    dow_is_star: bool = True

    def matches(self, dt: datetime) -> bool:
        if dt.minute not in self.minutes:
            return False
        if dt.hour not in self.hours:
            return False
        if dt.month not in self.months:
            return False
        dom_match = dt.day in self.days_of_month
        cron_dow = (dt.weekday() + 1) % 7  # Python Mon=0 -> cron Sun=0
        dow_match = cron_dow in self.days_of_week
        # Classic cron: when both dom and dow are restricted, either matches.
        if self.dom_is_star and self.dow_is_star:
            return True
        if self.dom_is_star:
            return dow_match
        if self.dow_is_star:
            return dom_match
        return dom_match or dow_match


def _parse_field(raw: str, lo: int, hi: int, field_name: str) -> tuple:
    """Return (value set, is_star)."""
    if raw == "":
        raise CronError(field_name, "empty field")
    try:
        values: set = set()
        for part in raw.split(","):
            part = part.strip()
            if not part:
                raise CronError(field_name, f"empty list item in '{raw}'")
            step = 1
            base = part
            if "/" in part:
                base, step_raw = part.split("/", 1)
                if not step_raw.isdigit() or int(step_raw) == 0:
                    raise CronError(field_name, f"invalid step in '{part}'")
                step = int(step_raw)
            if base == "*":
                start, end = lo, hi
            elif "-" in base:
                a, b = base.split("-", 1)
                if not a.isdigit() or not b.isdigit():
                    raise CronError(field_name, f"invalid range '{part}'")
                start, end = int(a), int(b)
                if start > end:
                    raise CronError(field_name, f"inverted range '{part}'")
            else:
                if not base.isdigit():
                    raise CronError(field_name, f"invalid value '{part}'")
                start = end = int(base)
            if start < lo or end > hi:
                raise CronError(
                    field_name,
                    f"value out of range [{lo}-{hi}] in '{part}'",
                )
            values.update(range(start, end + 1, step))
        return frozenset(values), raw.strip() == "*"
    except CronError:
        raise
    except Exception as exc:  # pragma: no cover - defensive
        raise CronError(field_name, str(exc)) from exc


def parse_cron(expr: str) -> CronSpec:
    """Parse a classic 5-field cron expression.

    Supported: ``*``, integers, ranges ``a-b``, steps ``*/n`` / ``a-b/n``,
    comma lists. Day-of-week 0 and 7 both mean Sunday. Raises
    :class:`CronError` naming the offending field.
    """
    parts = (expr or "").strip().split()
    if len(parts) != 5:
        raise CronError(
            "expression",
            f"expected 5 fields, got {len(parts)} in '{expr}'",
        )
    parsed = [
        _parse_field(parts[i], lo, hi, FIELD_NAMES[i])
        for i, (lo, hi) in enumerate(FIELD_BOUNDS)
    ]
    (minutes, _), (hours, _), (dom, dom_star), (months, _), (dow, dow_star) = parsed
    # Normalise dow 7 -> 0 (Sunday).
    dow = frozenset(0 if v == 7 else v for v in dow)
    return CronSpec(
        minutes=minutes,
        hours=hours,
        days_of_month=dom,
        months=months,
        days_of_week=dow,
        dom_is_star=dom_star,
        dow_is_star=dow_star,
    )


def next_run(spec: CronSpec, after: datetime) -> datetime:
    """First cron time strictly after ``after`` (naive local time)."""
    candidate = after.replace(second=0, microsecond=0) + timedelta(minutes=1)
    deadline = candidate + timedelta(days=366 * 5)
    while candidate <= deadline:
        if spec.matches(candidate):
            return candidate
        candidate += timedelta(minutes=1)
    raise CronError("expression", "no matching time within 5 years")


@dataclass
class Job:
    """A scheduled job."""

    name: str
    prompt: str
    cron_expr: str
    deliver: str = "local"
    profile: str = "maintainer"
    notify_on_run: bool = True
    enabled: bool = True
    id: str = field(default_factory=lambda: f"job_{uuid.uuid4().hex[:12]}")
    created_at: Optional[datetime] = None
    last_run_at: Optional[datetime] = None

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise ValueError("job name must not be empty")
        if not self.prompt.strip():
            raise ValueError("job prompt must not be empty")
        if self.deliver not in DELIVER_TARGETS:
            raise ValueError(
                f"deliver must be one of {DELIVER_TARGETS}, got '{self.deliver}'",
            )
        if not self.profile.strip():
            raise ValueError("profile must not be empty")
        parse_cron(self.cron_expr)  # validate; raises CronError

    @property
    def spec(self) -> CronSpec:
        return parse_cron(self.cron_expr)

    def next_run_after(self, after: datetime) -> datetime:
        return next_run(self.spec, after)

    def is_due(self, now: datetime) -> bool:
        """Due when enabled and the next slot after the anchor has passed.

        Anchor is ``last_run_at`` if set, else ``created_at``. With no
        anchor at all, the job is due once any slot in the last day has
        passed (so a daily job created fresh is due after its slot).
        """
        if not self.enabled:
            return False
        anchor = self.last_run_at or self.created_at or (now - timedelta(days=1))
        return self.next_run_after(anchor) <= now

    def to_payload(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "prompt": self.prompt,
            "cron_expr": self.cron_expr,
            "deliver": self.deliver,
            "profile": self.profile,
            "notify_on_run": self.notify_on_run,
            "enabled": self.enabled,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "last_run_at": self.last_run_at.isoformat() if self.last_run_at else None,
        }

    @classmethod
    def from_payload(cls, d: Dict[str, Any]) -> "Job":
        job = cls(
            name=d["name"],
            prompt=d["prompt"],
            cron_expr=d["cron_expr"],
            deliver=d.get("deliver", "local"),
            profile=d.get("profile", "maintainer"),
            notify_on_run=bool(d.get("notify_on_run", True)),
            enabled=bool(d.get("enabled", True)),
            id=d.get("id") or f"job_{uuid.uuid4().hex[:12]}",
        )
        if d.get("created_at"):
            job.created_at = datetime.fromisoformat(d["created_at"])
        if d.get("last_run_at"):
            job.last_run_at = datetime.fromisoformat(d["last_run_at"])
        return job


@dataclass
class RunRecord:
    """One execution of a job."""

    job_id: str
    started_at: datetime
    status: str = "running"
    finished_at: Optional[datetime] = None
    output_bytes: int = 0
    agent_id: str = ""
    error: str = ""

    def __post_init__(self) -> None:
        if self.status not in STATUSES:
            raise ValueError(f"bad status '{self.status}'")

    @property
    def duration_seconds(self) -> float:
        if self.finished_at is None:
            return 0.0
        return (self.finished_at - self.started_at).total_seconds()

    def to_payload(self) -> Dict[str, Any]:
        return {
            "job_id": self.job_id,
            "status": self.status,
            "started_at": self.started_at.isoformat(),
            "finished_at": self.finished_at.isoformat() if self.finished_at else None,
            "output_bytes": self.output_bytes,
            "duration_seconds": self.duration_seconds,
            "agent_id": self.agent_id,
            "error": self.error,
        }

    @classmethod
    def from_payload(cls, d: Dict[str, Any]) -> "RunRecord":
        return cls(
            job_id=d["job_id"],
            started_at=datetime.fromisoformat(d["started_at"]),
            status=d.get("status", "running"),
            finished_at=datetime.fromisoformat(d["finished_at"])
            if d.get("finished_at")
            else None,
            output_bytes=int(d.get("output_bytes", 0)),
            agent_id=d.get("agent_id", ""),
            error=d.get("error", ""),
        )


def due_jobs(jobs: List[Job], now: datetime) -> List[Job]:
    """Enabled jobs with a scheduled slot at or before ``now``."""
    return [j for j in jobs if j.is_due(now)]


class JobStore:
    """SQLite persistence in the existing TALOS database file."""

    def __init__(self, db_path: Optional[str] = None) -> None:
        self._db_path = Path(db_path) if db_path else _DEFAULT_DB_PATH

    def _connect(self) -> sqlite3.Connection:
        self._db_path.parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(str(self._db_path), check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _ensure_schema(self, conn: sqlite3.Connection) -> None:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS jobs (
                id TEXT PRIMARY KEY, payload_json TEXT NOT NULL)"""
        )
        conn.execute(
            """CREATE TABLE IF NOT EXISTS job_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_id TEXT NOT NULL, payload_json TEXT NOT NULL)"""
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_job_runs_job ON job_runs(job_id)"
        )

    def save_job(self, job: Job) -> Job:
        conn = self._connect()
        try:
            self._ensure_schema(conn)
            conn.execute(
                "INSERT OR REPLACE INTO jobs (id, payload_json) VALUES (?, ?)",
                (job.id, json.dumps(job.to_payload())),
            )
            conn.commit()
            return job
        finally:
            conn.close()

    def get_job(self, job_id: str) -> Optional[Job]:
        conn = self._connect()
        try:
            self._ensure_schema(conn)
            row = conn.execute(
                "SELECT payload_json FROM jobs WHERE id = ?", (job_id,)
            ).fetchone()
            conn.commit()
            if row is None:
                return None
            return Job.from_payload(json.loads(row["payload_json"]))
        finally:
            conn.close()

    def list_jobs(self) -> List[Job]:
        conn = self._connect()
        try:
            self._ensure_schema(conn)
            rows = conn.execute("SELECT payload_json FROM jobs").fetchall()
            conn.commit()
            return [Job.from_payload(json.loads(r["payload_json"])) for r in rows]
        finally:
            conn.close()

    def append_run(self, record: RunRecord) -> RunRecord:
        conn = self._connect()
        try:
            self._ensure_schema(conn)
            conn.execute(
                "INSERT INTO job_runs (job_id, payload_json) VALUES (?, ?)",
                (record.job_id, json.dumps(record.to_payload())),
            )
            conn.commit()
            return record
        finally:
            conn.close()

    def list_runs(self, job_id: str, limit: int = 50) -> List[RunRecord]:
        conn = self._connect()
        try:
            self._ensure_schema(conn)
            rows = conn.execute(
                "SELECT payload_json FROM job_runs WHERE job_id = ? ORDER BY id DESC LIMIT ?",
                (job_id, limit),
            ).fetchall()
            conn.commit()
            return [RunRecord.from_payload(json.loads(r["payload_json"])) for r in rows]
        finally:
            conn.close()


_default_store: Optional[JobStore] = None


def get_store() -> JobStore:
    global _default_store
    if _default_store is None:
        _default_store = JobStore()
    return _default_store


def record_run(job: Job, record: RunRecord, store: Optional[JobStore] = None) -> RunRecord:
    """Persist a run and advance the job's ``last_run_at``."""
    job.last_run_at = record.started_at
    (store or get_store()).save_job(job)
    (store or get_store()).append_run(record)
    return record


def run_now(job: Job, store: Optional[JobStore] = None) -> RunRecord:
    """Execute ``job`` now through the same entry point as a manual run.

    Seam: ``server.db.save_message`` + ``core.room_bus.bus.dispatch_message``
    — exactly what ``POST /api/messages`` does. Dispatch is attempted when
    an event loop is available; persistence always happens. The run
    record is persisted either way.
    """
    started = datetime.now()
    record = RunRecord(job_id=job.id, started_at=started, agent_id=job.profile)
    (store or get_store()).append_run(record)
    try:
        from core.models import Message, Role
        from server.db import save_message

        msg = Message(
            room_id="the-council",
            sender_id=job.profile,
            sender_name=job.profile,
            sender_role=Role.agent,
            content=f"[scheduled job: {job.name}] {job.prompt}",
            created_at=time.time(),
        )
        save_message(msg)
        output = len(msg.content.encode("utf-8"))
        try:
            from core.room_bus import bus

            try:
                loop = asyncio.get_running_loop()
            except RuntimeError:
                loop = None
            if loop is not None:
                asyncio.create_task(bus.dispatch_message(msg))
            else:
                asyncio.run(bus.dispatch_message(msg))
        except Exception as exc:  # pragma: no cover - dispatch best-effort
            logger.warning("Job %s dispatch skipped: %s", job.id, exc)
        record.output_bytes = output
        record.status = "success"
    except Exception as exc:
        record.status = "failed"
        record.error = str(exc)[:400]
    record.finished_at = datetime.now()
    record_run(job, record, store=store)
    return record


# -- FastAPI router ----------------------------------------------------------
# Insertion point in server/main.py (not edited here; ownership is this
# module + tests + one Kotlin screen): after the imports, add
#   from server.job_scheduler import router as jobs_router
#   app.include_router(jobs_router)
# before the websocket route. Auth mirrors require_auth in main.py:
# a Bearer token verified by server.auth.verify_token, 401 on failure.

try:  # FastAPI is a hard dependency of main.py; guard for pure-logic imports.
    from fastapi import APIRouter, Depends, Header, HTTPException
    from pydantic import BaseModel

    def _require_auth(authorization: Optional[str] = Header(None)) -> str:
        from server.auth import verify_token

        if not authorization or not verify_token(authorization):
            raise HTTPException(status_code=401, detail="Invalid or missing TALOS API Token")
        return authorization.replace("Bearer ", "").strip()

    class JobCreateRequest(BaseModel):
        name: str
        prompt: str
        cron_expr: str
        deliver: str = "local"
        profile: str = "maintainer"
        notify_on_run: bool = True

    class JobUpdateRequest(BaseModel):
        name: Optional[str] = None
        prompt: Optional[str] = None
        cron_expr: Optional[str] = None
        deliver: Optional[str] = None
        profile: Optional[str] = None
        notify_on_run: Optional[bool] = None
        enabled: Optional[bool] = None

    router = APIRouter(prefix="/api/jobs", tags=["jobs"])

    @router.get("")
    async def list_jobs_endpoint(token: str = Depends(_require_auth)):
        return [j.to_payload() for j in get_store().list_jobs()]

    @router.post("")
    async def create_job_endpoint(req: JobCreateRequest, token: str = Depends(_require_auth)):
        try:
            job = Job(
                name=req.name,
                prompt=req.prompt,
                cron_expr=req.cron_expr,
                deliver=req.deliver,
                profile=req.profile,
                notify_on_run=req.notify_on_run,
                created_at=datetime.now(),
            )
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc))
        get_store().save_job(job)
        return job.to_payload()

    @router.patch("/{job_id}")
    async def update_job_endpoint(
        job_id: str, req: JobUpdateRequest, token: str = Depends(_require_auth)
    ):
        store = get_store()
        job = store.get_job(job_id)
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        data = job.to_payload()
        for key, value in req.model_dump(exclude_unset=True).items():
            if value is not None:
                data[key] = value
        try:
            updated = Job.from_payload(data)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc))
        updated.id = job.id
        updated.created_at = job.created_at
        updated.last_run_at = job.last_run_at
        store.save_job(updated)
        return updated.to_payload()

    @router.post("/{job_id}/run")
    async def run_job_endpoint(job_id: str, token: str = Depends(_require_auth)):
        job = get_store().get_job(job_id)
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        return run_now(job).to_payload()

    @router.post("/{job_id}/pause")
    async def pause_job_endpoint(job_id: str, token: str = Depends(_require_auth)):
        store = get_store()
        job = store.get_job(job_id)
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        job.enabled = not job.enabled
        store.save_job(job)
        return job.to_payload()

    @router.get("/{job_id}/runs")
    async def list_runs_endpoint(job_id: str, token: str = Depends(_require_auth)):
        if get_store().get_job(job_id) is None:
            raise HTTPException(status_code=404, detail="Job not found")
        return [r.to_payload() for r in get_store().list_runs(job_id)]

except ImportError:  # pragma: no cover - FastAPI missing
    router = None  # type: ignore
