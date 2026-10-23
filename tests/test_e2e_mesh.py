"""End-to-end integration tests for the TALOS mesh/auth/orchestration surface.

These tests drive the real FastAPI application in-process (httpx ASGI transport)
against the real SQLite persistence layer and the real room bus, so the whole
request path (auth dependency -> handler -> bus -> orchestrator) is exercised.

Covered behaviours:

1. ``GET  /api/mesh/status``   -> active Tailscale node + peer information.
2. ``POST /api/auth/pair``     -> issues a valid token and welcomes the client
   on the room bus.
3. ``GET  /api/auth/verify``   -> passes with a Bearer token, 401 without one.
4. ``POST /api/messages``      -> a ``the-council`` message triggers the
   orchestrator exactly once per agent (no turn collision / no agent loop).
"""

import asyncio
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx

from core.models import Role
from core.room_bus import bus
from server.agent_orchestrator import orchestrator
from server.auth import (
    create_new_token,
    get_master_token,
    init_auth_db,
    verify_token,
)
from server.db import init_db
from server.main import app


class RecordingSocket:
    """Minimal WebSocket stand-in that records broadcast events."""

    def __init__(self, room_id: str):
        self.room_id = room_id
        self.events = []
        self.binary_events = []

    async def send_text(self, payload: str) -> None:
        self.events.append(payload)

    async def send_bytes(self, payload: bytes) -> None:
        self.binary_events.append(payload)

    def decoded(self):
        out = []
        for payload in self.events:
            try:
                out.append(json.loads(payload))
            except (TypeError, ValueError):
                continue
        return out

    def of_type(self, event_type: str):
        return [e for e in self.decoded() if e.get("event_type") == event_type]


class MeshE2ETestCase(unittest.IsolatedAsyncioTestCase):
    """Shared fixtures: fast orchestrator timings + a live ASGI client."""

    async def asyncSetUp(self) -> None:
        init_db()
        init_auth_db()

        # Keep the chamber responsive so the suite stays fast, but keep the
        # real orchestrator instance (the app dispatches through the global
        # room bus that it listens on).
        self._orig_stagger = orchestrator.turn_stagger
        self._orig_lead = orchestrator.typing_lead
        self._orig_interval = orchestrator.token_interval
        orchestrator.turn_stagger = 0.01
        orchestrator.typing_lead = 0.01
        orchestrator.token_interval = 0.0

        await orchestrator.start()

        self.transport = httpx.ASGITransport(app=app)
        self.client = httpx.AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.addCleanup(self._cleanup)

    async def _cleanup(self) -> None:
        await self.client.aclose()
        orchestrator.turn_stagger = self._orig_stagger
        orchestrator.typing_lead = self._orig_lead
        orchestrator.token_interval = self._orig_interval

    def auth_header(self, token: str) -> dict:
        return {"Authorization": f"Bearer {token}"}


class TestMeshStatus(MeshE2ETestCase):

    async def test_mesh_status_reports_active_node_and_peers(self):
        resp = await self.client.get("/api/mesh/status")
        self.assertEqual(resp.status_code, 200, resp.text)

        data = resp.json()
        self.assertIs(data["active"], True)
        self.assertTrue(data["hostname"], "mesh status must expose a hostname")
        self.assertTrue(data["self_ip"], "mesh status must expose the node IP")
        self.assertIsInstance(data["peers"], list)

        self.assertEqual(data["peers_count"], len(data["peers"]))
        for peer in data["peers"]:
            self.assertIn("hostname", peer)
            self.assertIn("ip", peer)
            self.assertIn("online", peer)
            self.assertIsInstance(peer["online"], bool)

    async def test_mesh_status_requires_no_auth(self):
        """Mesh discovery is intentionally reachable before pairing."""
        resp = await self.client.get("/api/mesh/status")
        self.assertEqual(resp.status_code, 200)

    async def test_mesh_peers_discovery_endpoint(self):
        """Peers discover endpoint returns active mesh subnet peers."""
        resp = await self.client.get("/api/mesh/peers/discover")
        self.assertEqual(resp.status_code, 200, resp.text)
        data = resp.json()
        self.assertIn("status", data)
        self.assertIn("peers", data)
        self.assertIsInstance(data["peers"], list)
        for p in data["peers"]:
            self.assertIn("hostname", p)
            self.assertIn("direct", p)
            self.assertEqual(p["route"], "tailscale-wireguard")


