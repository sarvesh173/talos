"""Model comparison fan-out tests.

No network: every stream is a fake client injected via ``client_factory``
(or a monkeypatched ModelClient). Covers validation, per-model lifecycle,
interleaving, failure isolation, per-model timeout, cancellation, honest
metrics, and credential hygiene.
"""

import asyncio
import os
import sys
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.llm_client import StreamEvent
from server import model_compare
from server.model_compare import (
    CompareError,
    Delta,
    Failed,
    Finished,
    InvalidModelCountError,
    ModelRef,
    Started,
    active_task_count,
    cancel_all,
    compare,
)

FAKE_KEY = "sk-fake-SECRETKEY1234567890"


def _tok(model, text):
    return StreamEvent(kind="token", text=text, model=model)


def _done(model, elapsed=0.01, ttft_ms=5):
    return StreamEvent(kind="done", model=model, elapsed=elapsed, detail=str(ttft_ms))


class FakeClient:
    """Scripted stand-in for ModelClient.stream.

    Script items: StreamEvent to yield, an Exception to raise, an
    asyncio.Event to wait on, or a float to sleep for.
    """

    def __init__(self, model, script):
        self.model = model
        self.script = list(script)
        self.messages = None

    async def stream(self, messages, max_tokens=512, temperature=0.7):
        self.messages = list(messages)
        for item in self.script:
            if isinstance(item, Exception):
                raise item
            if isinstance(item, asyncio.Event):
                await item.wait()
            elif isinstance(item, (int, float)):
                await asyncio.sleep(float(item))
            else:
                yield item


def _factory(scripts):
    def factory(ref):
        model = ref.model if isinstance(ref, ModelRef) else str(ref)
        return FakeClient(model, scripts[model])

    return factory


async def _collect(prompt, models, **kwargs):
    return [event async for event in compare(prompt, models, **kwargs)]


class ValidationTest(unittest.IsolatedAsyncioTestCase):
    async def test_one_model_rejected(self):
        with self.assertRaises(InvalidModelCountError):
            await _collect("hi", [ModelRef("a")], client_factory=_factory({}))

    async def test_five_models_rejected(self):
        models = [ModelRef(f"m{i}") for i in range(5)]
        with self.assertRaises(InvalidModelCountError):
            await _collect("hi", models, client_factory=_factory({}))

    async def test_zero_models_rejected_with_typed_error(self):
        with self.assertRaises(CompareError):
            await _collect("hi", [], client_factory=_factory({}))

    async def test_empty_prompt_rejected(self):
        with self.assertRaises(CompareError):
            await _collect("  ", [ModelRef("a"), ModelRef("b")], client_factory=_factory({}))


class LifecycleTest(unittest.IsolatedAsyncioTestCase):
    async def test_three_models_all_started_then_finished(self):
        scripts = {m: [_tok(m, f"answer-{m}"), _done(m)] for m in ("a", "b", "c")}
        events = await _collect(
            "hello", [ModelRef(m) for m in ("a", "b", "c")],
            client_factory=_factory(scripts),
        )
        for model in ("a", "b", "c"):
            kinds = [type(e).__name__ for e in events if e.model == model]
            self.assertEqual(kinds[0], "Started", kinds)
            self.assertEqual(kinds[-1], "Finished", kinds)
            self.assertIn("Delta", kinds)
        finished = [e for e in events if isinstance(e, Finished)]
        self.assertEqual({e.model for e in finished}, {"a", "b", "c"})
        for e in finished:
            self.assertIsNotNone(e.metrics)
            self.assertEqual(e.content, f"answer-{e.model}")
            self.assertGreater(e.metrics.latency_ms, 0)

    async def test_deltas_interleave_without_corruption(self):
        scripts = {
            "a": [_tok("a", "a1"), 0.03, _tok("a", "a2"), 0.03, _tok("a", "a3"), _done("a")],
            "b": [_tok("b", "b1"), 0.03, _tok("b", "b2"), 0.03, _tok("b", "b3"), _done("b")],
        }
        events = await _collect(
            "hi", [ModelRef("a"), ModelRef("b")], client_factory=_factory(scripts)
        )
        deltas = [e for e in events if isinstance(e, Delta)]
        # Per-model concatenation is intact...
        self.assertEqual("".join(e.text for e in deltas if e.model == "a"), "a1a2a3")
        self.assertEqual("".join(e.text for e in deltas if e.model == "b"), "b1b2b3")
        # ...and the streams genuinely interleaved (not a-then-b or b-then-a).
        sequence = [e.model for e in deltas]
        self.assertNotEqual(sequence, ["a"] * 3 + ["b"] * 3)
        self.assertNotEqual(sequence, ["b"] * 3 + ["a"] * 3)
        first_b = sequence.index("b")
        last_a = len(sequence) - 1 - sequence[::-1].index("a")
        self.assertLess(first_b, last_a)


