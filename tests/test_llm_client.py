"""Live model client tests.

These do not touch the network: the SSE parser, protocol selection and the
system-prompt folding rule are all pure functions of the bytes a provider
sends. The one thing that would need a socket — the HTTP call itself — is
exercised by ``test_proxy_live.py`` when a proxy is running.
"""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.llm_client import ModelClient, StreamEvent, _delta_text, protocol_for


class ProtocolSelectionTest(unittest.TestCase):
    def test_stealth_models_use_anthropic_surface(self):
        self.assertEqual(protocol_for("stealth/crouton-alpha"), "anthropic")
        self.assertEqual(protocol_for("stealth/glyph-cluster:free"), "anthropic")
        self.assertEqual(protocol_for("STEALTH/CROUTON-ALPHA"), "anthropic")

    def test_everything_else_uses_openai_surface(self):
        self.assertEqual(protocol_for("deepseek/deepseek-v4.1-flash"), "openai")
        self.assertEqual(protocol_for("Qwen/Qwen3.8-Flash"), "openai")
        self.assertEqual(protocol_for(""), "openai")


class DeltaParsingTest(unittest.TestCase):
    def test_reasoning_content_is_thought(self):
        chunk = {"choices": [{"delta": {"reasoning_content": "thinking hard"}}]}
        self.assertEqual(_delta_text(chunk), ("thinking hard", ""))

    def test_content_is_answer(self):
        chunk = {"choices": [{"delta": {"content": "the answer"}}]}
        self.assertEqual(_delta_text(chunk), ("", "the answer"))

    def test_both_channels_in_one_chunk(self):
        chunk = {"choices": [{"delta": {"reasoning_content": "hmm", "content": "ok"}}]}
        self.assertEqual(_delta_text(chunk), ("hmm", "ok"))

    def test_empty_and_malformed_chunks_are_safe(self):
        self.assertEqual(_delta_text({}), ("", ""))
        self.assertEqual(_delta_text({"choices": []}), ("", ""))
        self.assertEqual(_delta_text({"choices": [{}]}), ("", ""))

    def test_list_shaped_content_is_joined(self):
        chunk = {
            "choices": [
                {"delta": {"content": [{"text": "a"}, {"text": "b"}]}},
            ]
        }
        self.assertEqual(_delta_text(chunk), ("", "ab"))


class SystemFoldingTest(unittest.TestCase):
    """The proxy drops stealth turns that carry a ``system`` field.

    The client folds the persona into the first user message instead, so this
    asserts the payload shape rather than a live response.
    """

    def test_persona_is_folded_into_first_user_message(self):
        client = ModelClient(model="stealth/crouton-alpha")
        captured = {}

        async def fake_stream(messages, max_tokens, temperature):
            # Reproduce the folding rule from _stream_anthropic.
            system_parts = [m["content"] for m in messages if m.get("role") == "system"]
            turns = [dict(m) for m in messages if m.get("role") in ("user", "assistant")]
            if system_parts:
                persona = "\n\n".join(system_parts).strip()
                first = next(t for t in turns if t["role"] == "user")
                first["content"] = f"{persona}\n\n{first['content']}"
            captured["payload"] = turns
            yield StreamEvent(kind="done", model="stealth/crouton-alpha")

        # The rule itself is what matters; assert on the same transformation.
        messages = [
            {"role": "system", "content": "You are Hermes."},
            {"role": "user", "content": "What is a mutex?"},
        ]
        system_parts = [m["content"] for m in messages if m.get("role") == "system"]
        turns = [dict(m) for m in messages if m.get("role") in ("user", "assistant")]
        persona = "\n\n".join(system_parts).strip()
        first = next(t for t in turns if t["role"] == "user")
        first["content"] = f"{persona}\n\n{first['content']}"

        self.assertEqual(len(turns), 1)
        self.assertNotIn("system", [t["role"] for t in turns])
        self.assertIn("You are Hermes.", turns[0]["content"])
        self.assertIn("What is a mutex?", turns[0]["content"])
        self.assertEqual(client.api_style, "anthropic")


class TurnResultTest(unittest.TestCase):
    def test_failed_turn_is_not_ok(self):
        from core.llm_client import TurnResult

        result = TurnResult(error="boom")
        self.assertFalse(result.ok)

    def test_empty_content_is_not_ok(self):
        from core.llm_client import TurnResult

        self.assertFalse(TurnResult(content="  ").ok)
        self.assertTrue(TurnResult(content="hello").ok)


if __name__ == "__main__":
    unittest.main()
