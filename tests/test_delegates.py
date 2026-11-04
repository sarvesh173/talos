"""Delegates and Summoning lifecycle: register, list, pause, cancel, and TTL sweep."""

import asyncio
import sys
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.llm_client import StreamEvent
from server.agent_orchestrator import AgentOrchestrator


class DelegateLifecycleTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.orch = AgentOrchestrator(persist=False)

    def test_register_and_list_delegate(self):
        rec = self.orch.register_delegate(
            delegate_id="del_test_1",
            name="Code Auditor",
            task="Audit Kotlin codebase for memory leaks",
            parent_id="main",
            model="stealth/crouton-alpha",
            reasoning_effort="high",
            ttl_seconds=3600,
        )
        self.assertEqual(rec["id"], "del_test_1")
        self.assertEqual(rec["status"], "running")

        delegates = self.orch.list_delegates()
        self.assertEqual(len(delegates), 1)
        self.assertEqual(delegates[0]["id"], "del_test_1")
        self.assertEqual(delegates[0]["model"], "stealth/crouton-alpha")
        self.assertIn("elapsed_seconds", delegates[0])
        self.assertIn("remaining_seconds", delegates[0])

    def test_pause_and_resume_delegate(self):
        self.orch.register_delegate(
            delegate_id="del_test_2",
            name="Doc Writer",
            task="Draft release notes",
        )
        # Pause
        self.assertTrue(self.orch.pause_delegate("del_test_2"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "paused")

        # Resume
        self.assertTrue(self.orch.pause_delegate("del_test_2"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "running")

    def test_cancel_delegate(self):
        self.orch.register_delegate(
            delegate_id="del_test_3",
            name="Scraper",
            task="Scrape documentation",
        )
        self.assertTrue(self.orch.cancel_delegate("del_test_3"))
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "cancelled")

    def test_ttl_auto_expiration(self):
        # Register with negative or 0 TTL
        rec = self.orch.register_delegate(
            delegate_id="del_test_4",
            name="Quick Prober",
            task="Probe network interface",
            ttl_seconds=0,
        )
        time.sleep(0.01)
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["status"], "expired")
        self.assertEqual(delegates[0]["remaining_seconds"], 0)


class _FakeBus:
    """Records stream_thought/stream_token calls instead of broadcasting."""

    def __init__(self):
        self.thoughts = []
        self.tokens = []
        self.broadcasts = []

    async def stream_thought(self, room_id, message_id, delta, agent_id=None, agent_name=None, color=None):
        self.thoughts.append(
            {"room_id": room_id, "message_id": message_id, "delta": delta,
             "agent_id": agent_id, "agent_name": agent_name}
        )

    async def stream_token(self, room_id, message_id, delta, agent_id=None, agent_name=None, color=None):
        self.tokens.append(
            {"room_id": room_id, "message_id": message_id, "delta": delta,
             "agent_id": agent_id, "agent_name": agent_name}
        )

    async def broadcast(self, event):
        self.broadcasts.append(event)


class _FakeModelClient:
    """Streaming client double honouring the ModelClient.stream contract."""

    def __init__(self, script, gate=None, delay_before=0.0):
        self._script = script
        self._gate = gate
        self._delay_before = delay_before
        self.messages = None

    async def stream(self, messages, max_tokens=2048, temperature=0.7):
        self.messages = list(messages)
        if self._delay_before:
            await asyncio.sleep(self._delay_before)
        for event in self._script:
            if isinstance(event, asyncio.Event):
                await event.wait()
                continue
            yield event


