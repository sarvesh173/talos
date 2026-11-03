"""Approval lifecycle: pending list, first-answer-wins, blanket allow."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.agent_orchestrator import AgentOrchestrator


class ApprovalLifecycleTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.orch = AgentOrchestrator(persist=False)

    async def test_pending_approval_appears_in_list(self):
        self.orch.register_approval(
            "ap_1", "Run the test suite", command="pytest -q", tool_name="shell", room_id="the-council"
        )
        pending = self.orch.pending_approvals()
        self.assertEqual(len(pending), 1)
        self.assertEqual(pending[0]["id"], "ap_1")

    async def test_resolve_drains_the_request(self):
        self.orch.register_approval("ap_2", "Edit a file", tool_name="edit")
        self.assertTrue(await self.orch.resolve_approval("ap_2", approved=True))
        self.assertEqual(self.orch.pending_approvals(), [])

    async def test_second_answer_loses(self):
        self.orch.register_approval("ap_3", "Delete a branch", tool_name="shell")
        self.assertTrue(await self.orch.resolve_approval("ap_3", approved=False))
        # A stale notification tap must not approve something already denied.
        self.assertFalse(await self.orch.resolve_approval("ap_3", approved=True))

    async def test_remember_grants_blanket_allow_for_that_tool_only(self):
        self.orch.register_approval("ap_4", "Run tests", tool_name="shell")
        await self.orch.resolve_approval("ap_4", approved=True, remember=True)
        self.assertTrue(self.orch.is_blanket_allowed("shell"))
        self.assertFalse(self.orch.is_blanket_allowed("edit"))

    async def test_deny_does_not_grant_blanket_allow(self):
        self.orch.register_approval("ap_5", "Push to main", tool_name="shell")
        await self.orch.resolve_approval("ap_5", approved=False, remember=True)
        self.assertFalse(self.orch.is_blanket_allowed("shell"))

    async def test_unknown_id_is_not_resolved(self):
        self.assertFalse(await self.orch.resolve_approval("nope", approved=True))
