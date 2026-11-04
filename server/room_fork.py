"""Conversation forking: copy a room up to any message into a new thread.

LibreChat-style branch. Forking never mutates the original room; message
content is copied verbatim (no re-summarisation), original timestamps are
preserved so the fork does not look freshly typed, and agent attribution
(sender_id / sender_name / sender_role) is kept intact.

Ownership: this module owns the ``parent_room_id`` / ``forked_at_message_id``
columns on ``rooms`` (added lazily so ``server/db.py`` need not change) and
all fork logic. ``server/main.py`` wires routes on top of :func:`fork_room`.

Privacy: no error string ever contains message content — only ids and codes.
"""

from __future__ import annotations

import sqlite3
import time
import uuid
from dataclasses import dataclass
from typing import List, Optional


@dataclass
class ForkResult:
    """Typed outcome of :func:`fork_room`. Never raises out of the fork."""

    ok: bool
    room_id: Optional[str] = None
    error_code: Optional[str] = None
    error: Optional[str] = None
    copied_message_count: int = 0
    created: bool = False

    def __str__(self) -> str:  # pragma: no cover - convenience only
        if self.ok:
            return f"ForkResult(ok room={self.room_id} copied={self.copied_message_count})"
        return f"ForkResult(error code={self.error_code} {self.error!r})"


@dataclass
class RoomRef:
    """One node in a fork lineage, root-first when returned."""

    id: str
    name: str
    parent_room_id: Optional[str] = None
    forked_at_message_id: Optional[str] = None
    created_at: Optional[float] = None
    updated_at: Optional[float] = None


_FORK_COLUMNS = (
    ("parent_room_id", "TEXT"),
    ("forked_at_message_id", "TEXT"),
)


def _table_columns(conn: sqlite3.Connection, table: str) -> List[str]:
    try:
        cur = conn.cursor()
        cur.execute(f"PRAGMA table_info({table})")
        return [row[1] if len(row) > 1 else row["name"] for row in cur.fetchall()]
    except sqlite3.Error:
        return []


def _ensure_fork_columns(conn: sqlite3.Connection) -> None:
    """Lazily add fork columns so existing DBs fork without a migration."""
    existing = _table_columns(conn, "rooms")
    if not existing:
        return
    cur = conn.cursor()
    for col, kind in _FORK_COLUMNS:
        if col not in existing:
            cur.execute(f"ALTER TABLE rooms ADD COLUMN {col} {kind}")


def _new_room_id() -> str:
    return uuid.uuid4().hex[:12]


def _new_message_id() -> str:
    return str(uuid.uuid4())


