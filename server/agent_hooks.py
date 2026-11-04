"""Delegate completion notifications with durable, leak-safe events.

A delegate (or the watchdog) calls :func:`notify_completion` when a task
reaches a terminal state. Previously delegates finished silently: nobody was
told, and their work was never integrated. This module gives completion a
durable record and two notification paths:

1. Append one JSON line to ``~/.hermes/talos_delegate_events.jsonl``
   (survives restarts, rotated at 2 MB) — read back via :func:`recent_events`.
2. Best-effort emit of the same payload to the existing room bus
   (:mod:`core.room_bus`) when it is importable. A missing/unreachable bus
   never breaks notification.
3. Best-effort Telegram POST when ``TALOS_TELEGRAM_BOT_TOKEN`` and
   ``TALOS_TELEGRAM_CHAT_ID`` are both present. When either is absent the
   Telegram path is skipped silently and recorded as ``telegram: skipped`` —
   no token is ever invented.

Credential-leak discipline (the user has warned about this before): events
contain only the task id, goal text truncated to 200 chars, status, duration,
and artifact *paths*. File contents, command output, and the free-text
``summary`` are never persisted — only its length is recorded, because a
summary is exactly where a pasted token or command output would end up.
Artifacts supplied as dicts are reduced to their ``path``/``file`` entry.
"""

from __future__ import annotations

import asyncio
import inspect
import json
import logging
import os
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Union

logger = logging.getLogger(__name__)

#: Durable event log. Monkeypatchable in tests; resolved at call time so a
#: patched ``Path.home()`` / env override is honoured.
EVENTS_PATH = Path.home() / ".hermes" / "talos_delegate_events.jsonl"
EVENTS_FILE = EVENTS_PATH
DEFAULT_EVENTS_PATH = EVENTS_PATH

#: Rotate (rename to ``<file>.1``) once the log reaches this size.
MAX_EVENTS_BYTES = 2 * 1024 * 1024

GOAL_MAX_CHARS = 200

_EVENTS_ENV = "TALOS_DELEGATE_EVENTS_PATH"
_TELEGRAM_TOKEN_ENV = "TALOS_TELEGRAM_BOT_TOKEN"
_TELEGRAM_CHAT_ENV = "TALOS_TELEGRAM_CHAT_ID"

Artifact = Union[str, Dict[str, Any]]


def _resolve_events_path(events_path: Optional[Union[str, Path]] = None) -> Path:
    if events_path is not None:
        return Path(events_path)
    env = os.environ.get(_EVENTS_ENV)
    if env:
        return Path(env)
    return Path(EVENTS_PATH)  # module global: patchable in tests


def _artifact_paths(artifacts: Optional[Iterable[Artifact]]) -> List[str]:
    """Reduce artifacts to plain paths; dict/object content is dropped."""
    if artifacts is None:
        return []
    if isinstance(artifacts, (str, bytes)):
        return [str(artifacts)]
    paths: List[str] = []
    for item in artifacts:
        if isinstance(item, dict):
            path = item.get("path") or item.get("file") or item.get("artifact")
            if path:
                paths.append(str(path))
        elif isinstance(item, (str, bytes)):
            paths.append(str(item))
        # Anything else (objects that might carry file content) is ignored:
        # only explicit paths are recorded.
    return paths


def _rotate_if_needed(path: Path) -> None:
    """Bound the log file: rename it aside once it reaches the size cap."""
    try:
        if path.exists() and path.stat().st_size >= MAX_EVENTS_BYTES:
            rotated = path.parent / (path.name + ".1")
            os.replace(path, rotated)
    except OSError as exc:  # rotation failure must not lose the event append
        logger.warning("Delegate event rotation failed for %s: %s", path, exc)