class FailureIsolationTest(unittest.IsolatedAsyncioTestCase):
    async def test_mid_stream_raise_fails_only_that_model(self):
        scripts = {
            "good1": [_tok("good1", "ok1"), _done("good1")],
            "bad": [_tok("bad", "partial"), RuntimeError("provider exploded"), _done("bad")],
            "good2": [_tok("good2", "ok2"), _done("good2")],
        }
        events = await _collect(
            "hi", [ModelRef(m) for m in ("good1", "bad", "good2")],
            client_factory=_factory(scripts),
        )
        failed = [e for e in events if isinstance(e, Failed)]
        finished = [e for e in events if isinstance(e, Finished)]
        self.assertEqual([e.model for e in failed], ["bad"])
        self.assertIn("provider exploded", failed[0].reason)
        self.assertEqual({e.model for e in finished}, {"good1", "good2"})

    async def test_stream_error_event_fails_only_that_model(self):
        scripts = {
            "down": [StreamEvent(kind="error", model="down", detail="HTTP 503: down")],
            "up": [_tok("up", "hi"), _done("up")],
        }
        events = await _collect(
            "hi", [ModelRef("down"), ModelRef("up")], client_factory=_factory(scripts)
        )
        failed = [e for e in events if isinstance(e, Failed)]
        self.assertEqual([e.model for e in failed], ["down"])
        self.assertIn("503", failed[0].reason)
        self.assertTrue(any(isinstance(e, Finished) and e.model == "up" for e in events))


class TimeoutTest(unittest.IsolatedAsyncioTestCase):
    async def test_model_that_never_emits_first_token_times_out(self):
        scripts = {
            # Sleeps far longer than the timeout before any token.
            "stuck": [5.0, _tok("stuck", "too late"), _done("stuck")],
            "fast": [_tok("fast", "quick"), _done("fast")],
        }
        started = time.perf_counter()
        events = await _collect(
            "hi", [ModelRef("stuck"), ModelRef("fast")],
            timeout_s=0.05, client_factory=_factory(scripts),
        )
        elapsed = time.perf_counter() - started
        self.assertLess(elapsed, 2.0, "timeout must not hang the gather")
        failed = [e for e in events if isinstance(e, Failed) and e.model == "stuck"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0].reason, "timeout")
        self.assertTrue(any(isinstance(e, Finished) and e.model == "fast" for e in events))
        self.assertEqual(active_task_count(), 0)


class CancelAllTest(unittest.IsolatedAsyncioTestCase):
    async def test_cancel_all_stops_in_flight_tasks(self):
        gate = asyncio.Event()  # never set: streams block forever
        scripts = {
            "a": [_tok("a", "a1"), gate, _done("a")],
            "b": [_tok("b", "b1"), gate, _done("b")],
        }
        collected = []

        async def consume():
            async for event in compare(
                "hi", [ModelRef("a"), ModelRef("b")],
                timeout_s=30, client_factory=_factory(scripts),
            ):
                collected.append(event)

        task = asyncio.create_task(consume())
        # Wait until both streams are genuinely in flight.
        for _ in range(100):
            if active_task_count() >= 2:
                break
            await asyncio.sleep(0.01)
        self.assertGreaterEqual(active_task_count(), 2)

        cancelled = cancel_all()
        self.assertGreaterEqual(cancelled, 2)
        await asyncio.wait_for(task, timeout=2.0)
        self.assertTrue(task.done())
        self.assertEqual(active_task_count(), 0)


