"""Agent roster honesty tests.

The landing screen used to list six CLI tools with invented session titles and
turn counts. These tests pin the replacement contract: report what exists, say
why when it cannot be read, and never invent a session.
"""

import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server import agent_sessions


class RosterShapeTest(unittest.TestCase):
    def test_every_discovered_engine_is_reported(self):
        report = agent_sessions.discover_agent_sessions()
        from server.cli_engines import CLI_ENGINES

        self.assertEqual(len(report["agents"]), len(CLI_ENGINES))

    def test_uninstalled_engine_says_so_and_has_no_sessions(self):
        report = agent_sessions.discover_agent_sessions()
        for agent in report["agents"]:
            if not agent["installed"]:
                self.assertEqual(agent["sessions"], [])
                self.assertTrue(agent["sessions_error"])

    def test_engines_without_a_reader_explain_themselves(self):
        report = agent_sessions.discover_agent_sessions()
        for agent in report["agents"]:
            if not agent["installed"]:
                continue
            if agent["id"] not in agent_sessions._SESSION_READERS:
                self.assertEqual(agent["sessions"], [])
                self.assertIn("not exposed", agent["sessions_error"])

    def test_counts_match_the_lists(self):
        report = agent_sessions.discover_agent_sessions()
        self.assertEqual(
            report["installed_count"],
            sum(1 for a in report["agents"] if a["installed"]),
        )
        for agent in report["agents"]:
            self.assertEqual(agent["session_count"], len(agent["sessions"]))


class OpencodeSessionTest(unittest.TestCase):
    """Reads a real-shaped SQLite database, not the user's live one."""

    def _make_db(self, rows):
        directory = tempfile.mkdtemp()
        path = os.path.join(directory, "opencode.db")
        connection = sqlite3.connect(path)
        connection.execute(
            "CREATE TABLE session (id TEXT PRIMARY KEY, title TEXT, time_updated INTEGER)"
        )
        connection.execute(
            "CREATE TABLE message (id TEXT PRIMARY KEY, session_id TEXT)"
        )
        for session_id, title, updated in rows:
            connection.execute(
                "INSERT INTO session VALUES (?,?,?)", (session_id, title, updated)
            )
            for i in range(3):
                connection.execute(
                    "INSERT INTO message VALUES (?,?)", (f"{session_id}_m{i}", session_id)
                )
        connection.commit()
        connection.close()
        return path

    def test_reads_sessions_newest_first(self):
        path = self._make_db([
            ("ses_old", "Older work", 1_000_000_000_000),
            ("ses_new", "Newer work", 1_700_000_000_000),
        ])
        with patch.object(agent_sessions, "_OPENCODE_DB", path):
            rows = agent_sessions._opencode_sessions()
        self.assertEqual([r.id for r in rows], ["ses_new", "ses_old"])
        self.assertEqual(rows[0].title, "Newer work")

    def test_milliseconds_become_seconds(self):
        path = self._make_db([("ses_a", "A", 1_700_000_000_000)])
        with patch.object(agent_sessions, "_OPENCODE_DB", path):
            rows = agent_sessions._opencode_sessions()
        # 1.7e12 ms is 1.7e9 s, a 2023 timestamp — not a year 55000 one.
        self.assertLess(rows[0].updated_at, 2_000_000_000)
        self.assertGreater(rows[0].updated_at, 1_000_000_000)

    def test_message_count_is_read(self):
        path = self._make_db([("ses_a", "A", 1_700_000_000_000)])
        with patch.object(agent_sessions, "_OPENCODE_DB", path):
            rows = agent_sessions._opencode_sessions()
        self.assertEqual(rows[0].message_count, 3)

    def test_blank_title_gets_a_placeholder_not_an_empty_row(self):
        path = self._make_db([("ses_a", "   ", 1_700_000_000_000)])
        with patch.object(agent_sessions, "_OPENCODE_DB", path):
            rows = agent_sessions._opencode_sessions()
        self.assertEqual(rows[0].title, "Untitled session")

    def test_missing_database_raises_a_clear_error(self):
        with patch.object(agent_sessions, "_OPENCODE_DB", "/nonexistent/opencode.db"):
            with self.assertRaises(FileNotFoundError):
                agent_sessions._opencode_sessions()

    def test_read_is_read_only(self):
        """The reader must never be able to mutate the user's agent state."""
        path = self._make_db([("ses_a", "A", 1_700_000_000_000)])
        before = os.path.getmtime(path)
        with patch.object(agent_sessions, "_OPENCODE_DB", path):
            agent_sessions._opencode_sessions()
        self.assertEqual(os.path.getmtime(path), before)


class FreshnessTest(unittest.TestCase):
    def test_recent_session_is_active(self):
        import time

        row = agent_sessions.AgentSessionRow(
            id="x", title="t", updated_at=time.time() - 60, status="idle"
        )
        self.assertEqual(row.to_payload()["status"], "active")

    def test_old_session_is_idle(self):
        import time

        row = agent_sessions.AgentSessionRow(
            id="x", title="t", updated_at=time.time() - 86_400, status="active"
        )
        self.assertEqual(row.to_payload()["status"], "idle")

    def test_age_is_never_negative(self):
        import time

        row = agent_sessions.AgentSessionRow(
            id="x", title="t", updated_at=time.time() + 5_000, status="idle"
        )
        self.assertEqual(row.to_payload()["age_seconds"], 0)


if __name__ == "__main__":
    unittest.main()