def fork_room(
    conn: sqlite3.Connection,
    room_id: str,
    message_id: str,
    title: Optional[str] = None,
    new_room_id: Optional[str] = None,
) -> ForkResult:
    """Fork ``room_id`` at ``message_id`` (inclusive) into a new room.

    Returns a typed :class:`ForkResult`; never raises out. A message id that
    does not exist, or that belongs to a different room, is refused without
    creating anything. When ``new_room_id`` is supplied and already exists,
    the existing fork is returned instead of making a second copy.
    """
    try:
        _ensure_fork_columns(conn)
        cur = conn.cursor()

        # Original room must exist.
        cur.execute("SELECT * FROM rooms WHERE id = ?", (room_id,))
        original = cur.fetchone()
        if original is None:
            return ForkResult(
                ok=False,
                error_code="room_not_found",
                error=f"room_id={room_id!r} not found",
            )
        orig_cols = set(_table_columns(conn, "rooms"))

        # Idempotency: explicit id already exists -> return it.
        if new_room_id is not None:
            cur.execute("SELECT id FROM rooms WHERE id = ?", (new_room_id,))
            if cur.fetchone() is not None:
                cur.execute(
                    "SELECT COUNT(*) FROM messages WHERE room_id = ?",
                    (new_room_id,),
                )
                count = int(cur.fetchone()[0])
                return ForkResult(
                    ok=True,
                    room_id=new_room_id,
                    copied_message_count=count,
                    created=False,
                )

        # Resolve the fork point: must exist and belong to this room.
        cur.execute("SELECT * FROM messages WHERE id = ?", (message_id,))
        target = cur.fetchone()
        if target is None:
            return ForkResult(
                ok=False,
                error_code="message_not_found",
                error=f"message_id={message_id!r} not found",
            )
        tcols = target.keys() if hasattr(target, "keys") else None
        t_room = target["room_id"]
        if t_room != room_id:
            return ForkResult(
                ok=False,
                error_code="message_room_mismatch",
                error=(
                    f"message_id={message_id!r} does not belong to "
                    f"room_id={room_id!r}"
                ),
            )

        # All messages in the original room, oldest-first, rowid as tiebreak.
        cur.execute(
            "SELECT rowid AS _rid, * FROM messages WHERE room_id = ? "
            "ORDER BY created_at ASC, _rid ASC",
            (room_id,),
        )
        messages = cur.fetchall()

        # Find index of target; copy up to and including it.
        fork_index: Optional[int] = None
        for idx, msg in enumerate(messages):
            if msg["id"] == message_id:
                fork_index = idx
                break
        if fork_index is None:  # defensive; target already validated
            return ForkResult(
                ok=False,
                error_code="message_not_found",
                error=f"message_id={message_id!r} not found",
            )
        prefix = messages[: fork_index + 1]

        new_id = new_room_id or _new_room_id()
        new_name = title if title is not None else f"{original['name']} (fork)"
        now = time.time()

        # --- write the new room ---
        has_agent_ids = "agent_ids_json" in orig_cols
        if has_agent_ids:
            cur.execute(
                """
                INSERT INTO rooms (
                    id, name, description, mode, participants_json,
                    agent_ids_json, created_at, updated_at,
                    parent_room_id, forked_at_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    new_id,
                    new_name,
                    original["description"] or "",
                    original["mode"],
                    original["participants_json"] or "[]",
                    original["agent_ids_json"] if "agent_ids_json" in orig_cols else "[]",
                    now,
                    now,
                    room_id,
                    message_id,
                ),
            )
        else:
            cur.execute(
                """
                INSERT INTO rooms (
                    id, name, description, mode, participants_json,
                    created_at, updated_at,
                    parent_room_id, forked_at_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    new_id,
                    new_name,
                    original["description"] or "",
                    original["mode"],
                    original["participants_json"] or "[]",
                    now,
                    now,
                    room_id,
                    message_id,
                ),
            )

        # --- copy messages verbatim with fresh row ids ---
        id_map = {msg["id"]: _new_message_id() for msg in prefix}
        for msg in prefix:
            new_msg_id = id_map[msg["id"]]
            reply_to = msg["reply_to_id"]
            if reply_to in id_map:
                reply_to = id_map[reply_to]
            cur.execute(
                """
                INSERT INTO messages (
                    id, room_id, sender_id, sender_name, sender_role,
                    content, thought, tool_calls_json, attachments_json,
                    reply_to_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    new_msg_id,
                    new_id,
                    msg["sender_id"],
                    msg["sender_name"],
                    msg["sender_role"],
                    msg["content"],
                    msg["thought"],
                    msg["tool_calls_json"] or "[]",
                    msg["attachments_json"] or "[]",
                    reply_to,
                    msg["created_at"],
                ),
            )

        conn.commit()
        return ForkResult(
            ok=True,
            room_id=new_id,
            copied_message_count=len(prefix),
            created=True,
        )
    except sqlite3.Error as exc:
        try:
            conn.rollback()
        except sqlite3.Error:
            pass
        # Never include message content; only the sqlite code/message
        # (which never contains row content for our statements).
        return ForkResult(
            ok=False,
            error_code="db_error",
            error=f"fork failed: {exc.__class__.__name__}",
        )
    except Exception as exc:  # never raises out
        try:
            conn.rollback()
        except Exception:
            pass
        return ForkResult(
            ok=False,
            error_code="internal_error",
            error=f"fork failed: {exc.__class__.__name__}",
        )


def fork_lineage(conn: sqlite3.Connection, room_id: str) -> List[RoomRef]:
    """Walk ``parent_room_id`` from ``room_id`` up to the root.

    Returns root-first so the app can render ``Main › fork 2 › fork 3``.
    Cycles and missing parents stop the walk silently.
    """
    try:
        cols = _table_columns(conn, "rooms")
        if not cols:
            return []
        has_parent = "parent_room_id" in cols
        has_forked = "forked_at_message_id" in cols

        chain: List[RoomRef] = []
        seen = set()
        current: Optional[str] = room_id
        while current is not None and current not in seen:
            seen.add(current)
            cur = conn.cursor()
            cur.execute("SELECT * FROM rooms WHERE id = ?", (current,))
            row = cur.fetchone()
            if row is None:
                break
            parent = row["parent_room_id"] if has_parent else None
            forked = row["forked_at_message_id"] if has_forked else None
            chain.append(
                RoomRef(
                    id=row["id"],
                    name=row["name"],
                    parent_room_id=parent,
                    forked_at_message_id=forked,
                    created_at=row["created_at"] if "created_at" in row.keys() else None,
                    updated_at=row["updated_at"] if "updated_at" in row.keys() else None,
                )
            )
            current = parent if parent else None

        chain.reverse()
        return chain
    except sqlite3.Error:
        return []
