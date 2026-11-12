"""Real agent session discovery for the app's roster.

The Android landing screen used to render six hardcoded CLI tools with invented
session titles and turn counts. That is worse than showing nothing: a fabricated
"62 turns, 3h ago" reads as a live system and quietly teaches the operator to
distrust every number in the app.

This module reports what is actually on the machine:

* which engines exist (delegated to :mod:`server.cli_engines`), and
* the real sessions each one has on disk, where that is readable.

OpenCode keeps a SQLite database at ``~/.local/share/opencode/opencode.db``;
reading it is a plain read-only query — no server, no auth, no side effects.
Engines whose session storage is not readable return an empty list plus a reason,
and the UI says "sessions unavailable" rather than inventing rows.
"""

from __future__ import annotations

import logging
import os
import sqlite3
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from server.cli_engines import connected_ids, discover_engines

logger = logging.getLogger(__name__)

#: Read-only. Never open these databases for writing from the app.
_OPENCODE_DB = "~/.local/share/opencode/opencode.db"

#: A session older than this is reported as stale rather than "active".
_ACTIVE_WINDOW_SECONDS = 15 * 60


@dataclass
class AgentSessionRow:
    id: str
    title: str
    updated_at: float
    status: str
    message_count: Optional[int] = None

    def to_payload(self) -> Dict[str, Any]:
        age = max(0.0, time.time() - self.updated_at)
        return {
            "id": self.id,
            "title": self.title,
            "updated_at": self.updated_at,
            "age_seconds": int(age),
            "status": "active" if age <= _ACTIVE_WINDOW_SECONDS else "idle",
            "message_count": self.message_count,
        }


@dataclass
class AgentReport:
    id: str
    name: str
    installed: bool
    binary: Optional[str]
    #: Binary name probed on PATH, e.g. "opencode". Sent so a client can show
    #: the command that was run instead of keeping its own id -> command map,
    #: which would drift from the host the moment an engine is renamed.
    binary_name: str = ""
    #: True when TALOS holds a control manifest for this engine, i.e. it can be
    #: driven — not merely present on disk. The app renders this, never a
    #: built-in list.
    connected: bool = False
    sessions: List[AgentSessionRow] = field(default_factory=list)
    #: Why sessions could not be listed, when they could not be.
    sessions_error: str = ""

    def to_payload(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "installed": self.installed,
            "binary": self.binary,
            "binary_name": self.binary_name,
            "connected": self.connected,
            "session_count": len(self.sessions),
            "sessions": [s.to_payload() for s in self.sessions],
            "sessions_error": self.sessions_error,
        }


def _opencode_sessions(limit: int = 20) -> List[AgentSessionRow]:
    """Read sessions from the OpenCode server, which is the only live source.

    This used to read ~/.local/share/opencode/opencode.db. That database still
    exists and still looks plausible, but the server stopped maintaining its
    `time_updated` column: every row in both copies reads 2026-10-15, so the app
    labelled a session updated minutes ago as "28d". Reading the server's own
    API costs one request and cannot go stale behind a schema change.
    """
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().list_sessions()
    if not result.ok:
        raise RuntimeError(result.error or "opencode did not answer")

    rows = (result.data or {}).get("data") or []
    out: List[AgentSessionRow] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        session_id = row.get("id")
        if not session_id:
            continue
        # The server moved timestamps into a `time` object; older builds sent a
        # bare number. Read whichever is present rather than assuming one.
        stamp = row.get("time")
        if isinstance(stamp, dict):
            updated_ms = stamp.get("updated") or stamp.get("created") or 0
        else:
            updated_ms = row.get("time_updated") or row.get("updated") or 0
        out.append(
            AgentSessionRow(
                id=str(session_id),
                title=(row.get("title") or "").strip() or "Untitled session",
                updated_at=float(updated_ms or 0) / 1000.0,
                status="idle",
                # The list endpoint does not carry a count; a per-session call
                # would cost one request each. None renders as "no count",
                # which is honest, where a stale number is not.
                message_count=None,
            )
        )
    return out[:limit]


#: Engines whose sessions this module knows how to read.
_SESSION_READERS = {
    "opencode": _opencode_sessions,
}


def discover_agent_sessions(limit_per_agent: int = 20) -> Dict[str, Any]:
    """Every discovered engine plus whatever real sessions are readable."""
    reports: List[AgentReport] = []
    connected = connected_ids()
    for engine in discover_engines():
        report = AgentReport(
            id=engine.id,
            name=engine.name,
            installed=engine.is_available,
            binary=engine.resolved_path,
            binary_name=engine.binary,
            connected=engine.is_available and engine.id in connected,
        )
        reader = _SESSION_READERS.get(engine.id)
        if reader is None:
            report.sessions_error = "session history not exposed by this engine"
        elif not engine.is_available:
            report.sessions_error = "engine not installed"
        else:
            try:
                report.sessions = reader(limit_per_agent)
            except FileNotFoundError as exc:
                report.sessions_error = str(exc)
            except sqlite3.Error as exc:
                logger.warning("Session read failed for %s: %s", engine.id, exc)
                report.sessions_error = "session database unreadable"
            except Exception as exc:  # pragma: no cover - defensive
                logger.warning("Unexpected session read failure %s: %s", engine.id, exc)
                report.sessions_error = "session read failed"
        reports.append(report)

    total_installed = sum(1 for r in reports if r.installed)
    total_connected = sum(1 for r in reports if r.connected)
    return {
        "agents": [r.to_payload() for r in reports],
        "installed_count": total_installed,
        "connected_count": total_connected,
        "total_count": len(reports),
        "server_time": time.time(),
    }