class TestPairing(MeshE2ETestCase):

    async def test_pair_issues_valid_token_and_welcomes_client_on_bus(self):
        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)
        self.addAsyncCleanup(bus.unsubscribe, "the-council", socket)

        resp = await self.client.post("/api/auth/pair")
        self.assertEqual(resp.status_code, 200, resp.text)

        data = resp.json()
        self.assertEqual(data["status"], "paired")
        token = data["token"]
        self.assertTrue(token.startswith("talos_sk_"), "unexpected token format")
        self.assertTrue(verify_token(token), "issued token must verify")
        self.assertIn("label", data)
        self.assertIn("mesh", data)
        self.assertIs(data["mesh"]["active"], True)

        # The welcome message must reach clients on the room bus.
        await asyncio.sleep(0.1)
        welcome = socket.of_type("message_new")
        self.assertTrue(welcome, "no welcome message broadcast on the room bus")

        payload = welcome[0]["payload"]
        self.assertEqual(payload["room_id"], "the-council")
        self.assertEqual(payload["sender_id"], "hermes")
        self.assertEqual(payload["sender_role"], Role.agent.value)
        self.assertIn("mesh link verified", payload["content"].lower())

    async def test_paired_token_authenticates_protected_route(self):
        pair_resp = await self.client.post("/api/auth/pair")
        token = pair_resp.json()["token"]

        resp = await self.client.get("/api/auth/verify", headers=self.auth_header(token))
        self.assertEqual(resp.status_code, 200)
        self.assertIs(resp.json()["valid"], True)


class TestAuthVerify(MeshE2ETestCase):

    async def test_verify_passes_with_bearer_token(self):
        token = create_new_token("e2e-verify-client")
        resp = await self.client.get("/api/auth/verify", headers=self.auth_header(token))
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertIs(body["valid"], True)
        self.assertIn("app", body)

    async def test_verify_passes_with_master_token(self):
        master = get_master_token()
        self.assertTrue(master, "master token should be provisioned")
        resp = await self.client.get("/api/auth/verify", headers=self.auth_header(master))
        self.assertEqual(resp.status_code, 200)

    async def test_verify_fails_without_token(self):
        resp = await self.client.get("/api/auth/verify")
        self.assertEqual(resp.status_code, 401, resp.text)
        self.assertIn("WWW-Authenticate", resp.headers)

    async def test_verify_fails_with_garbage_token(self):
        resp = await self.client.get(
            "/api/auth/verify", headers=self.auth_header("talos_sk_not_a_real_token")
        )
        self.assertEqual(resp.status_code, 401)

    async def test_protected_route_requires_token(self):
        resp = await self.client.get("/api/rooms")
        self.assertEqual(resp.status_code, 401)


