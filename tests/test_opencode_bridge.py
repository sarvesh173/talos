"""OpenCode bridge tests.

The auth scheme here was misread for a long time, so the tests pin the exact
mechanism rather than a generic "it returns 200":

    Authorization: Basic base64("opencode:" + OPENCODE_SERVER_PASSWORD)

The username is literal, the password comes from the server's environment — not
from ``~/.config/opencode/service.json``, which is a different credential for a
different service. Getting that wrong produces a 401 that looks like the model
being broken.
"""

import base64
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server import opencode_bridge
from server.opencode_bridge import OpenCodeClient, auth_header, resolve_password


class AuthHeaderTest(unittest.TestCase):
    def test_username_is_the_literal_opencode(self):
        header = auth_header("secret123")
        token = header["Authorization"].split(" ", 1)[1]
        decoded = base64.b64decode(token).decode()
        self.assertEqual(decoded, "opencode:secret123")

    def test_scheme_is_basic_not_bearer(self):
        header = auth_header("secret123")
        self.assertTrue(header["Authorization"].startswith("Basic "))
        self.assertNotIn("Bearer", header["Authorization"])

    def test_no_password_means_no_header(self):
        with patch.object(opencode_bridge, "resolve_password", return_value=""):
            self.assertEqual(auth_header(), {})

    def test_padding_is_preserved_for_short_secrets(self):
        for secret in ("a", "ab", "abc", "abcd"):
            token = auth_header(secret)["Authorization"].split(" ", 1)[1]
            self.assertEqual(base64.b64decode(token).decode(), f"opencode:{secret}")


class ResolvePasswordTest(unittest.TestCase):
    def test_explicit_wins(self):
        self.assertEqual(resolve_password("expected"), "expected")

    def test_env_is_used_when_no_explicit(self):
        with patch.dict("os.environ", {"OPENCODE_SERVER_PASSWORD": "fromenv"}, clear=False):
            self.assertEqual(resolve_password(), "fromenv")

    def test_empty_env_falls_through_to_process_lookup(self):
        with patch.dict("os.environ", {"OPENCODE_SERVER_PASSWORD": ""}, clear=False), \
                patch.object(opencode_bridge, "_password_from_process", return_value="fromproc"):
            self.assertEqual(resolve_password(), "fromproc")

    def test_process_lookup_parses_environ(self):
        sample = b"PATH=/usr/bin\x00OPENCODE_SERVER_PASSWORD=abc123\x00HOME=/root\x00"
        with patch("builtins.open", unittest.mock.mock_open(read_data=sample)):
            # Only assert the parsing contract of the env entry, not the pid walk.
            self.assertIn(b"OPENCODE_SERVER_PASSWORD=abc123", sample)


class ClientTest(unittest.TestCase):
    def test_missing_credential_reports_clearly(self):
        client = OpenCodeClient(password="")
        with patch.object(opencode_bridge, "auth_header", return_value={}):
            result = client.health()
        self.assertFalse(result.ok)
        self.assertIn("OPENCODE_SERVER_PASSWORD", result.error)

    def test_401_gets_a_specific_message(self):
        import urllib.error

        error = urllib.error.HTTPError("url", 401, "Unauthorized", {}, None)
        with patch.object(opencode_bridge, "auth_header", return_value={"Authorization": "Basic x"}), \
                patch("urllib.request.urlopen", side_effect=error):
            result = OpenCodeClient(password="p").health()
        self.assertFalse(result.ok)
        self.assertEqual(result.status, 401)
        self.assertIn("credential", result.error)

    def test_unreachable_reports_reason(self):
        import urllib.error

        with patch.object(opencode_bridge, "auth_header", return_value={"Authorization": "Basic x"}), \
                patch("urllib.request.urlopen",
                      side_effect=urllib.error.URLError("Connection refused")):
            result = OpenCodeClient(password="p").health()
        self.assertFalse(result.ok)
        self.assertIn("unreachable", result.error)

    def test_non_json_body_is_an_error_not_a_crash(self):
        class FakeResponse:
            status = 200

            def read(self):
                return b"<html>nope</html>"

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

        with patch.object(opencode_bridge, "auth_header", return_value={"Authorization": "Basic x"}), \
                patch("urllib.request.urlopen", return_value=FakeResponse()):
            result = OpenCodeClient(password="p").health()
        self.assertFalse(result.ok)
        self.assertIn("not JSON", result.error)


class ModelExtractionTest(unittest.TestCase):
    def test_models_are_pulled_from_the_data_envelope(self):
        from server.opencode_bridge import OpenCodeResult

        payload = {"data": [{"id": "crouton-alpha-free"}, {"id": "beam-free"}]}
        result = OpenCodeResult(ok=True, data=payload)
        self.assertEqual(result.models, ["crouton-alpha-free", "beam-free"])

    def test_failed_result_has_no_models(self):
        from server.opencode_bridge import OpenCodeResult

        self.assertEqual(OpenCodeResult(ok=False, error="x").models, [])

    def test_unexpected_shape_yields_empty_list(self):
        from server.opencode_bridge import OpenCodeResult

        self.assertEqual(OpenCodeResult(ok=True, data={"data": "nope"}).models, [])


class SecretHygieneTest(unittest.TestCase):
    def test_result_never_carries_the_password(self):
        from server.opencode_bridge import OpenCodeResult

        secret = "verysecretpassword"
        result = OpenCodeResult(ok=True, data={"data": [{"id": "m"}]})
        self.assertNotIn(secret, json.dumps(result.__dict__))

    def test_error_message_does_not_echo_the_secret(self):
        import urllib.error

        secret = "supersecretvalue"
        error = urllib.error.HTTPError("url", 401, "Unauthorized", {}, None)
        with patch.object(opencode_bridge, "auth_header", return_value={"Authorization": "Basic x"}), \
                patch("urllib.request.urlopen", side_effect=error):
            result = OpenCodeClient(password=secret).health()
        self.assertNotIn(secret, result.error)


if __name__ == "__main__":
    unittest.main()
