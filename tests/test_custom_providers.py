"""Custom provider CRUD — the endpoints ProviderVaultScreen already called.

That screen has offered "add a provider" since it was written: POST
/api/providers/custom, list, fetch-models, delete. None of the four existed, so
every action in it failed and the vault could never be filled from the app.

These tests drive the module directly against a temp store and a temp .env, and
assert the one thing that matters most: the real key is never returned.
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server import custom_providers


class _Sandbox:
    """Redirect the provider store and ~/.hermes/.env into a temp dir."""

    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.store = root / "custom_providers.json"
        self.home = root / "home"
        (self.home / ".hermes").mkdir(parents=True)
        self._orig_store = custom_providers.STORE_PATH
        self._orig_home = None
        self._orig_env = dict(os.environ)

    def __enter__(self):
        custom_providers.STORE_PATH = self.store
        import pathlib

        self._orig_home = pathlib.Path.home
        pathlib.Path.home = staticmethod(lambda: self.home)
        return self

    def __exit__(self, *exc):
        custom_providers.STORE_PATH = self._orig_store
        import pathlib

        if self._orig_home is not None:
            pathlib.Path.home = self._orig_home
        os.environ.clear()
        os.environ.update(self._orig_env)
        self.tmp.cleanup()
        return False

    def env_text(self) -> str:
        p = self.home / ".hermes" / ".env"
        return p.read_text() if p.exists() else ""


def _run(coro):
    import asyncio

    return asyncio.new_event_loop().run_until_complete(coro)


class CreateTest(unittest.TestCase):
    def test_create_stores_the_provider(self):
        with _Sandbox() as box:
            req = custom_providers.CreateProviderRequest(
                name="Local vLLM", base_url="http://127.0.0.1:8000/v1/", api_key="sk-secret-123456789"
            )
            view = _run(custom_providers.create_custom_provider(req))

            self.assertTrue(view.id.startswith("c_"))
            self.assertEqual(view.name, "Local vLLM")
            self.assertEqual(view.base_url, "http://127.0.0.1:8000/v1")  # trailing slash trimmed
            self.assertTrue(view.key_set)

            rows = json.loads(box.store.read_text())
            self.assertEqual(len(rows), 1)

    def test_the_real_key_is_never_returned(self):
        """The vault lists providers; a list must not leak the credential."""
        with _Sandbox():
            secret = "sk-live-DO-NOT-LEAK-abcdef123456"
            req = custom_providers.CreateProviderRequest(
                name="p", base_url="https://api.example.com/v1", api_key=secret
            )
            view = _run(custom_providers.create_custom_provider(req))
            listed = _run(custom_providers.list_custom_providers())

            self.assertNotIn(secret, view.api_key)
            self.assertNotIn(secret, listed[0].api_key)
            self.assertNotIn(secret, json.dumps(view.model_dump()))
            self.assertNotIn(secret, json.dumps([v.model_dump() for v in listed]))

    def test_key_is_written_where_the_prober_reads(self):
        """A provider the app added must be probe-able without a second config path."""
        with _Sandbox() as box:
            req = custom_providers.CreateProviderRequest(
                name="p", base_url="https://api.example.com/v1", api_key="sk-abc123456789"
            )
            view = _run(custom_providers.create_custom_provider(req))
            text = box.env_text()
            self.assertIn("TALOS_CUSTOM_", text)
            self.assertIn("sk-abc123456789", text)
            self.assertIn(view.id.upper().replace("C_", "C_"), text)

    def test_base_url_must_be_http(self):
        with _Sandbox():
            req = custom_providers.CreateProviderRequest(
                name="bad", base_url="ftp://example.com", api_key="k"
            )
            with self.assertRaises(custom_providers.HTTPException) as ctx:
                _run(custom_providers.create_custom_provider(req))
            self.assertEqual(ctx.exception.status_code, 400)

    def test_duplicate_base_url_is_refused(self):
        with _Sandbox():
            req = custom_providers.CreateProviderRequest(
                name="one", base_url="https://same.example.com/v1", api_key="k1"
            )
            _run(custom_providers.create_custom_provider(req))
            with self.assertRaises(custom_providers.HTTPException) as ctx:
                _run(custom_providers.create_custom_provider(req))
            self.assertEqual(ctx.exception.status_code, 409)


class ListAndDeleteTest(unittest.TestCase):
    def test_empty_store_lists_nothing(self):
        with _Sandbox():
            self.assertEqual(_run(custom_providers.list_custom_providers()), [])

    def test_delete_removes_it(self):
        with _Sandbox():
            req = custom_providers.CreateProviderRequest(
                name="gone", base_url="https://x.example.com/v1", api_key="k"
            )
            view = _run(custom_providers.create_custom_provider(req))
            result = _run(custom_providers.delete_custom_provider(view.id))
            self.assertTrue(result["deleted"])
            self.assertEqual(_run(custom_providers.list_custom_providers()), [])

    def test_delete_unknown_is_a_404_not_a_silent_success(self):
        with _Sandbox():
            with self.assertRaises(custom_providers.HTTPException) as ctx:
                _run(custom_providers.delete_custom_provider("c_nope"))
            self.assertEqual(ctx.exception.status_code, 404)


class FetchModelsTest(unittest.TestCase):
    def test_unreachable_provider_returns_empty_not_fake_models(self):
        """The no-fabrication rule: no host means no models, never a placeholder list."""
        with _Sandbox():
            req = custom_providers.CreateProviderRequest(
                name="dead", base_url="http://127.0.0.1:9/v1", api_key="k"
            )
            view = _run(custom_providers.create_custom_provider(req))
            self.assertEqual(_run(custom_providers.fetch_custom_provider_models(view.id)), [])

    def test_unknown_provider_is_404(self):
        with _Sandbox():
            with self.assertRaises(custom_providers.HTTPException) as ctx:
                _run(custom_providers.fetch_custom_provider_models("c_ghost"))
            self.assertEqual(ctx.exception.status_code, 404)


class RouteMountTest(unittest.TestCase):
    def test_all_three_paths_are_reachable_by_clients(self):
        from server.main import app

        paths = app.openapi()["paths"]
        self.assertIn("/api/providers/custom", paths)
        self.assertIn("get", paths["/api/providers/custom"])
        self.assertIn("post", paths["/api/providers/custom"])
        self.assertIn("/api/providers/custom/{provider_id}/fetch-models", paths)
        self.assertIn("/api/providers/custom/{provider_id}", paths)


if __name__ == "__main__":
    unittest.main()
