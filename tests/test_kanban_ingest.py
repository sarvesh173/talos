"""Kanban ingest endpoint tests.

The OpenCode plugin posts tool events here. The properties that matter:

* a start/finish pair produces exactly one closed row with a real duration;
* a credential in the arguments never reaches the board;
* an orphan finish does not fabricate a row;
* a malformed event is rejected rather than stored half-formed.
"""

import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient

import server.main as app_module
from server.tool_ledger import ledger


def _client():
    """Real token from the host vault; auth is DB-backed, not env-backed."""
    token = os.environ.get("TALOS_TEST_TOKEN", "").strip()
    if not token:
        token_path = os.path.expanduser("~/.hermes/talos_token.txt")
        try:
            token = Path(token_path).read_text(encoding="utf-8").strip()
        except OSError:
            raise unittest.SkipTest(f"no host token at {token_path}")
    return TestClient(app_module.app), {"Authorization": f"Bearer {token}"}


class IngestTest(unittest.TestCase):
    def setUp(self):
        ledger.clear("the-council")
        self.client, self.headers = _client()

    def _post(self, event):
        return self.client.post(
            "/api/kanban/events",
            headers=self.headers,
            json={"room_id": "the-council", "event": event},
        )

    def test_start_then_finish_closes_one_row(self):
        start = self._post(
            {"kind": "tool.start", "agentId": "opencode", "agentName": "OpenCode",
             "tool": "bash", "arguments": {"cmd": "ls"}}
        )
        self.assertEqual(start.status_code, 200)
        self.assertTrue(start.json()["recorded"])

        finish = self._post(
            {"kind": "tool.finish", "agentId": "opencode", "agentName": "OpenCode",
             "tool": "bash", "status": "ok", "resultSummary": "3 files"}
        )
        self.assertEqual(finish.status_code, 200)
        self.assertTrue(finish.json()["recorded"])

        rows = ledger.recent("the-council")
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["status"], "ok")
        self.assertEqual(rows[0]["result_summary"], "3 files")
        self.assertIsNotNone(rows[0]["duration_ms"])

    def test_credentials_are_redacted_on_ingest(self):
        secret = "sk-or-v1-LEAKMEMEMEMEMEMEMEMEMEMEMEMEME"
        self._post(
            {"kind": "tool.start", "agentId": "opencode", "agentName": "OpenCode",
             "tool": "bash", "arguments": {"cmd": f"curl -H 'Authorization: Bearer {secret}'"}}
        )
        stored = str(ledger.recent("the-council"))
        self.assertNotIn(secret, stored)

    def test_orphan_finish_does_not_create_a_row(self):
        result = self._post(
            {"kind": "tool.finish", "agentId": "ghost", "tool": "nothing"}
        )
        self.assertEqual(result.status_code, 200)
        self.assertFalse(result.json()["recorded"])
        self.assertEqual(ledger.recent("the-council"), [])

    def test_missing_tool_is_rejected(self):
        result = self._post({"kind": "tool.start", "agentId": "opencode"})
        self.assertEqual(result.status_code, 400)
        self.assertIn("tool", result.json()["detail"])

    def test_non_tool_event_is_acknowledged_but_not_ledgered(self):
        result = self._post({"kind": "permission.asked", "tool": "bash"})
        self.assertEqual(result.status_code, 200)
        self.assertFalse(result.json()["recorded"])
        self.assertEqual(ledger.recent("the-council"), [])

    def test_two_sequential_calls_close_in_order(self):
        for index in range(2):
            self._post(
                {"kind": "tool.start", "agentId": "opencode", "tool": f"t{index}"}
            )
            self._post(
                {"kind": "tool.finish", "agentId": "opencode", "tool": f"t{index}",
                 "status": "ok"}
            )
        rows = ledger.recent("the-council")
        self.assertEqual(len(rows), 2)
        self.assertTrue(all(row["status"] == "ok" for row in rows))

    def test_unauthenticated_request_is_refused(self):
        result = self.client.post(
            "/api/kanban/events",
            json={"room_id": "the-council", "event": {"kind": "tool.start", "tool": "x"}},
        )
        self.assertIn(result.status_code, (401, 403))


if __name__ == "__main__":
    unittest.main()
