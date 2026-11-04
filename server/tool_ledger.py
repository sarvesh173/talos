"""Tool-call ledger: the "what is actually happening" surface.

The Kanban view answers one question: *what tool is every agent calling right
now, with what arguments, and did it work?* Prose chat logs cannot answer that —
they show the model's summary, not the machinery.

This module is the append-only ledger behind that view. It is deliberately
boring: a bounded in-memory ring per room plus SQLite persistence, because the
question "what did the delegate do at 03:14" outlives the process.

Design constraints that shaped it:

* **Never store secrets.** Arguments pass through :func:`redact` before they are
  written. A tool call that echoes an API key must not turn the ledger into a
  credential dump.
* **Never block the agent.** Recording is fire-and-forget; a failure to persist
  logs a warning and moves on. Losing a ledger row is bad, stalling a turn is
  worse.
* **Bounded.** The in-memory ring caps per room so a runaway loop cannot eat the
  process. The DB keeps the long tail.
"""

from __future__ import annotations

import json
import logging
import re
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

#: Ring size per room. Large enough for a busy afternoon, small enough that a
#: pathological loop cannot exhaust memory.
RING_SIZE = 2000

#: Anything that looks like a credential is replaced before storage.
_SECRET_PATTERNS = (
    re.compile(r"(?i)\b(sk-[A-Za-z0-9_\-]{8,})"),
    re.compile(r"(?i)\b(sk-or-v1-[A-Za-z0-9_\-]{8,})"),
    re.compile(r"(?i)\b(gh[pousr]_[A-Za-z0-9]{20,})"),
    re.compile(r"(?i)\b(xox[baprs]-[A-Za-z0-9\-]{10,})"),
    re.compile(r"(?i)(api[_-]?key\s*[=:]\s*)([^\s,;\"'}]{8,})"),
    re.compile(r"(?i)(bearer\s+)([A-Za-z0-9._\-]{16,})"),
    re.compile(r"(?i)(authorization\s*[=:]\s*)([^\s,;\"'}]{8,})"),
)

REDACTED = "[REDACTED]"


def redact(value: Any, _depth: int = 0) -> Any:
    """Strip credential-shaped strings from anything about to be persisted.

    Recurses through dicts and lists because tool arguments are nested JSON far
    more often than they are flat strings.
    """
    if _depth > 8:
        return value
    if isinstance(value, str):
        out = value
        for pattern in _SECRET_PATTERNS:
            out = pattern.sub(
                lambda m: (m.group(1) + REDACTED) if m.lastindex and m.lastindex > 1 else REDACTED,
                out,
            )
        return out
    if isinstance(value, dict):
        return {
            redact(k, _depth + 1): redact(v, _depth + 1)
            for k, v in list(value.items())[:200]
        }
    if isinstance(value, (list, tuple)):
        return [redact(item, _depth + 1) for item in value[:200]]
    if isinstance(value, float) and value != value:  # NaN
        return None
    return value


@dataclass
class ToolCallRecord:
    """One tool invocation by one agent."""

    id: str
    room_id: str
    agent_id: str
    agent_name: str
    tool: str
    arguments: Any = None
    status: str = "running"  # running | ok | error | denied
    result_summary: str = ""
    error: str = ""
    started_at: float = field(default_factory=time.time)
    finished_at: Optional[float] = None
    #: Set when the call came from a delegate rather than the main agent.
    is_delegate: bool = False
    parent_agent_id: str = ""

    @property
    def duration_ms(self) -> Optional[int]:
        if self.finished_at is None:
            return None
        return int((self.finished_at - self.started_at) * 1000)

    def to_payload(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "room_id": self.room_id,
            "agent_id": self.agent_id,
            "agent_name": self.agent_name,
            "tool": self.tool,
            "arguments": self.arguments,
            "status": self.status,
            "result_summary": self.result_summary,
            "error": self.error,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "duration_ms": self.duration_ms,
            "is_delegate": self.is_delegate,
            "parent_agent_id": self.parent_agent_id,
        }