class DelegateExecutionTest(unittest.IsolatedAsyncioTestCase):
    """A delegate really executes: streaming deltas, live counters, TTL."""

    def _orchestrator(self, bus, factory_calls, script, gate=None, delay_before=0.0):
        def factory(*, base_url, model, reasoning_effort):
            factory_calls.append(
                {"base_url": base_url, "model": model, "reasoning_effort": reasoning_effort}
            )
            return _FakeModelClient(script, gate=gate, delay_before=delay_before)

        return AgentOrchestrator(
            event_bus=bus,
            persist=False,
            default_model="deepseek/deepseek-v4-pro",
            delegate_client_factory=factory,
        )

    async def test_delegate_streams_and_completes(self):
        bus = _FakeBus()
        calls = []
        script = [
            StreamEvent(kind="thought", text="planning ", model="stealth/crouton-alpha"),
            StreamEvent(kind="token", text="Hello ", model="stealth/crouton-alpha"),
            StreamEvent(kind="token", text="world", model="stealth/crouton-alpha"),
            StreamEvent(kind="done", model="stealth/crouton-alpha", tokens=2, elapsed=0.1, detail="0"),
        ]
        orch = self._orchestrator(bus, calls, script)
        orch.register_delegate(
            delegate_id="del_run_1",
            name="Researcher",
            task="Summarise the auth flow",
            description="Focus on token verification.",
            parent_id="main",
            model="stealth/crouton-alpha",
            room_id="the-council",
        )
        record = await orch.wait_for_delegate("del_run_1")

        self.assertEqual(record["status"], "completed")
        self.assertEqual(record["progress"], 1.0)
        self.assertEqual(record["tokens_generated"], 2)
        self.assertEqual(record["output"], "Hello world")
        self.assertEqual(record["thought"], "planning")
        # The delegate's own model id was used verbatim.
        self.assertEqual(calls[0]["model"], "stealth/crouton-alpha")
        # Scoped prompt carries task + description.
        self.assertIn("Summarise the auth flow", str(orch._delegate_messages(record)))
        self.assertIn("Focus on token verification.", str(orch._delegate_messages(record)))
        # Deltas hit the bus tagged with the delegate id.
        self.assertEqual([t["delta"] for t in bus.thoughts], ["planning "])
        self.assertEqual([t["delta"] for t in bus.tokens], ["Hello ", "world"])
        self.assertTrue(all(t["agent_id"] == "del_run_1" for t in bus.tokens))
        self.assertTrue(all(t["room_id"] == "the-council" for t in bus.tokens))

    async def test_delegate_cli_alias_falls_back_to_default_model(self):
        bus = _FakeBus()
        calls = []
        script = [StreamEvent(kind="token", text="ok", model="deepseek/deepseek-v4-pro"),
                  StreamEvent(kind="done", model="deepseek/deepseek-v4-pro", tokens=1, elapsed=0.1, detail="0")]
        orch = self._orchestrator(bus, calls, script)
        orch.register_delegate(
            delegate_id="del_run_2",
            name="Alias User",
            task="Do the thing",
            model="claude-code",
            room_id="the-council",
        )
        record = await orch.wait_for_delegate("del_run_2")
        self.assertEqual(record["status"], "completed")
        self.assertEqual(calls[0]["model"], "deepseek/deepseek-v4-pro")

    async def test_delegate_respects_pause(self):
        bus = _FakeBus()
        calls = []
        gate = asyncio.Event()
        script = [
            StreamEvent(kind="token", text="first ", model="stealth/crouton-alpha"),
            gate,
            StreamEvent(kind="token", text="second", model="stealth/crouton-alpha"),
            StreamEvent(kind="done", model="stealth/crouton-alpha", tokens=2, elapsed=0.1, detail="0"),
        ]
        orch = self._orchestrator(bus, calls, script)
        orch.register_delegate(
            delegate_id="del_run_3",
            name="Pausable",
            task="Stream slowly",
            room_id="the-council",
        )
        # Let the first token land, then pause before releasing the gate.
        for _ in range(100):
            if bus.tokens:
                break
            await asyncio.sleep(0.01)
        self.assertEqual(len(bus.tokens), 1)
        self.assertTrue(orch.pause_delegate("del_run_3"))
        gate.set()
        await asyncio.sleep(0.15)
        # Paused: the second token must not be forwarded yet.
        self.assertEqual(len(bus.tokens), 1)
        self.assertEqual(orch.list_delegates()[0]["status"], "paused")
        # Resume and let it finish.
        self.assertTrue(orch.pause_delegate("del_run_3"))
        record = await orch.wait_for_delegate("del_run_3")
        self.assertEqual(record["status"], "completed")
        self.assertEqual(record["tokens_generated"], 2)
        self.assertEqual(len(bus.tokens), 2)

    async def test_delegate_cancel_stops_execution(self):
        bus = _FakeBus()
        calls = []
        gate = asyncio.Event()
        script = [gate, StreamEvent(kind="token", text="late", model="stealth/crouton-alpha")]
        orch = self._orchestrator(bus, calls, script)
        orch.register_delegate(
            delegate_id="del_run_4",
            name="Cancellable",
            task="Wait forever",
            room_id="the-council",
        )
        await asyncio.sleep(0.05)
        self.assertTrue(orch.cancel_delegate("del_run_4"))
        record = await orch.wait_for_delegate("del_run_4")
        self.assertEqual(record["status"], "cancelled")
        self.assertEqual(bus.tokens, [])

    async def test_delegate_expires_on_ttl(self):
        bus = _FakeBus()
        calls = []
        script = [StreamEvent(kind="token", text="never", model="stealth/crouton-alpha")]
        orch = self._orchestrator(bus, calls, script, delay_before=5.0)
        orch.register_delegate(
            delegate_id="del_run_5",
            name="Short Lived",
            task="Too slow",
            ttl_seconds=0.2,
            room_id="the-council",
        )
        record = await orch.wait_for_delegate("del_run_5")
        self.assertEqual(record["status"], "expired")


if __name__ == "__main__":
    unittest.main()
