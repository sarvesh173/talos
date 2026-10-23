"""Unit and Integration Tests for Real-time Latency Benchmarking and Failover Routing."""

import unittest
from httpx import ASGITransport, AsyncClient
from server.main import app
from server.auth import create_new_token


class TestFailoverRouting(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self.transport = ASGITransport(app=app)
        self.client = AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.token = create_new_token("Test Failover Token")
        self.auth_headers = {"Authorization": f"Bearer {self.token}"}

    async def asyncTearDown(self):
        await self.client.aclose()

    async def test_benchmark_unauthorized(self):
        resp = await self.client.post("/api/providers/benchmark")
        self.assertEqual(resp.status_code, 401)

    async def test_failover_unauthorized(self):
        resp = await self.client.get("/api/providers/failover")
        self.assertEqual(resp.status_code, 401)

    async def test_benchmark_and_failover_route_flow(self):
        # Trigger real-time benchmarking
        resp = await self.client.post("/api/providers/benchmark", headers=self.auth_headers)
        self.assertEqual(resp.status_code, 200, resp.text)
        data = resp.json()

        self.assertIn("primary_provider", data)
        self.assertIn("failover_chain", data)
        self.assertIn("rankings", data)
        self.assertIsInstance(data["failover_chain"], list)
        self.assertIsInstance(data["rankings"], list)
        self.assertGreater(len(data["rankings"]), 0)

        # Check rankings fields
        for r in data["rankings"]:
            self.assertIn("id", r)
            self.assertIn("name", r)
            self.assertIn("status", r)
            self.assertIn("tier", r)

        # Retrieve cached failover state
        failover_resp = await self.client.get("/api/providers/failover", headers=self.auth_headers)
        self.assertEqual(failover_resp.status_code, 200)
        cached_data = failover_resp.json()
        self.assertEqual(cached_data["primary_provider"], data["primary_provider"])
        self.assertEqual(cached_data["failover_chain"], data["failover_chain"])
