"""A template reply must never look like a model reply.

When no model is reachable the orchestrator falls back to canned templates so
the room keeps moving. That text used to be saved as an ordinary agent message:
the app rendered it exactly like a real answer, with the same bubble and no
hint that no model spoke. That is the project's no-fabrication rule broken by
the code that was supposed to be the fallback.

The fix is a provenance flag on Message, persisted so it survives a reload.
"""

import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.models import Message, Role
from server import db


class _IsolatedDatabase:
    """Point db at a temporary file, including the cached connection.

    `db._get_connection()` caches a process-wide sqlite3 handle, so swapping
    `_DB_PATH` alone leaves the old handle live and the test silently writes to
    the real database. Reset `_CONN` too, and restore both on exit.
    """

    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "talos.db"
        self._orig_path = None
        self._orig_conn = None

    def __enter__(self):
        self._orig_path = db._DB_PATH
        self._orig_conn = db._CONN
        db._DB_PATH = self.path
        db._CONN = None
        db.init_db()
        return self.path

    def __exit__(self, *exc):
        if db._CONN is not None:
            try:
                db._CONN.close()
            except Exception:
                pass
        db._CONN = self._orig_conn
        db._DB_PATH = self._orig_path
        self.tmp.cleanup()
        return False


_HELD: dict = {}


def _use_existing_database(path: Path) -> None:
    """Point db at a database file that already exists, resetting the cache."""
    _HELD["path"] = db._DB_PATH
    _HELD["conn"] = db._CONN
    db._DB_PATH = path
    db._CONN = None


def _restore_database() -> None:
    if db._CONN is not None:
        try:
            db._CONN.close()
        except Exception:
            pass
    db._CONN = _HELD.get("conn")
    db._DB_PATH = _HELD.get("path", db._DB_PATH)


class MessageFlagTest(unittest.TestCase):
    def test_a_normal_message_is_not_simulated(self):
        msg = Message(room_id="r", sender_id="a", sender_name="A", content="hi")
        self.assertFalse(msg.simulated)
        self.assertIsNone(msg.simulated_reason)

    def test_a_template_reply_can_carry_its_reason(self):
        msg = Message(
            room_id="r",
            sender_id="a",
            sender_name="A",
            sender_role=Role.agent,
            content="canned",
            simulated=True,
            simulated_reason="model unreachable",
        )
        self.assertTrue(msg.simulated)
        self.assertEqual(msg.simulated_reason, "model unreachable")


class PersistenceTest(unittest.TestCase):
    """The flag has to survive a round-trip, or a reload launders the fake."""

    def setUp(self):
        self.iso = _IsolatedDatabase()
        self.iso.__enter__()

    def tearDown(self):
        self.iso.__exit__()

    def test_flag_survives_save_and_reload(self):
        msg = Message(
            room_id="the-council",
            sender_id="hermes",
            sender_name="Hermes",
            sender_role=Role.agent,
            content="template text",
            simulated=True,
            simulated_reason="live models disabled",
        )
        db.save_message(msg)

        loaded = db.get_room_messages("the-council")
        match = next(m for m in loaded if m.id == msg.id)
        self.assertTrue(match.simulated)
        self.assertEqual(match.simulated_reason, "live models disabled")

    def test_a_real_reply_stays_unflagged(self):
        msg = Message(
            room_id="the-council",
            sender_id="hermes",
            sender_name="Hermes",
            sender_role=Role.agent,
            content="real model text",
        )
        db.save_message(msg)
        loaded = next(m for m in db.get_room_messages("the-council") if m.id == msg.id)
        self.assertFalse(loaded.simulated)


class OldDatabaseMigrationTest(unittest.TestCase):
    """A database created before the flag existed must gain the columns."""

    def test_init_db_adds_the_columns_to_an_old_messages_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "old.db"
            # Build the pre-flag schema by hand.
            conn = sqlite3.connect(str(path))
            conn.execute(
                """CREATE TABLE messages (
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
                )"""
            )
            conn.commit()
            conn.close()

            _use_existing_database(path)
            try:
                db.init_db()
                conn = sqlite3.connect(str(path))
                cols = {row[1] for row in conn.execute("PRAGMA table_info(messages)")}
                conn.close()
                self.assertIn("simulated", cols)
                self.assertIn("simulated_reason", cols)
            finally:
                _restore_database()

    def test_a_row_written_before_the_flag_reads_as_not_simulated(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "old.db"
            conn = sqlite3.connect(str(path))
            conn.execute(
                """CREATE TABLE messages (
                    id TEXT PRIMARY KEY, room_id TEXT NOT NULL, sender_id TEXT NOT NULL,
                    sender_name TEXT NOT NULL, sender_role TEXT NOT NULL,
                    content TEXT NOT NULL, thought TEXT,
                    tool_calls_json TEXT DEFAULT '[]',
                    attachments_json TEXT DEFAULT '[]',
                    reply_to_id TEXT, created_at REAL)"""
            )
            conn.execute(
                "INSERT INTO messages VALUES ('m1','r','a','A','agent','old','',NULL,NULL,NULL,1.0)"
            )
            conn.commit()
            conn.close()

            _use_existing_database(path)
            try:
                db.init_db()
                loaded = db.get_room_messages("r")
                self.assertEqual(len(loaded), 1)
                self.assertFalse(loaded[0].simulated)
            finally:
                _restore_database()


class OrchestratorFlagTest(unittest.TestCase):
    """The fallback branch itself must mark what it produced."""

    def test_template_turn_is_saved_as_simulated(self):
        import asyncio

        from server.agent_orchestrator import AgentOrchestrator

        with _IsolatedDatabase():
            if True:
                async def scenario():
                    orch = AgentOrchestrator(
                        persist=True, use_live_models=False, typing_lead=0, token_interval=0
                    )
                    orch._schedule_turns(["hermes"], "the-council", "anything")
                    await asyncio.sleep(0.4)
                    return db.get_room_messages("the-council")

                msgs = asyncio.new_event_loop().run_until_complete(scenario())
                agent_msgs = [m for m in msgs if m.sender_role == Role.agent]
                self.assertTrue(agent_msgs, "no agent message was persisted")
                self.assertTrue(
                    all(m.simulated for m in agent_msgs),
                    "a template reply was stored as a real model reply",
                )
                self.assertEqual(
                    agent_msgs[0].simulated_reason, "live models disabled"
                )


if __name__ == "__main__":
    unittest.main()
