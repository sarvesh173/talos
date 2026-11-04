"""Tool ledger tests.

The ledger is the backend of the Kanban surface. Two properties matter more than
any feature: a credential must never reach disk through it, and a running call
must always be closeable by its id.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.tool_ledger import REDACTED, ToolLedger, redact, summarize_arguments


class RedactionTest(unittest.TestCase):
    def test_openai_style_key_is_stripped(self):
        self.assertNotIn("sk-proj-abcdef123456", redact("key sk-proj-abcdef123456 here"))
        self.assertIn(REDACTED, redact("key sk-proj-abcdef123456 here"))

    def test_openrouter_key_is_stripped(self):
        text = redact("Authorization: Bearer sk-or-v1-9f8e7d6c5b4a39281706")
        self.assertNotIn("9f8e7d6c5b4a39281706", text)

    def test_github_token_is_stripped(self):
        self.assertNotIn("ghp_", redact("ghp_" + "a" * 30))

    def test_api_key_assignment_keeps_the_label(self):
        out = redact("api_key=supersecretvalue123")
        self.assertIn("api_key", out)
        self.assertNotIn("supersecretvalue123", out)

    def test_nested_structures_are_redacted(self):
        payload = {
            "headers": {"Authorization": "Bearer sk-abcdef1234567890"},
            "items": ["plain", "sk-zzzzzzzzzzzzzzzz"],
        }
        out = redact(payload)
        self.assertNotIn("sk-abcdef1234567890", str(out))
        self.assertNotIn("sk-zzzzzzzzzzzzzzzz", str(out))
        self.assertEqual(out["items"][0], "plain")

    def test_ordinary_text_survives(self):
        self.assertEqual(redact("hungry"), "hungry")
        self.assertEqual(redact("the orb animation is 90 lines"), "the orb animation is 90 lines")

    def test_non_ascii_survives(self):
        self.assertEqual(redact("बिल्कुल सही"), "बिल्कुल सही")


class LedgerTest(unittest.TestCase):
    def setUp(self):
        self.ledger = ToolLedger(ring_size=5)

    def test_start_then_finish_closes_the_row(self):
        rec = self.ledger.start("r1", "a1", "Hermes", "shell", {"cmd": "ls"})
        self.assertEqual(rec.status, "running")
        self.ledger.finish(rec, status="ok", result_summary="3 files")
        rows = self.ledger.recent("r1")
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["status"], "ok")
        self.assertEqual(rows[0]["result_summary"], "3 files")
        self.assertIsNotNone(rows[0]["duration_ms"])

    def test_running_lane_tracks_in_flight_only(self):
        a = self.ledger.start("r1", "a1", "Hermes", "shell")
        b = self.ledger.start("r1", "a2", "Kotlin Auditor", "read_file", is_delegate=True)
        self.ledger.finish(a, status="ok")
        running = self.ledger.running("r1")
        self.assertEqual([r["id"] for r in running], [b.id])

    def test_ring_is_bounded(self):
        for i in range(12):
            self.ledger.start("r1", "a1", "Hermes", f"tool{i}")
        self.assertEqual(len(self.ledger.recent("r1", limit=100)), 5)

    def test_recent_is_newest_first(self):
        for name in ("first", "second", "third"):
            self.ledger.start("r1", "a1", "Hermes", name)
        self.assertEqual(
            [r["tool"] for r in self.ledger.recent("r1")], ["third", "second", "first"]
        )

    def test_failures_only_filter(self):
        ok = self.ledger.start("r1", "a1", "Hermes", "read_file")
        self.ledger.finish(ok, status="ok")
        bad = self.ledger.start("r1", "a1", "Hermes", "shell")
        self.ledger.finish(bad, status="error", error="permission denied")
        rows = self.ledger.recent("r1", failures_only=True)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["tool"], "shell")

    def test_delegates_only_filter(self):
        self.ledger.start("r1", "main", "Hermes", "shell")
        self.ledger.start("r1", "d1", "Auditor", "read_file", is_delegate=True)
        rows = self.ledger.recent("r1", delegates_only=True)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["agent_id"], "d1")

    def test_stats_counts_per_agent_and_tool(self):
        a = self.ledger.start("r1", "main", "Hermes", "shell")
        self.ledger.finish(a, status="ok")
        b = self.ledger.start("r1", "main", "Hermes", "shell")
        self.ledger.finish(b, status="error", error="boom")
        c = self.ledger.start("r1", "d1", "Auditor", "read_file", is_delegate=True)

        stats = self.ledger.stats("r1")
        self.assertEqual(stats["total_calls"], 3)
        self.assertEqual(stats["failures"], 1)
        self.assertEqual(stats["running"], 1)
        self.assertEqual(stats["tools"]["shell"], 2)
        by_id = {a["agent_id"]: a for a in stats["agents"]}
        self.assertEqual(by_id["main"]["calls"], 2)
        self.assertEqual(by_id["main"]["errors"], 1)
        self.assertEqual(by_id["d1"]["running"], 1)

    def test_credentials_never_enter_the_ledger(self):
        rec = self.ledger.start(
            "r1", "a1", "Hermes", "http", {"Authorization": "Bearer sk-or-v1-abcdef1234567890"}
        )
        stored = self.ledger.recent("r1")[0]
        self.assertNotIn("abcdef1234567890", str(stored))
        self.assertIn(REDACTED, str(stored))

    def test_persist_callback_receives_payload(self):
        captured = {}
        ledger = ToolLedger(persist=lambda p: captured.update(p))
        rec = ledger.start("r1", "a1", "Hermes", "shell")
        ledger.finish(rec, status="ok", result_summary="fine")
        self.assertEqual(captured["status"], "ok")
        self.assertEqual(captured["tool"], "shell")

    def test_persist_failure_does_not_raise(self):
        def boom(_payload):
            raise RuntimeError("disk full")

        ledger = ToolLedger(persist=boom)
        rec = ledger.start("r1", "a1", "Hermes", "shell")
        ledger.finish(rec, status="ok")  # must not raise

    def test_clear_room_and_all(self):
        self.ledger.start("r1", "a1", "Hermes", "x")
        self.ledger.start("r2", "a1", "Hermes", "y")
        self.ledger.clear("r1")
        self.assertEqual(self.ledger.recent("r1"), [])
        self.assertEqual(len(self.ledger.recent("r2")), 1)
        self.ledger.clear()
        self.assertEqual(self.ledger.recent("r2"), [])


class SummaryTest(unittest.TestCase):
    def test_none_is_empty(self):
        self.assertEqual(summarize_arguments(None), "")

    def test_short_string_passes_through(self):
        self.assertEqual(summarize_arguments("ls -la"), "ls -la")

    def test_long_payload_is_truncated_with_ellipsis(self):
        out = summarize_arguments({"path": "x" * 500}, limit=40)
        self.assertLessEqual(len(out), 40)
        self.assertTrue(out.endswith("…"))

    def test_dict_renders_as_json(self):
        self.assertEqual(summarize_arguments({"a": 1}), '{"a": 1}')

    def test_newlines_are_flattened(self):
        self.assertNotIn("\n", summarize_arguments({"cmd": "line1\nline2"}))


if __name__ == "__main__":
    unittest.main()
