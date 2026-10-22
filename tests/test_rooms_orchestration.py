"""Tests for /api rooms with multi-agent orchestration (bench management)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx

from server.auth import create_new_token, init_auth_db
from server.db import init_db
from server.main import app


class RoomsOrchestrationTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        init_db()
        init_auth_db()
        self.transport = httpx.ASGITransport(app=app)
        self.client = httpx.AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.token = create_new_token("e2e-rooms-client")
        self.addCleanup(self._cleanup)

    async def _cleanup(self) -> None:
        await self.client.aclose()

    def auth_header(self) -> dict:
        return {"Authorization": f"Bearer {self.token}"}

    async def test_rooms_requires_auth(self):
        resp = await self.client.get("/api/rooms")
        self.assertEqual(resp.status_code, 401)

    async def test_rooms_list_seeded_rooms(self):
        resp = await self.client.get("/api/rooms", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        rooms = resp.json()
        self.assertGreaterEqual(len(rooms), 2)
        ids = {r["id"] for r in rooms}
        self.assertIn("the-council", ids)
        self.assertTrue(any("dm" in r_id for r_id in ids))

    async def test_room_crud_and_agent_bench(self):
        # Create
        resp = await self.client.post(
            "/api/rooms",
            headers=self.auth_header(),
            json={"name": "Bench Room", "description": "multi-agent orchestration room", "agent_ids": ["core", "omnirush"]},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        room = resp.json()
        room_id = room["id"]
        self.assertEqual(room["name"], "Bench Room")
        self.assertEqual(room["agent_ids"], ["core", "omnirush"])

        # List includes it
        resp = await self.client.get("/api/rooms", headers=self.auth_header())
        self.assertTrue(any(r["id"] == room_id for r in resp.json()))

        # Agents on the bench
        resp = await self.client.get(f"/api/rooms/{room_id}/agents", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        agents = resp.json()
        self.assertEqual(len(agents), 1)
        self.assertEqual(agents[0]["id"], "omnirush")

        # Replace the bench
        resp = await self.client.put(
            f"/api/rooms/{room_id}/agents",
            headers=self.auth_header(),
            json={"agent_ids": ["hermes", "omnirush"]},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(resp.json()["agent_ids"], ["hermes", "omnirush"])

        # Update metadata
        resp = await self.client.put(
            f"/api/rooms/{room_id}",
            headers=self.auth_header(),
            json={"name": "Bench Room v2"},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(resp.json()["name"], "Bench Room v2")

        # Get by id
        resp = await self.client.get(f"/api/rooms/{room_id}", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(resp.json()["id"], room_id)

        # Delete
        resp = await self.client.delete(f"/api/rooms/{room_id}", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        resp = await self.client.get(f"/api/rooms/{room_id}", headers=self.auth_header())
        self.assertEqual(resp.status_code, 404)

    async def test_delete_unknown_room_404(self):
        resp = await self.client.delete("/api/rooms/no-such-room", headers=self.auth_header())
        self.assertEqual(resp.status_code, 404)

    async def test_agents_on_nonexistent_room_404(self):
        resp = await self.client.get("/api/rooms/no-such-room/agents", headers=self.auth_header())
        self.assertEqual(resp.status_code, 404)


if __name__ == "__main__":
    unittest.main()
