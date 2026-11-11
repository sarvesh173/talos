"""A cold start on an empty database must work.

`_get_connection()` created its indexes before `init_db()` created the tables,
so a fresh checkout (the DB file is git-ignored) crashed at boot with
"no such table: main.messages" — invisible on any machine that already had a
populated database. These tests pin the cold-start path so it cannot regress.
"""

import importlib
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


class ColdStartTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = Path(self.tmp.name) / "fresh.db"
        # Re-import the module so its module-level connection cache is empty.
        if "server.db" in sys.modules:
            del sys.modules["server.db"]
        self.db = importlib.import_module("server.db")
        self.db._DB_PATH = self.db_path
        self.db._CONN = None

    def tearDown(self):
        try:
            self.db._CONN = None
        except Exception:
            pass
        self.tmp.cleanup()

    def test_fresh_database_initialises_without_error(self):
        self.assertFalse(self.db_path.exists())
        self.db.init_db()
        self.assertTrue(self.db_path.exists())

    def test_tables_and_indexes_exist_after_init(self):
        self.db.init_db()
        conn = sqlite3.connect(str(self.db_path))
        conn.row_factory = sqlite3.Row
        tables = {r["name"] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        )}
        # api_tokens is owned by server.auth, not db.init_db() — assert only
        # what init_db() is actually responsible for.
        for expected in ("rooms", "messages", "agents"):
            self.assertIn(expected, tables)

        indexes = {r["name"] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='index'"
        )}
        self.assertIn("idx_messages_room_time", indexes)
        self.assertIn("idx_agents_active", indexes)
        conn.close()

    def test_init_is_idempotent(self):
        self.db.init_db()
        # A second call on the now-populated database must not raise.
        self.db.init_db()

    def test_get_connection_alone_does_not_require_existing_tables(self):
        """Opening a connection before init_db() is the cold-start order."""
        self.db._get_connection()
        self.db.init_db()


if __name__ == "__main__":
    unittest.main()
