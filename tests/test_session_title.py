"""The session-create endpoint must honour the caller's title.

Every session the app created used to come back as "New Session" because the
handler hardcoded the title and ignored the request body, which made the session
list indistinguishable.
"""

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient

import server.main as main
from server.auth import get_master_token
from server.opencode_bridge import OpenCodeResult


class _FakeClient:
    """Stands in for OpenCodeClient so no host is contacted."""

    last_title = None

    def __init__(self, *args, **kwargs):
        pass

    def create_session(self, title=None):
        _FakeClient.last_title = title
        return OpenCodeResult(
            ok=True,
            data={"data": {"id": "ses_test", "title": title, "projectID": "p1"}},
            error=None,
        )


def _client():
    main.init_db()
    return TestClient(main.app)


def _auth():
    return {"Authorization": f"Bearer {get_master_token()}"}


class CreateSessionTitleTest(unittest.TestCase):
    def test_title_from_body_is_passed_to_the_bridge(self):
        with patch.object(main, "OpenCodeClient", _FakeClient, create=True):
            with patch("server.opencode_bridge.OpenCodeClient", _FakeClient):
                resp = _client().post(
                    "/api/opencode/sessions",
                    headers=_auth(),
                    json={"title": "Fix login bug"},
                )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(_FakeClient.last_title, "Fix login bug")
        self.assertEqual(resp.json()["session"]["title"], "Fix login bug")

    def test_missing_body_falls_back_to_a_neutral_title(self):
        with patch("server.opencode_bridge.OpenCodeClient", _FakeClient):
            resp = _client().post("/api/opencode/sessions", headers=_auth())
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(_FakeClient.last_title, "New Session")

    def test_blank_title_falls_back(self):
        with patch("server.opencode_bridge.OpenCodeClient", _FakeClient):
            resp = _client().post(
                "/api/opencode/sessions", headers=_auth(), json={"title": "   "}
            )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertEqual(_FakeClient.last_title, "New Session")

    def test_unauthenticated_is_rejected(self):
        resp = _client().post("/api/opencode/sessions", json={"title": "x"})
        self.assertEqual(resp.status_code, 401)


if __name__ == "__main__":
    unittest.main()