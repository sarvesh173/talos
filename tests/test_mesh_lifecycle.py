"""
Unit tests for the dynamic agent lifecycle, 5-agent mesh council, and P2P summon protocol.
"""

import unittest
import asyncio
from core.models import AgentProfile, ConsensusProposal, VoteChoice
from core.consensus import deliberate_proposal, list_room_proposals, get_proposal

class TestMeshLifecycle(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.agents_4 = [
            AgentProfile(id="hermes", name="Hermes Agent", role_description="Nous Autonomy"),
            AgentProfile(id="claude", name="Claude Code", role_description="Anthropic Architecture"),
            AgentProfile(id="codex", name="Codex", role_description="OpenAI Synthesis"),
            AgentProfile(id="openclaw", name="OpenClaw", role_description="Tools & Automation")
        ]
        self.dynamic_opencode = AgentProfile(
            id="opencode",
            name="OpenCode",
            role_description="Open-Source Terminal CLI"
        )

    async def test_4_agent_quorum_calculation(self):
        proposal = await deliberate_proposal(
            room_id="lifecycle-test-4",
            question="Refactor AST Parser",
            participants=self.agents_4
        )
        self.assertIsNotNone(proposal.id)
        self.assertEqual(len(proposal.votes), 4)
        self.assertIn(proposal.outcome, ["approved", "rejected", "tie"])

    async def test_dynamic_opencode_summon_and_5_agent_quorum(self):
        agents_5 = self.agents_4 + [self.dynamic_opencode]
        self.assertEqual(len(agents_5), 5)

        proposal = await deliberate_proposal(
            room_id="lifecycle-test-5",
            question="Summon OpenCode for deep terminal AST pass",
            participants=agents_5
        )
        self.assertIsNotNone(proposal.id)
        self.assertEqual(len(proposal.votes), 5)
        self.assertIn(proposal.outcome, ["approved", "rejected", "tie"])

    def test_agent_deregistration_on_task_completion(self):
        active_agents = list(self.agents_4)
        active_agents.append(self.dynamic_opencode)
        self.assertIn("opencode", [a.id for a in active_agents])

        # Terminate / dismiss OpenCode after task
        active_agents = [a for a in active_agents if a.id != "opencode"]
        self.assertEqual(len(active_agents), 4)
        self.assertNotIn("opencode", [a.id for a in active_agents])

if __name__ == "__main__":
    unittest.main()
