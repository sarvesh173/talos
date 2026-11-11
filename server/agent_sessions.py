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

from server.cli_engines import discover_engines

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
            "session_count": len(self.sessions),
            "sessions": [s.to_payload() for s in self.sessions],
            "sessions_error": self.sessions_error,
        }


def _opencode_sessions(limit: int = 20) -> List[AgentSessionRow]:
    """Read OpenCode's session table, read-only.

    Timestamps in this database are epoch milliseconds. Message counts are
    carried in the same query as a correlated sub-query: the previous version
    issued one extra ``SELECT COUNT(*)`` per session (21 queries for 20
    sessions, 21.5ms). Measured against the live database the correlated form
    costs 4.2ms, and a ``LEFT JOIN ... GROUP BY`` costs 7.4ms, so the
    correlated sub-query wins. A failure to count messages must not lose the
    session itself, so the count column is nullable.
    """
    path = os.path.expanduser(_OPENCODE_DB)
    if not os.path.exists(path):
        raise FileNotFoundError(f"no OpenCode database at {path}")

    # mode=ro guarantees this cannot mutate the user's agent state.
    uri = f"file:{path}?mode=ro"
    connection = sqlite3.connect(uri, uri=True, timeout=3.0)
    try:
        rows = connection.execute(
            "SELECT s.id, s.title, s.time_updated, "
            "  (SELECT COUNT(*) FROM message m WHERE m.session_id = s.id) AS cnt "
            "FROM session s ORDER BY s.time_updated DESC LIMIT ?",
            (limit,),
        ).fetchall()
        out: List[AgentSessionRow] = []
        for session_id, title, updated_ms, count in rows:
            out.append(
                AgentSessionRow(
                    id=str(session_id),
                    # Strip first, then fall back: a title of spaces is not a
                    # title, and `(title or "Untitled").strip()` would leave "".
                    title=(title or "").strip() or "Untitled session",
                    updated_at=float(updated_ms or 0) / 1000.0,
                    status="idle",
                    message_count=int(count) if count is not None else None,
                )
            )
        return out
    finally:
        connection.close()


#: Engines whose sessions this module knows how to read.
_SESSION_READERS = {
    "opencode": _opencode_sessions,
}


def discover_agent_sessions(limit_per_agent: int = 20) -> Dict[str, Any]:
    """Every discovered engine plus whatever real sessions are readable."""
    reports: List[AgentReport] = []
    for engine in discover_engines():
        report = AgentReport(
            id=engine.id,
            name=engine.name,
            installed=engine.is_available,
            binary=engine.resolved_path,
            binary_name=engine.binary,
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
    return {
        "agents": [r.to_payload() for r in reports],
        "installed_count": total_installed,
        "total_count": len(reports),
        "server_time": time.time(),
    }
