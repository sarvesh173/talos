"""Unit tests for TALOS core event bus, models, and SQLite persistence.
"""

import asyncio
import os
import sys
from pathlib import Path
import unittest

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.brand import BRAND
from core.models import BusEvent, EventType, Message, Role, Room, RoomMode, ToolCall
from core.room_bus import RoomEventBus
from server.db import (
    get_room,
    get_room_messages,
    init_db,
    list_agents,
    list_rooms,
    save_message,
)


class TestTalosCore(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        init_db()

    def test_brand_loaded(self):
        self.assertIn("name", BRAND)
        self.assertEqual(BRAND["codename"], "talos")

    def test_default_rooms_seeded(self):
        rooms = list_rooms()
        self.assertGreaterEqual(len(rooms), 2)
        room_ids = [r.id for r in rooms]
        self.assertIn("alya-dm", room_ids)
        self.assertIn("the-council", room_ids)

    def test_agents_seeded(self):
        agents = list_agents()
        agent_ids = [a.id for a in agents]
        self.assertIn("alya", agent_ids)
        self.assertIn("omnirush", agent_ids)

    def test_message_persistence(self):
        msg = Message(
            room_id="the-council",
            sender_id="sarvesh",
            sender_name="Sarvesh",
            sender_role=Role.human,
            content="Testing the multi-agent room bus.",
            thought="Testing human thought",
            tool_calls=[
                ToolCall(tool_name="terminal", arguments={"cmd": "whoami"}, output="kira", status="success")
            ]
        )
        save_message(msg)

        history = get_room_messages("the-council", limit=10)
        saved_msg = next((m for m in history if m.id == msg.id), None)
        self.assertIsNotNone(saved_msg)
        self.assertEqual(saved_msg.content, "Testing the multi-agent room bus.")
        self.assertEqual(len(saved_msg.tool_calls), 1)
        self.assertEqual(saved_msg.tool_calls[0].tool_name, "terminal")
        self.assertEqual(saved_msg.tool_calls[0].output, "kira")

    async def test_room_bus_agent_listener(self):
        bus = RoomEventBus()
        received_messages = []

        async def agent_callback(m: Message, r_id: str):
            received_messages.append((m, r_id))

        bus.register_agent_listener("test-room", agent_callback)

        test_msg = Message(
            room_id="test-room",
            sender_id="human-1",
            sender_name="Sarvesh",
            sender_role=Role.human,
            content="Hello agents!"
        )

        await bus.dispatch_message(test_msg)
        # Give asyncio tasks a tick to finish
        await asyncio.sleep(0.05)

        self.assertEqual(len(received_messages), 1)
        self.assertEqual(received_messages[0][0].content, "Hello agents!")
        self.assertEqual(received_messages[0][1], "test-room")


if __name__ == "__main__":
    unittest.main()