class ToolLedger:
    """Bounded, redacting, persistent record of every tool call."""

    def __init__(self, ring_size: int = RING_SIZE, persist=None) -> None:
        # No arbitrary floor: a caller asking for a small ring (tests, a
        # constrained device) gets exactly that. Only zero/negative is clamped.
        self._ring_size = max(int(ring_size), 1)
        self._rings: Dict[str, List[ToolCallRecord]] = {}
        #: Optional callable(record_payload) used to persist to SQLite. Injected
        #: so the ledger carries no DB import and stays trivially testable.
        self._persist = persist

    # -- writes ---------------------------------------------------------

    def start(
        self,
        room_id: str,
        agent_id: str,
        agent_name: str,
        tool: str,
        arguments: Any = None,
        is_delegate: bool = False,
        parent_agent_id: str = "",
    ) -> ToolCallRecord:
        record = ToolCallRecord(
            id=f"tool_{uuid.uuid4().hex[:16]}",
            room_id=room_id,
            agent_id=agent_id,
            agent_name=agent_name,
            tool=tool,
            arguments=redact(arguments),
            is_delegate=is_delegate,
            parent_agent_id=parent_agent_id,
        )
        self._append(room_id, record)
        return record

    def finish(
        self,
        record: ToolCallRecord,
        status: str = "ok",
        result_summary: str = "",
        error: str = "",
    ) -> ToolCallRecord:
        record.status = status
        record.result_summary = (redact(result_summary) or "")[:400]
        record.error = (redact(error) or "")[:400]
        record.finished_at = time.time()
        if self._persist is not None:
            try:
                self._persist(record.to_payload())
            except Exception as exc:  # pragma: no cover - persistence is optional
                logger.warning("Tool ledger persist failed: %s", exc)
        return record

    def close_open(
        self,
        room_id: str,
        agent_id: str,
        tool: str,
        status: str = "ok",
        result_summary: str = "",
        error: str = "",
    ) -> Optional[ToolCallRecord]:
        """Close the most recent running call for this agent and tool.

        Needed by out-of-process reporters (the OpenCode plugin), which see a
        start and a finish as two separate HTTP requests and cannot hand back the
        record object. Matching on the newest open row for the pair is correct
        because an agent cannot have two concurrent calls to the same tool within
        one session in practice, and if it can, closing the newest is the least
        wrong choice.
        """
        ring = self._rings.get(room_id, [])
        for record in reversed(ring):
            if (
                record.agent_id == agent_id
                and record.tool == tool
                and record.status == "running"
            ):
                return self.finish(
                    record,
                    status=status,
                    result_summary=result_summary,
                    error=error,
                )
        return None

    def _append(self, room_id: str, record: ToolCallRecord) -> None:
        ring = self._rings.setdefault(room_id, [])
        ring.append(record)
        if len(ring) > self._ring_size:
            # Drop from the front; the DB (when attached) keeps the long tail.
            del ring[: len(ring) - self._ring_size]

    # -- reads ----------------------------------------------------------

    def recent(
        self,
        room_id: str,
        limit: int = 100,
        agent_id: Optional[str] = None,
        delegates_only: bool = False,
        failures_only: bool = False,
    ) -> List[Dict[str, Any]]:
        ring = self._rings.get(room_id, [])
        rows = ring
        if agent_id:
            rows = [r for r in rows if r.agent_id == agent_id]
        if delegates_only:
            rows = [r for r in rows if r.is_delegate]
        if failures_only:
            rows = [r for r in rows if r.status in ("error", "denied")]
        # Bound *after* filtering: the ring already caps storage, but a filtered
        # read must not be able to return more rows than the ring holds.
        return [r.to_payload() for r in rows[-limit:]][::-1]

    def running(self, room_id: Optional[str] = None) -> List[Dict[str, Any]]:
        """Every call still in flight — the live part of the Kanban board."""
        rooms = [room_id] if room_id else list(self._rings.keys())
        out: List[Dict[str, Any]] = []
        for rid in rooms:
            out.extend(r.to_payload() for r in self._rings.get(rid, []) if r.status == "running")
        return sorted(out, key=lambda r: r["started_at"])

    def stats(self, room_id: str) -> Dict[str, Any]:
        """Per-agent, per-tool counters for the header strip."""
        ring = self._rings.get(room_id, [])
        by_agent: Dict[str, Dict[str, Any]] = {}
        by_tool: Dict[str, int] = {}
        failures = 0
        for record in ring:
            agent = by_agent.setdefault(
                record.agent_id,
                {
                    "agent_id": record.agent_id,
                    "agent_name": record.agent_name,
                    "is_delegate": record.is_delegate,
                    "calls": 0,
                    "errors": 0,
                    "running": 0,
                },
            )
            agent["calls"] += 1
            by_tool[record.tool] = by_tool.get(record.tool, 0) + 1
            if record.status in ("error", "denied"):
                agent["errors"] += 1
                failures += 1
            elif record.status == "running":
                agent["running"] += 1
        return {
            "room_id": room_id,
            "total_calls": len(ring),
            "failures": failures,
            "running": sum(1 for r in ring if r.status == "running"),
            "agents": list(by_agent.values()),
            "tools": dict(sorted(by_tool.items(), key=lambda kv: -kv[1])),
        }

    def clear(self, room_id: Optional[str] = None) -> None:
        if room_id:
            self._rings.pop(room_id, None)
        else:
            self._rings.clear()


def summarize_arguments(arguments: Any, limit: int = 160) -> str:
    """One-line argument preview for the compact Kanban row.

    Truncation is on the *rendered* JSON, not the structure, so a nested payload
    still reads as JSON rather than as a cut-off bracketed mess.
    """
    if arguments is None:
        return ""
    if isinstance(arguments, str):
        text = arguments
    else:
        try:
            text = json.dumps(arguments, ensure_ascii=False, default=str)
        except Exception:
            text = str(arguments)
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


#: Process-wide ledger. The orchestrator appends; the HTTP layer reads.
ledger = ToolLedger()
