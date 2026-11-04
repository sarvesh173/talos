"""Question and permission lifecycle: options, custom answers, scopes, timeouts."""

import sys
import time
import unittest
from pathlib import Path
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.agent_orchestrator import AgentOrchestrator


class QuestionLifecycleTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.grants_file = Path(self.tmp_dir.name) / "grants.json"
        self.orch = AgentOrchestrator(persist=False)
        self.orch.set_grants_path(self.grants_file)

    def tearDown(self) -> None:
        self.tmp_dir.cleanup()

    async def test_pending_question_registered_and_drained(self):
        self.orch.register_question(
            "q_1",
            prompt="Which approach should we take?",
            options=[{"label": "Option A", "value": "a"}, {"label": "Option B", "value": "b"}],
            agent_name="Hermes",
            room_id="the-council",
        )
        pending = self.orch.pending_questions()
        self.assertEqual(len(pending), 1)
        self.assertEqual(pending[0]["id"], "q_1")

        self.assertTrue(await self.orch.answer_question("q_1", answer="Option A", option_value="a"))
        self.assertEqual(self.orch.pending_questions(), [])
        # Double-answer returns False
        self.assertFalse(await self.orch.answer_question("q_1", answer="Option B", option_value="b"))

    async def test_custom_answer_roundtrip(self):
        self.orch.register_question(
            "q_2",
            prompt="Is this file path safe to delete?",
            detail="/tmp/unused_cache",
            allow_custom=True,
            room_id="the-council",
        )
        self.assertTrue(await self.orch.answer_question("q_2", answer="Delete only if older than 24h"))
        self.assertEqual(self.orch.pending_questions(), [])

    async def test_lazy_timeout_expires(self):
        self.orch.register_question(
            "q_3",
            prompt="Quick confirm",
            timeout_seconds=0,  # Expires immediately
            room_id="the-council",
        )
        # Sleep a tiny fraction so now - created_at > 0
        time.sleep(0.01)
        # Calling pending_questions sweeps expired questions
        pending = self.orch.pending_questions()
        self.assertEqual(pending, [])
        # Question was expired and purged
        self.assertFalse(await self.orch.answer_question("q_3", answer="yes"))

    async def test_approval_scopes_session_vs_always(self):
        # Once scope does not grant blanket allow
        self.orch.register_approval("ap_once", "Run tests", tool_name="shell")
        await self.orch.resolve_approval("ap_once", approved=True, scope="once")
        self.assertFalse(self.orch.is_blanket_allowed("shell"))

        # Session scope grants blanket allow for current process only
        self.orch.register_approval("ap_sess", "Read file", tool_name="fs_read")
        await self.orch.resolve_approval("ap_sess", approved=True, scope="session")
        self.assertTrue(self.orch.is_blanket_allowed("fs_read"))

        # Fresh instance without reading from file does NOT have session allow
        fresh_orch = AgentOrchestrator(persist=False)
        fresh_orch.set_grants_path(self.grants_file)
        self.assertFalse(fresh_orch.is_blanket_allowed("fs_read"))

        # Always scope persists to file and survives across orchestrators
        self.orch.register_approval("ap_always", "Git push", tool_name="git_push")
        await self.orch.resolve_approval("ap_always", approved=True, scope="always")
        self.assertTrue(self.orch.is_blanket_allowed("git_push"))

        fresh_orch_2 = AgentOrchestrator(persist=False)
        fresh_orch_2.set_grants_path(self.grants_file)
        self.assertTrue(fresh_orch_2.is_blanket_allowed("git_push"))
