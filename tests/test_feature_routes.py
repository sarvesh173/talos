"""Tests for the feature HTTP routes.

These cover the gap that made the features unreachable: the modules existed and
their own tests passed, but no router was mounted, so nothing could call them
over HTTP. Every test goes through ``TestClient`` against the real app, which is
the only way to prove a route is actually mounted — inspecting ``app.routes`` is
misleading on FastAPI 0.141+, where an included router stays a lazy
``_IncludedRouter`` node until the schema is built.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient

import server.main as main
from server.auth import get_master_token


def _client():
    main.init_db()
    return TestClient(main.app)


def _auth():
    return {"Authorization": f"Bearer {get_master_token()}"}


class MountedRoutesTest(unittest.TestCase):
    """Every feature route must appear in the generated OpenAPI schema."""

    def test_feature_paths_are_mounted(self):
        schema = main.app.openapi()["paths"]
        expected = {
            "/api/rooms/{room_id}/fork",
            "/api/rooms/{room_id}/lineage",
            "/api/search",
            "/api/search/status",
            "/api/search/reindex",
            "/api/prompts",
            "/api/prompts/{prompt_id}",
            "/api/prompts/{prompt_id}/render",
            "/api/compare",
            "/api/agents/{agent_id}/review",
        }
        missing = expected - set(schema)
        self.assertEqual(missing, set(), f"routes not mounted: {sorted(missing)}")


class AuthTest(unittest.TestCase):
    """Auth is not optional on the feature surface."""

    def setUp(self):
        self.client = _client()

    def test_search_requires_auth(self):
        self.assertEqual(self.client.get("/api/search?q=x").status_code, 401)

    def test_prompts_requires_auth(self):
        self.assertEqual(self.client.get("/api/prompts").status_code, 401)

    def test_fork_requires_auth(self):
        resp = self.client.post("/api/rooms/r/fork", json={"message_id": "m1"})
        self.assertEqual(resp.status_code, 401)

    def test_compare_requires_auth(self):
        resp = self.client.post("/api/compare", json={"prompt": "hi", "models": ["a", "b"]})
        self.assertEqual(resp.status_code, 401)

    def test_review_requires_auth(self):
        self.assertEqual(self.client.get("/api/agents/x/review").status_code, 401)


class SearchRouteTest(unittest.TestCase):
    def setUp(self):
        self.client = _client()
        self.headers = _auth()

    def test_status_reports_a_real_backend(self):
        resp = self.client.get("/api/search/status", headers=self.headers)
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        # The backend is either FTS5 or the documented fallback — never a lie
        # about an index that does not exist.
        self.assertIn(body["backend"], ("fts5", "like"))
        self.assertIn("indexed_rows", body)

    def test_empty_query_returns_no_hits(self):
        resp = self.client.get("/api/search?q=", headers=self.headers)
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["hits"], [])

    def test_reindex_is_idempotent(self):
        first = self.client.post("/api/search/reindex", headers=self.headers)
        second = self.client.post("/api/search/reindex", headers=self.headers)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(
            first.json().get("indexed_rows"), second.json().get("indexed_rows")
        )


class PromptLibraryRouteTest(unittest.TestCase):
    def setUp(self):
        self.client = _client()
        self.headers = _auth()

    def test_crud_round_trip(self):
        created = self.client.post(
            "/api/prompts",
            json={"title": "Review", "body": "Review {{scope}} for {{who}}"},
            headers=self.headers,
        )
        self.assertEqual(created.status_code, 201)
        pid = created.json()["id"]
        self.assertEqual(created.json()["variables"], ["scope", "who"])

        fetched = self.client.get(f"/api/prompts/{pid}", headers=self.headers)
        self.assertEqual(fetched.status_code, 200)
        self.assertEqual(fetched.json()["title"], "Review")

        patched = self.client.patch(
            f"/api/prompts/{pid}", json={"category": "review"}, headers=self.headers
        )
        self.assertEqual(patched.status_code, 200)
        self.assertEqual(patched.json()["category"], "review")

        listed = self.client.get("/api/prompts", headers=self.headers)
        self.assertIn(pid, [p["id"] for p in listed.json()])

        deleted = self.client.delete(f"/api/prompts/{pid}", headers=self.headers)
        self.assertEqual(deleted.status_code, 204)

        gone = self.client.get(f"/api/prompts/{pid}", headers=self.headers)
        self.assertEqual(gone.status_code, 404)

    def test_render_reports_missing_instead_of_guessing(self):
        created = self.client.post(
            "/api/prompts",
            json={"title": "T", "body": "Hello {{name}}, from {{place}}"},
            headers=self.headers,
        )
        pid = created.json()["id"]
        rendered = self.client.post(
            f"/api/prompts/{pid}/render",
            json={"values": {"name": "Sarvesh"}},
            headers=self.headers,
        )
        self.assertEqual(rendered.status_code, 200)
        body = rendered.json()
        self.assertIn("Sarvesh", body["text"])
        # An unsupplied variable is reported and left intact, never blanked.
        self.assertEqual(body["missing"], ["place"])
        self.assertIn("{{place}}", body["text"])
        self.client.delete(f"/api/prompts/{pid}", headers=self.headers)

    def test_malformed_placeholder_is_400(self):
        resp = self.client.post(
            "/api/prompts",
            json={"title": "Bad", "body": "Hello {{unclosed"},
            headers=self.headers,
        )
        self.assertEqual(resp.status_code, 400)

    def test_unknown_prompt_is_404(self):
        resp = self.client.get("/api/prompts/does-not-exist", headers=self.headers)
        self.assertEqual(resp.status_code, 404)


class ForkRouteTest(unittest.TestCase):
    def setUp(self):
        self.client = _client()
        self.headers = _auth()

    def test_unknown_room_is_404_not_empty_200(self):
        resp = self.client.post(
            "/api/rooms/definitely-not-a-room/fork",
            json={"message_id": "m1"},
            headers=self.headers,
        )
        self.assertEqual(resp.status_code, 404)

    def test_lineage_of_a_real_room_includes_itself(self):
        rooms = self.client.get("/api/rooms", headers=self.headers).json()
        self.assertTrue(rooms, "the database needs at least one room for this test")
        rid = rooms[0]["id"]
        resp = self.client.get(f"/api/rooms/{rid}/lineage", headers=self.headers)
        self.assertEqual(resp.status_code, 200)
        self.assertIn(rid, [ref["id"] for ref in resp.json()])


class CompareRouteTest(unittest.TestCase):
    def setUp(self):
        self.client = _client()
        self.headers = _auth()

    def test_model_count_bounds(self):
        one = self.client.post(
            "/api/compare", json={"prompt": "hi", "models": ["a"]}, headers=self.headers
        )
        self.assertEqual(one.status_code, 422)
        five = self.client.post(
            "/api/compare",
            json={"prompt": "hi", "models": ["a", "b", "c", "d", "e"]},
            headers=self.headers,
        )
        self.assertEqual(five.status_code, 422)

    def test_stream_terminates_with_done(self):
        resp = self.client.post(
            "/api/compare",
            json={"prompt": "hi", "models": ["no/such-model", "also/none"], "timeout_s": 2},
            headers=self.headers,
        )
        self.assertEqual(resp.status_code, 200)
        # The stream must always close with an explicit terminator; a client
        # reading SSE needs to know the run finished rather than hung.
        self.assertIn("[DONE]", resp.text)


class ReviewRouteTest(unittest.TestCase):
    def setUp(self):
        self.client = _client()
        self.headers = _auth()

    def test_unknown_agent_has_a_shape_that_is_not_a_review(self):
        resp = self.client.get("/api/agents/ghost-agent/review", headers=self.headers)
        # Either a 404 or a real review with zero changes; never a crash.
        self.assertIn(resp.status_code, (200, 404))
        if resp.status_code == 200:
            self.assertIn("summary", resp.json())


if __name__ == "__main__":
    unittest.main()
