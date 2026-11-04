"""Room forking tests.

The property that matters: a fork is a faithful, prefix-only copy — the
original is untouched, nothing after the fork point leaks in, and an error
never carries message content.
"""

import json
import sqlite3
import sys
import time
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.room_fork import fork_lineage, fork_room


def _temp_conn() -> sqlite3.Connection:
    """Temp in-memory DB built with the real db.py schema."""
    conn = sqlite3.connect(":memory:", check_same_thread=False)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE rooms (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT DEFAULT '',
            mode TEXT NOT NULL,
            participants_json TEXT DEFAULT '[]',
            created_at REAL,
            updated_at REAL
        )
        """
    )
    cur.execute(
        """
        CREATE TABLE messages (
            id TEXT PRIMARY KEY,
            room_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            sender_name TEXT NOT NULL,
            sender_role TEXT NOT NULL,
            content TEXT NOT NULL,
            thought TEXT,
            tool_calls_json TEXT DEFAULT '[]',
            attachments_json TEXT DEFAULT '[]',
            reply_to_id TEXT,
            created_at REAL
        )
        """
    )
    cur.execute(
        """
        CREATE TABLE agents (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            avatar TEXT,
            color TEXT DEFAULT '#6366f1',
            model TEXT,
            role_description TEXT,
            system_prompt TEXT,
            is_active INTEGER DEFAULT 1
        )
        """
    )
    cur.execute("ALTER TABLE rooms ADD COLUMN agent_ids_json TEXT DEFAULT '[]'")
    conn.commit()
    return conn


def _insert_room(conn, room_id, name):
    cur = conn.cursor()
    now = time.time()
    cur.execute(
        """
        INSERT INTO rooms (id, name, description, mode, participants_json,
                           agent_ids_json, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (room_id, name, "desc", "group", "[]", "[]", now, now),
    )
    conn.commit()
    return now


