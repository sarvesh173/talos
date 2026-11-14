"""Agent roster honesty tests.

The landing screen used to list six CLI tools with invented session titles and
turn counts. These tests pin the replacement contract: report what exists, say
why when it cannot be read, and never invent a session.
"""

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server import agent_sessions
from server.opencode_bridge import OpenCodeResult


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
    """The reader asks the OpenCode server, so the server is what gets stubbed.

    These tests used to write a temporary SQLite file and patch ``_OPENCODE_DB``.
    The reader stopped reading that file when it moved to the server API, so the
    patches did nothing and the tests quietly queried the live host.
    """

    @staticmethod
    def _stub(rows, ok=True, error=None):
        client = MagicMock()
        client.list_sessions.return_value = OpenCodeResult(
            ok=ok, data={"data": rows} if ok else None, error=error
        )
        return patch("server.opencode_bridge.OpenCodeClient", return_value=client)

    def test_reads_sessions_newest_first(self):
        rows = [
            {"id": "ses_old", "title": "Older work", "time": {"updated": 1_000_000_000_000}},
            {"id": "ses_new", "title": "Newer work", "time": {"updated": 1_700_000_000_000}},
        ]
        with self._stub(rows):
            out = agent_sessions._opencode_sessions()
        self.assertEqual([r.id for r in out], ["ses_new", "ses_old"])
        self.assertEqual(out[0].title, "Newer work")

    def test_milliseconds_become_seconds(self):
        rows = [{"id": "ses_a", "title": "A", "time": {"updated": 1_700_000_000_000}}]
        with self._stub(rows):
            out = agent_sessions._opencode_sessions()
        # 1.7e12 ms is 1.7e9 s, a 2023 timestamp — not a year 55000 one.
        self.assertLess(out[0].updated_at, 2_000_000_000)
        self.assertGreater(out[0].updated_at, 1_000_000_000)

    def test_list_endpoint_has_no_message_count(self):
        rows = [{"id": "ses_a", "title": "A", "time": {"updated": 1_700_000_000_000}}]
        with self._stub(rows):
            out = agent_sessions._opencode_sessions()
        # The list endpoint does not carry a count; None is the honest value.
        self.assertIsNone(out[0].message_count)

    def test_blank_title_gets_a_placeholder_not_an_empty_row(self):
        rows = [{"id": "ses_a", "title": "   ", "time": {"updated": 1_700_000_000_000}}]
        with self._stub(rows):
            out = agent_sessions._opencode_sessions()
        self.assertEqual(out[0].title, "Untitled session")

    def test_server_failure_raises_a_clear_error(self):
        with self._stub([], ok=False, error="opencode unreachable"):
            with self.assertRaises(RuntimeError):
                agent_sessions._opencode_sessions()

    def test_rows_without_an_id_are_skipped(self):
        rows = [
            {"id": "", "title": "Ghost"},
            {"id": "ses_a", "title": "A", "time": {"updated": 1_700_000_000_000}},
        ]
        with self._stub(rows):
            out = agent_sessions._opencode_sessions()
        self.assertEqual([r.id for r in out], ["ses_a"])


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
