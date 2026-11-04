"""Delegates and Summoning lifecycle: register, list, pause, cancel, and TTL sweep."""

import sys
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.agent_orchestrator import AgentOrchestrator


class DelegateLifecycleTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.orch = AgentOrchestrator(persist=False)

    def test_register_and_list_delegate(self):
        rec = self.orch.register_delegate(
            delegate_id="del_test_1",
            name="Code Auditor",
            task="Audit Kotlin codebase for memory leaks",
            parent_id="main",
            model="stealth/crouton-alpha",
            reasoning_effort="high",
            ttl_seconds=3600,
        )
        self.assertEqual(rec["id"], "del_test_1")
        self.assertEqual(rec["status"], "running")

        delegates = self.orch.list_delegates()
        self.assertEqual(len(delegates), 1)
        self.assertEqual(delegates[0]["id"], "del_test_1")
        self.assertEqual(delegates[0]["model"], "stealth/crouton-alpha")
        self.assertIn("elapsed_seconds", delegates[0])
        self.assertIn("remaining_seconds", delegates[0])

    def test_pause_and_resume_delegate(self):
        self.orch.register_delegate(
            delegate_id="del_test_2",
            name="Doc Writer",
            task="Draft release notes",
        )
        # Pause
        self.assertTrue(self.orch.pause_delegate("del_test_2"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "paused")

        # Resume
        self.assertTrue(self.orch.pause_delegate("del_test_2"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "running")

    def test_cancel_delegate(self):
        self.orch.register_delegate(
            delegate_id="del_test_3",
            name="Scraper",
            task="Scrape documentation",
        )
        self.assertTrue(self.orch.cancel_delegate("del_test_3"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "cancelled")

    def test_ttl_auto_expiration(self):
        # Register with negative or 0 TTL
        rec = self.orch.register_delegate(
            delegate_id="del_test_4",
            name="Quick Prober",
            task="Probe network interface",
            ttl_seconds=0,
        )
        time.sleep(0.01)
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "expired")
        self.assertEqual(delegates[0]["remaining_seconds"], 0)


if __name__ == "__main__":
    unittest.main()