def _insert_messages(conn, room_id, base_time=1_700_000_000.0, n=10, prefix="m"):
    """Insert n messages; return list of ids in order."""
    cur = conn.cursor()
    ids = []
    for i in range(1, n + 1):
        mid = f"{prefix}-{i}"
        ids.append(mid)
        # Alternate sender to prove agent attribution is preserved.
        sender_id = "human-1" if i % 2 == 1 else "agent-1"
        sender_name = "User" if i % 2 == 1 else "Agent"
        role = "human" if i % 2 == 1 else "agent"
        cur.execute(
            """
            INSERT INTO messages (id, room_id, sender_id, sender_name,
                                  sender_role, content, thought,
                                  tool_calls_json, attachments_json,
                                  reply_to_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                mid,
                room_id,
                sender_id,
                sender_name,
                role,
                f"SECRET_CONTENT_{i}_do_not_leak",
                None,
                "[]",
                "[]",
                None,
                base_time + i,
            ),
        )
    conn.commit()
    return ids


def _messages_in(conn, room_id):
    cur = conn.cursor()
    cur.execute(
        "SELECT * FROM messages WHERE room_id = ? ORDER BY created_at ASC, id ASC",
        (room_id,),
    )
    return cur.fetchall()


class ForkRoomTest(unittest.TestCase):
    def setUp(self):
        self.conn = _temp_conn()
        self.room_id = "room-orig"
        _insert_room(self.conn, self.room_id, "Main")
        self.msg_ids = _insert_messages(self.conn, self.room_id)

    def tearDown(self):
        self.conn.close()

    def test_fork_at_four_yields_four(self):
        result = fork_room(self.conn, self.room_id, self.msg_ids[3])
        self.assertTrue(result.ok)
        self.assertIsNotNone(result.room_id)
        self.assertNotEqual(result.room_id, self.room_id)
        self.assertEqual(result.copied_message_count, 4)
        msgs = _messages_in(self.conn, result.room_id)
        self.assertEqual(len(msgs), 4)
        # No message after the fork point: first four contents, in order.
        for i, m in enumerate(msgs, start=1):
            self.assertEqual(m["content"], f"SECRET_CONTENT_{i}_do_not_leak")
            self.assertEqual(m["created_at"], 1_700_000_000.0 + i)
            # Agent attribution preserved.
            if i % 2 == 1:
                self.assertEqual(m["sender_id"], "human-1")
                self.assertEqual(m["sender_role"], "human")
            else:
                self.assertEqual(m["sender_id"], "agent-1")
                self.assertEqual(m["sender_role"], "agent")
        # New row ids, not the originals.
        fork_ids = {m["id"] for m in msgs}
        self.assertTrue(fork_ids.isdisjoint(set(self.msg_ids)))

    def test_fork_title_defaults_and_parent_recorded(self):
        result = fork_room(self.conn, self.room_id, self.msg_ids[3])
        cur = self.conn.cursor()
        cur.execute("SELECT * FROM rooms WHERE id = ?", (result.room_id,))
        row = cur.fetchone()
        self.assertEqual(row["name"], "Main (fork)")
        self.assertEqual(row["parent_room_id"], self.room_id)
        self.assertEqual(row["forked_at_message_id"], self.msg_ids[3])

    def test_fork_custom_title(self):
        result = fork_room(self.conn, self.room_id, self.msg_ids[1], title="Branch A")
        cur = self.conn.cursor()
        cur.execute("SELECT name FROM rooms WHERE id = ?", (result.room_id,))
        self.assertEqual(cur.fetchone()[0], "Branch A")

    def test_original_unchanged(self):
        before = [tuple(r) for r in _messages_in(self.conn, self.room_id)]
        fork_room(self.conn, self.room_id, self.msg_ids[3])
        after = [tuple(r) for r in _messages_in(self.conn, self.room_id)]
        self.assertEqual(len(after), 10)
        self.assertEqual(before, after)
        for i, m in enumerate(_messages_in(self.conn, self.room_id), start=1):
            self.assertEqual(m["content"], f"SECRET_CONTENT_{i}_do_not_leak")

    def test_fork_at_first_yields_one(self):
        result = fork_room(self.conn, self.room_id, self.msg_ids[0])
        self.assertTrue(result.ok)
        self.assertEqual(len(_messages_in(self.conn, result.room_id)), 1)

    def test_fork_at_last_yields_all(self):
        result = fork_room(self.conn, self.room_id, self.msg_ids[-1])
        self.assertTrue(result.ok)
        self.assertEqual(len(_messages_in(self.conn, result.room_id)), 10)

    def test_unknown_message_errors_and_creates_nothing(self):
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM rooms")
        before = cur.fetchone()[0]
        result = fork_room(self.conn, self.room_id, "nope-does-not-exist")
        self.assertFalse(result.ok)
        self.assertEqual(result.error_code, "message_not_found")
        cur.execute("SELECT COUNT(*) FROM rooms")
        self.assertEqual(cur.fetchone()[0], before)

    def test_message_from_another_room_rejected(self):
        _insert_room(self.conn, "room-other", "Other")
        other_ids = _insert_messages(self.conn, "room-other", prefix="x")
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM rooms")
        before = cur.fetchone()[0]
        result = fork_room(self.conn, self.room_id, other_ids[0])
        self.assertFalse(result.ok)
        self.assertEqual(result.error_code, "message_room_mismatch")
        cur.execute("SELECT COUNT(*) FROM rooms")
        self.assertEqual(cur.fetchone()[0], before)

    def test_unknown_room_errors(self):
        result = fork_room(self.conn, "ghost-room", self.msg_ids[0])
        self.assertFalse(result.ok)
        self.assertEqual(result.error_code, "room_not_found")

    def test_idempotent_on_new_room_id(self):
        first = fork_room(self.conn, self.room_id, self.msg_ids[3], new_room_id="fork-1")
        second = fork_room(self.conn, self.room_id, self.msg_ids[3], new_room_id="fork-1")
        self.assertTrue(first.ok)
        self.assertTrue(second.ok)
        self.assertEqual(first.room_id, "fork-1")
        self.assertEqual(second.room_id, "fork-1")
        self.assertFalse(second.created)
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM rooms WHERE id = ?", ("fork-1",))
        self.assertEqual(cur.fetchone()[0], 1)
        self.assertEqual(len(_messages_in(self.conn, "fork-1")), 4)

    def test_lineage_three_levels(self):
        r1 = fork_room(self.conn, self.room_id, self.msg_ids[3])
        self.assertTrue(r1.ok)
        # Fork messages carry fresh ids, so resolve the next fork points
        # from each fork's own message rows (2nd message, then 1st).
        r1_ids = [m["id"] for m in _messages_in(self.conn, r1.room_id)]
        r2 = fork_room(self.conn, r1.room_id, r1_ids[1])
        self.assertTrue(r2.ok)
        r2_ids = [m["id"] for m in _messages_in(self.conn, r2.room_id)]
        r3 = fork_room(self.conn, r2.room_id, r2_ids[0])
        self.assertTrue(r3.ok)
        lineage = fork_lineage(self.conn, r3.room_id)
        self.assertEqual(len(lineage), 4)  # orig + 3 forks
        self.assertEqual(lineage[0].id, self.room_id)
        self.assertEqual(lineage[-1].id, r3.room_id)
        self.assertEqual(
            [r.id for r in lineage],
            [self.room_id, r1.room_id, r2.room_id, r3.room_id],
        )
        # Root has no parent.
        self.assertIsNone(lineage[0].parent_room_id)
        self.assertEqual(lineage[1].parent_room_id, self.room_id)

    def test_error_string_never_contains_message_text(self):
        result = fork_room(self.conn, self.room_id, "unknown-id")
        self.assertFalse(result.ok)
        for secret in [f"SECRET_CONTENT_{i}_do_not_leak" for i in range(1, 11)]:
            self.assertNotIn(secret, result.error or "")
            self.assertNotIn(secret, result.error_code or "")
        # Also on the cross-room error path.
        _insert_room(self.conn, "room-other2", "Other2")
        other = _insert_messages(self.conn, "room-other2", prefix="z")
        cross = fork_room(self.conn, self.room_id, other[0])
        self.assertFalse(cross.ok)
        for secret in [f"SECRET_CONTENT_{i}_do_not_leak" for i in range(1, 11)]:
            self.assertNotIn(secret, cross.error or "")


if __name__ == "__main__":
    unittest.main()
