"""Unit and Integration Tests for Multi-Agent Consensus Protocol."""

import unittest
from httpx import ASGITransport, AsyncClient
from core.models import AgentProfile, Role, VoteChoice
from core.consensus import deliberate_proposal, list_room_proposals
from server.main import app
from server.auth import create_new_token


class TestConsensusProtocol(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self.transport = ASGITransport(app=app)
        self.client = AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.token = create_new_token("Test Consensus Token")
        self.auth_headers = {"Authorization": f"Bearer {self.token}"}

    async def asyncTearDown(self):
        await self.client.aclose()

    async def test_deliberate_proposal_direct(self):
        agents = [
            AgentProfile(id="hermes", name="Hermes", role_description="Autonomous reasoning and mesh guard"),
            AgentProfile(id="claude", name="Claude", role_description="System architecture and verification"),
            AgentProfile(id="omnirush", name="OmniRush", role_description="High-speed execution engine"),
        ]
        proposal = await deliberate_proposal("the-council", "Should we deploy v1.16.0?", agents)
        self.assertEqual(proposal.room_id, "the-council")
        self.assertEqual(proposal.outcome, "approved")
        self.assertGreaterEqual(proposal.consensus_ratio, 0.6)
        self.assertEqual(len(proposal.votes), 3)
        for v in proposal.votes:
            self.assertEqual(v.vote, VoteChoice.approve)

    async def test_consensus_api_unauthorized(self):
        resp = await self.client.post(
            "/api/rooms/the-council/consensus/propose",
            json={"question": "Should we ship?"},
        )
        self.assertEqual(resp.status_code, 401)

    async def test_consensus_api_nonexistent_room(self):
        resp = await self.client.post(
            "/api/rooms/ghost-chamber/consensus/propose",
            json={"question": "Should we ship?"},
            headers=self.auth_headers,
        )
        self.assertEqual(resp.status_code, 404)

    async def test_consensus_api_propose_and_list(self):
        resp = await self.client.post(
            "/api/rooms/the-council/consensus/propose",
            json={"question": "Can we execute consensus protocol across the chamber?"},
            headers=self.auth_headers,
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        data = resp.json()
        self.assertIn("outcome", data)
        self.assertIn("votes", data)
        self.assertGreater(len(data["votes"]), 0)

        # Query room consensus history
        history_resp = await self.client.get(
            "/api/rooms/the-council/consensus",
            headers=self.auth_headers,
        )
        self.assertEqual(history_resp.status_code, 200)
        history = history_resp.json()
        self.assertIsInstance(history, list)
        self.assertGreaterEqual(len(history), 1)