def _append_event(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    _rotate_if_needed(path)
    line = json.dumps(payload, ensure_ascii=False, sort_keys=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def _telegram_text(payload: Dict[str, Any]) -> str:
    lines = [
        f"Delegate {payload['status']}: {payload['task_id']}",
        f"Goal: {payload['goal']}",
        f"Duration: {payload['duration_s']}s",
    ]
    if payload["artifacts"]:
        lines.append("Artifacts: " + ", ".join(payload["artifacts"]))
    return "\n".join(lines)


def _send_telegram(payload: Dict[str, Any]) -> str:
    """POST to the Telegram Bot API if configured, else skip silently.

    Returns ``"skipped"`` (not configured), ``"sent"``, or ``"failed"``.
    Never raises and never logs the token.
    """
    token = os.environ.get(_TELEGRAM_TOKEN_ENV)
    chat_id = os.environ.get(_TELEGRAM_CHAT_ENV)
    if not token or not chat_id:
        return "skipped"
    body = json.dumps(
        {"chat_id": chat_id, "text": _telegram_text(payload)}
    ).encode("utf-8")
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMessage",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return "sent" if 200 <= response.status < 300 else "failed"
    except Exception as exc:  # network, HTTP, timeout — notification is best-effort
        logger.warning("Telegram delegate notification failed: %s", exc)
        return "failed"


def _event_type_for(status: str):
    """Map a terminal status onto the room-bus EventType, if available."""
    try:
        from core.models import EventType

        mapping = {
            "completed": EventType.delegate_completed,
            "success": EventType.delegate_completed,
            "ok": EventType.delegate_completed,
            "done": EventType.delegate_completed,
            "failed": EventType.delegate_failed,
            "error": EventType.delegate_failed,
            "cancelled": EventType.delegate_cancelled,
            "canceled": EventType.delegate_cancelled,
            "expired": EventType.delegate_expired,
            "stalled": EventType.delegate_stalled,
        }
        return mapping.get(str(status).lower(), EventType.delegate_completed)
    except Exception:
        return None


def _emit_to_bus(payload: Dict[str, Any], bus: Any = None) -> str:
    """Emit the same payload to the room bus if one is importable.

    Returns ``"emitted"``/``"scheduled"``, ``"skipped"`` (no bus available),
    or ``"failed"``. Never raises: a missing bus must not break notification.
    When called from a running event loop the broadcast is scheduled on it
    (mirroring ``AgentOrchestrator._emit``); otherwise it is run to completion
    so a sync caller (delegate/watchdog) still notifies.
    """
    try:
        if bus is None:
            try:
                from core.room_bus import bus as bus_obj  # type: ignore
            except Exception as exc:
                logger.debug("Room bus not importable: %s", exc)
                return "skipped"
            bus = bus_obj

        event: Any = payload
        event_type = _event_type_for(payload.get("status", ""))
        if event_type is not None:
            try:
                from core.models import BusEvent

                event = BusEvent(
                    event_type=event_type,
                    room_id=str(payload.get("room_id") or "the-council"),
                    payload=dict(payload),
                )
            except Exception:
                event = payload

        result = bus.broadcast(event)
        if inspect.isawaitable(result):
            try:
                loop = asyncio.get_running_loop()
            except RuntimeError:
                asyncio.run(result)  # type: ignore[arg-type]
                return "emitted"
            loop.create_task(result)  # type: ignore[arg-type]
            return "scheduled"
        return "emitted"
    except Exception as exc:
        logger.warning("Room-bus delegate notification failed: %s", exc)
        return "failed"


def notify_completion(
    task_id: str,
    goal: str,
    status: str,
    summary: str = "",
    duration_s: float = 0.0,
    artifacts: Optional[Iterable[Artifact]] = None,
    *,
    events_path: Optional[Union[str, Path]] = None,
    bus: Any = None,
) -> Dict[str, Any]:
    """Record a delegate terminal state and notify on every available path.

    ``summary`` is accepted for call-site ergonomics but its text is never
    persisted (credential-leak discipline — see module docstring); only
    ``len(summary)`` is recorded. Returns the exact payload written to the
    JSONL log and emitted to the bus.
    """
    payload: Dict[str, Any] = {
        "event": "delegate_completion",
        "task_id": str(task_id),
        "goal": str(goal or "")[:GOAL_MAX_CHARS],
        "status": str(status),
        "duration_s": float(duration_s or 0.0),
        "artifacts": _artifact_paths(artifacts),
        "summary_chars": len(summary) if summary else 0,
        "timestamp": time.time(),
    }
    payload["telegram"] = _send_telegram(payload)
    _append_event(_resolve_events_path(events_path), payload)
    payload["room_bus"] = _emit_to_bus(payload, bus=bus)
    return payload


def recent_events(
    limit: int = 50,
    events_path: Optional[Union[str, Path]] = None,
) -> List[Dict[str, Any]]:
    """Most recent delegate events, newest first. Malformed lines are skipped."""
    path = _resolve_events_path(events_path)
    if not path.exists():
        return []
    events: List[Dict[str, Any]] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        logger.warning("Could not read delegate events at %s: %s", path, exc)
        return []
    for line in reversed(lines):
        line = line.strip()
        if not line:
            continue
        try:
            record = json.loads(line)
        except (ValueError, TypeError):
            continue
        if isinstance(record, dict):
            events.append(record)
        if len(events) >= max(0, int(limit)):
            break
    return events


__all__ = [
    "EVENTS_PATH",
    "EVENTS_FILE",
    "DEFAULT_EVENTS_PATH",
    "MAX_EVENTS_BYTES",
    "GOAL_MAX_CHARS",
    "notify_completion",
    "recent_events",
]
