"""The generate route forwards to the core; it must not invent images.

The gallery read was already covered in test_image_gallery.py. This covers the
generate path: input validation, and a faithful forward (status and body) with
the caller's token.
"""

import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient

import server.main as main
import server.images as images
from server.auth import get_master_token


class _Res:
    def __init__(self, status_code: int, payload):
        self.status_code = status_code
        self._payload = payload
        self.text = str(payload)

    def json(self):
        return self._payload


class _Client:
    captured = {}

    def __init__(self, *a, **k):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, url, json=None, headers=None):
        _Client.captured = {"url": url, "json": json, "headers": headers}
        return _Res(200, {"id": "img_x", "prompt": json["prompt"]})


def _client():
    main.init_db()
    return TestClient(main.app)


def _auth():
    return {"Authorization": f"Bearer {get_master_token()}"}


class GenerateRouteTest(unittest.TestCase):
    def test_prompt_is_required(self):
        resp = _client().post("/api/image/generate", headers=_auth(), json={})
        self.assertEqual(resp.status_code, 400)
        resp = _client().post("/api/image/generate", headers=_auth(), json={"prompt": "  "})
        self.assertEqual(resp.status_code, 400)

    def test_valid_body_is_forwarded_to_the_core_with_the_callers_token(self):
        with patch("httpx.AsyncClient", _Client, create=True):
            resp = _client().post(
                "/api/image/generate",
                headers=_auth(),
                json={"prompt": "a red cube", "style": "cinematic", "aspect_ratio": "1:1"},
            )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(resp.json()["id"], "img_x")
        self.assertEqual(_Client.captured["json"]["prompt"], "a red cube")
        self.assertTrue(_Client.captured["headers"]["Authorization"].startswith("Bearer "))

    def test_core_failure_becomes_502_not_a_fake_image(self):
        class _Fail(_Client):
            async def post(self, url, json=None, headers=None):
                return _Res(500, {"error": "boom"})

        with patch("httpx.AsyncClient", _Fail, create=True):
            resp = _client().post(
                "/api/image/generate", headers=_auth(), json={"prompt": "x"}
            )
        self.assertEqual(resp.status_code, 502)

    def test_unauthenticated_is_rejected(self):
        resp = _client().post("/api/image/generate", json={"prompt": "x"})
        self.assertEqual(resp.status_code, 401)


if __name__ == "__main__":
    unittest.main()