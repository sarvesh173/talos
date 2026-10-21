"""Tests for the multi-agent turn orchestrator."""

import asyncio
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.models import EventType, Message, Role
from core.room_bus import RoomEventBus
from server.agent_orchestrator import AgentOrchestrator, AgentSpec
from server.db import init_db


class RecordingSocket:
    """Minimal WebSocket stand-in that records broadcast events."""

    def __init__(self, room_id):
        self.room_id = room_id
        self.events = []

    async def send_text(self, payload):
        self.events.append(payload)


class TestAgentOrchestrator(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        init_db()

    async def test_council_message_triggers_both_agents(self):
        bus = RoomEventBus()
        orch = AgentOrchestrator(event_bus=bus, turn_stagger=0.01, token_interval=0.0)
        orch.attach()

        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)

        msg = Message(
            room_id="the-council",
            sender_id="sarvesh",
            sender_name="Sarvesh",
            sender_role=Role.human,
            content="How should we design the multi-agent turn scheduler?",
        )
        await bus.dispatch_message(msg)
        await asyncio.sleep(1.5)

        senders = []
        for payload in socket.events:
            senders.append(payload)
        self.assertTrue(any('"token_delta"' in p for p in senders), "no token_delta emitted")
        self.assertTrue(any('"thought_delta"' in p for p in senders), "no thought_delta emitted")
        self.assertTrue(any('"typing_indicator"' in p for p in senders), "no typing indicator")

        final = [p for p in senders if '"message_new"' in p and '"sender_role":"agent"' in p]
        self.assertEqual(len(final), 2, "expected one reply per agent")
        self.assertTrue(any("Alya" in p for p in final))
        self.assertTrue(any("OmniRush" in p for p in final))

    async def test_mention_selects_single_agent(self):
        bus = RoomEventBus()
        orch = AgentOrchestrator(event_bus=bus, turn_stagger=0.01, token_interval=0.0)
        orch.attach()

        socket = RecordingSocket("alya-dm")
        await bus.subscribe("alya-dm", socket)

        msg = Message(
            room_id="alya-dm",
            sender_id="sarvesh",
            sender_name="Sarvesh",
            sender_role=Role.human,
            content="@alya what is the plan?",
        )
        await bus.dispatch_message(msg)
        await asyncio.sleep(1.0)

        final = [p for p in socket.events if '"message_new"' in p and '"sender_role":"agent"' in p]
        self.assertEqual(len(final), 1)
        self.assertIn("Alya", final[0])
        self.assertNotIn("OmniRush", final[0])

    async def test_agent_messages_do_not_trigger_loops(self):
        bus = RoomEventBus()
        orch = AgentOrchestrator(event_bus=bus, turn_stagger=0.01, token_interval=0.0)
        orch.attach()

        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)

        msg = Message(
            room_id="the-council",
            sender_id="alya",
            sender_name="Alya",
            sender_role=Role.agent,
            content="Some earlier agent reply.",
        )
        await bus.dispatch_message(msg)
        await asyncio.sleep(0.6)

        # The original agent message is echoed back, but no NEW turn is spawned.
        final = [p for p in socket.events if '"message_new"' in p and '"turn-' in p]
        self.assertEqual(len(final), 0)

    async def test_turns_are_staggered(self):
        bus = RoomEventBus()
        orch = AgentOrchestrator(event_bus=bus, turn_stagger=0.2, token_interval=0.0)
        orch.attach()

        socket = RecordingSocket("the-council")
        await bus.subscribe("the-council", socket)

        msg = Message(
            room_id="the-council",
            sender_id="sarvesh",
            sender_name="Sarvesh",
            sender_role=Role.human,
            content="@council architect the chamber",
        )
        await bus.dispatch_message(msg)
        await asyncio.sleep(2.0)

        # Typing indicators should start for Alya first, then OmniRush later.
        starts = []
        for payload in socket.events:
            if '"typing_indicator"' in payload and '"is_typing":true' in payload.replace(" ", ""):
                starts.append(payload)
        self.assertEqual(len(starts), 2)
        self.assertIn("alya", starts[0])
        self.assertIn("omnirush", starts[1])

    async def test_status_payload(self):
        bus = RoomEventBus()
        orch = AgentOrchestrator(event_bus=bus)
        orch.attach()
        status = orch.status()
        self.assertEqual(len(status["agents"]), 2)
        self.assertIn("__global__", status["rooms"])


if __name__ == "__main__":
    unittest.main()
