"""Tests for the /api/providers provider health surface."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx
import os

from core.providers import REQUIRED_PROVIDER_IDS
from server.auth import create_new_token, init_auth_db
from server.db import init_db
from server.main import app

REQUIRED_IDS = set(REQUIRED_PROVIDER_IDS)


class ProviderEndpointsTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        os.environ["TALOS_DISABLE_PROVIDER_PROBING"] = "1"
        init_db()
        init_auth_db()
        self.transport = httpx.ASGITransport(app=app)
        self.client = httpx.AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.token = create_new_token("e2e-providers-client")
        self.addCleanup(self._cleanup)

    async def _cleanup(self) -> None:
        await self.client.aclose()

    def auth_header(self) -> dict:
        return {"Authorization": f"Bearer {self.token}"}

    async def test_providers_requires_auth(self):
        resp = await self.client.get("/api/providers")
        self.assertEqual(resp.status_code, 401)

    async def test_providers_lists_all_required_providers(self):
        resp = await self.client.get("/api/providers", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        providers = resp.json()
        self.assertEqual(len(providers), 7, providers)
        ids = {p["id"] for p in providers}
        self.assertEqual(ids, REQUIRED_IDS, ids)

    async def test_provider_shape_has_latency_status_context(self):
        resp = await self.client.get("/api/providers", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200)
        for p in resp.json():
            self.assertIn("id", p)
            self.assertIn("name", p)
            self.assertIn("vendor", p)
            self.assertIn("status", p)
            self.assertIn("latency_ms", p)
            self.assertIn("context_length", p)
            self.assertIn("credential_present", p)
            self.assertIn("credential_source", p)
            self.assertIn("checked_at", p)
            self.assertIsInstance(p["status"], str)

    async def test_provider_summary(self):
        resp = await self.client.get("/api/providers/summary", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertEqual(body["total"], 7)
        self.assertIsInstance(body["operational"], int)
        self.assertIsInstance(body["average_latency_ms"], (int, float, type(None)))

    async def test_single_provider_get(self):
        resp = await self.client.get("/api/providers/qwen", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertEqual(body["id"], "qwen")
        self.assertEqual(body["name"], "Qwen")
        self.assertEqual(body["vendor"], "Alibaba Cloud")
        self.assertEqual(body["context_length"], 131072)

    async def test_single_provider_not_found(self):
        resp = await self.client.get("/api/providers/no-such-provider", headers=self.auth_header())
        self.assertEqual(resp.status_code, 404)


if __name__ == "__main__":
    unittest.main()