class TestCouncilOrchestration(MeshE2ETestCase):

    async def test_council_message_triggers_orchestrator_once(self):
        token = create_new_token("e2e-council-client")
        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)
        self.addAsyncCleanup(bus.unsubscribe, "the-council", socket)

        resp = await self.client.post(
            "/api/messages",
            headers=self.auth_header(token),
            json={
                "room_id": "the-council",
                "sender_id": "sarvesh",
                "sender_name": "Sarvesh",
                "sender_role": "human",
                "content": "How should we design the multi-agent turn scheduler?",
            },
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertEqual(body["room_id"], "the-council")
        self.assertEqual(body["sender_role"], Role.human.value)
        self.assertTrue(body["id"])

        # Let the staggered turns complete.
        await asyncio.sleep(1.0)

        agent_messages = [
            e["payload"]
            for e in socket.of_type("message_new")
            if e.get("payload", {}).get("sender_role") == Role.agent.value
        ]

        senders = {m["sender_id"] for m in agent_messages}
        self.assertTrue("hermes" in senders, "hermes must answer in council")
        self.assertTrue(len(agent_messages) >= 2, "bench agents must answer (no collision)")

        # Every agent reply must reference the triggering message.
        for m in agent_messages:
            self.assertEqual(m["reply_to_id"], body["id"])

        # Streaming contract is preserved on the wire.
        self.assertTrue(socket.of_type("typing_indicator"), "no typing indicator emitted")
        self.assertTrue(socket.of_type("token_delta"), "no token stream emitted")
        self.assertTrue(socket.of_type("thought_delta"), "no thought stream emitted")

    async def test_agent_replies_do_not_trigger_a_second_round(self):
        """No collision: agent answers must not spawn further turns."""
        token = create_new_token("e2e-council-loop-client")
        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)
        self.addAsyncCleanup(bus.unsubscribe, "the-council", socket)

        resp = await self.client.post(
            "/api/messages",
            headers=self.auth_header(token),
            json={
                "room_id": "the-council",
                "sender_id": "sarvesh",
                "sender_name": "Sarvesh",
                "sender_role": "human",
                "content": "@council architect the chamber",
            },
        )
        self.assertEqual(resp.status_code, 200)

        await asyncio.sleep(1.2)
        first_round = [
            e for e in socket.of_type("message_new")
            if e.get("payload", {}).get("sender_role") == Role.agent.value
        ]
        self.assertTrue(len(first_round) >= 2)

        # Give any (hypothetical) feedback loop time to fire.
        await asyncio.sleep(1.0)
        second_round = [
            e for e in socket.of_type("message_new")
            if e.get("payload", {}).get("sender_role") == Role.agent.value
        ]
        self.assertEqual(len(second_round), len(first_round), "agent replies must not re-trigger the bench")
        self.assertEqual(
            len({e["event_id"] for e in first_round}),
            len(first_round),
            "duplicate agent events detected",
        )

    async def test_message_in_dm_does_not_wake_the_full_bench(self):
        token = create_new_token("e2e-dm-client")
        socket = RecordingSocket("core-dm")
        await bus.subscribe("core-dm", socket)
        self.addAsyncCleanup(bus.unsubscribe, "core-dm", socket)

        resp = await self.client.post(
            "/api/messages",
            headers=self.auth_header(token),
            json={
                "room_id": "core-dm",
                "sender_id": "sarvesh",
                "sender_name": "Sarvesh",
                "sender_role": "human",
                "content": "Just checking in, no mention here.",
            },
        )
        self.assertEqual(resp.status_code, 200)

        await asyncio.sleep(0.8)
        agent_messages = [
            e for e in socket.of_type("message_new")
            if e.get("payload", {}).get("sender_role") == Role.agent.value
        ]
        self.assertEqual(agent_messages, [], "unaddressed DM must not wake the bench")

    async def test_post_message_requires_auth(self):
        resp = await self.client.post(
            "/api/messages",
            json={
                "room_id": "the-council",
                "sender_id": "sarvesh",
                "sender_name": "Sarvesh",
                "content": "hello",
            },
        )
        self.assertEqual(resp.status_code, 401)


class TestWebSocketBinary(unittest.TestCase):
    def test_websocket_binary_frame_handling(self):
        from starlette.testclient import TestClient

        init_db()
        init_auth_db()
        token = create_new_token("e2e-ws-binary-client")

        received_frames = []

        async def on_binary(data: bytes, r_id: str):
            received_frames.append((data, r_id))

        bus.register_binary_listener("the-council", on_binary)

        test_client = TestClient(app)
        with test_client.websocket_connect(f"/ws/the-council/client-audio?token={token}") as ws:
            sync_data = ws.receive_text()
            self.assertIn("room_sync", sync_data)
            voice_payload = b"\x00\xff\xee\xdd\x01\x02VOICE_MEMO_CHUNK"
            ws.send_bytes(voice_payload)

        self.assertEqual(len(received_frames), 1)
        self.assertEqual(received_frames[0], (b"\x00\xff\xee\xdd\x01\x02VOICE_MEMO_CHUNK", "the-council"))


if __name__ == "__main__":
    unittest.main()
