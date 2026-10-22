"""Tests for token management: masked listing, revocation, summary."""

import sys
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx

from server.auth import create_new_token, get_master_token, init_auth_db
from server.db import init_db
from server.main import app


class TokenManagementTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        init_db()
        init_auth_db()
        self.transport = httpx.ASGITransport(app=app)
        self.client = httpx.AsyncClient(transport=self.transport, base_url="http://talos.test")
        self.token = create_new_token("e2e-token-client")
        self.addCleanup(self._cleanup)

    async def _cleanup(self) -> None:
        await self.client.aclose()

    def auth_header(self, token: str | None = None) -> dict:
        value = token if token is not None else self.token
        return {"Authorization": f"Bearer {value}"}

    async def test_tokens_list_requires_auth(self):
        resp = await self.client.get("/api/auth/tokens")
        self.assertEqual(resp.status_code, 401)

    async def test_tokens_are_masked_never_raw(self):
        resp = await self.client.get("/api/auth/tokens", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        tokens = resp.json()
        self.assertGreaterEqual(len(tokens), 1)
        # Raw secrets must never be returned by the list endpoint.
        self.assertNotIn(self.token, resp.text)
        for t in tokens:
            self.assertIn("token_id", t)
            self.assertIn("label", t)
            self.assertIn("masked_token", t)
            self.assertTrue(t["masked_token"].endswith("...") or len(t["masked_token"]) < 40)

    async def test_list_includes_master_token(self):
        resp = await self.client.get("/api/auth/tokens", headers=self.auth_header())
        tokens = resp.json()
        master = get_master_token()
        self.assertTrue(master)
        masters = [t for t in tokens if t.get("is_master")]
        self.assertEqual(len(masters), 1)
        self.assertTrue(masters[0]["is_master"])

    async def test_token_summary_shape(self):
        resp = await self.client.get("/api/auth/tokens/summary", headers=self.auth_header())
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertIn("total", body)
        self.assertIn("active", body)
        self.assertIn("revoked", body)
        self.assertIn("by_role", body)
        self.assertGreaterEqual(body["total"], 1)

    async def test_revoke_issued_token(self):
        unique_label = f"e2e-revoke-client-{uuid.uuid4().hex[:8]}"
        create_new_token(unique_label)
        from server.auth import get_token, list_tokens

        created = [t for t in list_tokens() if t.label == unique_label]
        self.assertEqual(len(created), 1)
        token_id = created[0].token_id

        resp = await self.client.post(
            "/api/auth/tokens/revoke",
            headers=self.auth_header(),
            json={"token_id": token_id},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(resp.json()["status"], "revoked")
        self.assertTrue(get_token(token_id).is_revoked)

    async def test_revoke_master_token_rejected(self):
        master = get_master_token()
        from server.auth import list_tokens

        master_row = [t for t in list_tokens() if t.is_master][0]
        resp = await self.client.post(
            "/api/auth/tokens/revoke",
            headers=self.auth_header(),
            json={"token_id": master_row.token_id},
        )
        self.assertEqual(resp.status_code, 404)

    async def test_revoke_unknown_token_rejected(self):
        resp = await self.client.post(
            "/api/auth/tokens/revoke",
            headers=self.auth_header(),
            json={"token_id": "does-not-exist"},
        )
        self.assertEqual(resp.status_code, 404)


if __name__ == "__main__":
    unittest.main()