class MetricsHonestyTest(unittest.IsolatedAsyncioTestCase):
    async def test_estimated_counts_are_marked_estimated(self):
        scripts = {
            "est": [_tok("est", "hello world"), _done("est")],
            "rep": [
                _tok("rep", "hello world"),
                SimpleNamespace(
                    kind="done", elapsed=0.1, detail="20", tokens=99,
                    usage={"prompt_tokens": 11, "completion_tokens": 22, "total_tokens": 33},
                ),
            ],
        }
        events = await _collect(
            "what is this?", [ModelRef("est"), ModelRef("rep")],
            client_factory=_factory(scripts),
        )
        by_model = {e.model: e.metrics for e in events if isinstance(e, Finished)}
        est, rep = by_model["est"], by_model["rep"]

        self.assertTrue(est.estimated)
        self.assertFalse(est.provider_reported)
        self.assertEqual(est.token_source, "estimated")
        self.assertIn("estimated", est.note.lower())
        self.assertIsNotNone(est.ttft_ms)
        self.assertGreater(est.latency_ms, 0)

        self.assertFalse(rep.estimated)
        self.assertTrue(rep.provider_reported)
        self.assertEqual(rep.token_source, "provider")
        self.assertEqual((rep.prompt_tokens, rep.completion_tokens, rep.total_tokens), (11, 22, 33))


class CredentialHygieneTest(unittest.IsolatedAsyncioTestCase):
    async def test_no_api_key_appears_in_any_event(self):
        scripts = {
            # The failure path echoes the credential back in its message.
            "leaky": [RuntimeError(f"auth failed for key {FAKE_KEY}")],
            "also_leaky": [
                StreamEvent(kind="error", model="also_leaky",
                            detail=f"Bearer {FAKE_KEY} rejected"),
            ],
            "clean": [_tok("clean", "fine"), _done("clean")],
        }
        env = {
            "COMMANDCODE_API_KEY": FAKE_KEY,
            "OPENROUTER_API_KEY": FAKE_KEY,
        }
        with mock.patch.dict(os.environ, env, clear=False):
            events = await _collect(
                "hi", [ModelRef(m) for m in ("leaky", "also_leaky", "clean")],
                client_factory=_factory(scripts),
            )
        blob = repr(events) + " ".join(
            str(vars(e)) for e in events
        )
        self.assertNotIn(FAKE_KEY, blob)
        self.assertNotIn(FAKE_KEY, " ".join(e.reason for e in events if isinstance(e, Failed)))
        # The failure is still reported, just without the secret.
        self.assertEqual(
            {e.model for e in events if isinstance(e, Failed)}, {"leaky", "also_leaky"}
        )


class DefaultClientWiringTest(unittest.IsolatedAsyncioTestCase):
    async def test_no_client_factory_uses_model_client(self):
        """Production path constructs ModelClient (patched: no network)."""
        scripts = {"a": [_tok("a", "A"), _done("a")], "b": [_tok("b", "B"), _done("b")]}

        def fake_model_client(base_url, model, timeout=120.0, reasoning_effort="high"):
            return FakeClient(model, scripts[model])

        with mock.patch.object(model_compare, "ModelClient", fake_model_client):
            events = await _collect("hi", [ModelRef("a"), ModelRef("b")], timeout_s=5)
        self.assertEqual(
            {e.model for e in events if isinstance(e, Finished)}, {"a", "b"}
        )


if __name__ == "__main__":
    unittest.main()
