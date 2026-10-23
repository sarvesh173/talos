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
        self.assertTrue(any("dm" in r_id for r_id in room_ids))
        self.assertIn("the-council", room_ids)

    def test_agents_seeded(self):
        agents = list_agents()
        agent_ids = [a.id for a in agents]
        self.assertTrue(len(agent_ids) >= 2)
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

        history = get_room_messages("the-council", limit=100)
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

    async def test_room_bus_binary_listener(self):
        bus = RoomEventBus()
        received_payloads = []

        async def binary_callback(payload: bytes, r_id: str):
            received_payloads.append((payload, r_id))

        bus.register_binary_listener("voice-room", binary_callback)

        test_bytes = b"\x00\x01\x02\x03\x04voice_memo_test"
        await bus.handle_binary_frame("voice-room", test_bytes)
        await asyncio.sleep(0.05)

        self.assertEqual(len(received_payloads), 1)
        self.assertEqual(received_payloads[0][0], test_bytes)
        self.assertEqual(received_payloads[0][1], "voice-room")

    async def test_room_bus_global_binary_listener(self):
        bus = RoomEventBus()
        global_payloads = []

        async def global_callback(payload: bytes, r_id: str):
            global_payloads.append((payload, r_id))

        bus.register_global_binary_listener(global_callback)

        chunk1 = b"\xaa\xbb\xcc"
        chunk2 = b"\xdd\xee\xff"
        await bus.dispatch_binary("room-a", chunk1)
        await bus.stream_voice_memo_chunk("room-b", chunk2)
        await asyncio.sleep(0.05)

        self.assertEqual(len(global_payloads), 2)
        self.assertEqual(global_payloads[0], (chunk1, "room-a"))
        self.assertEqual(global_payloads[1], (chunk2, "room-b"))

    async def test_room_bus_broadcast_bytes_excluding_sender(self):
        bus = RoomEventBus()

        class MockWs:
            def __init__(self):
                self.received = []

            async def send_bytes(self, data: bytes):
                self.received.append(data)

        sender_ws = MockWs()
        receiver_ws = MockWs()

        await bus.subscribe("room-memo", sender_ws)
        await bus.subscribe("room-memo", receiver_ws)

        voice_data = b"AUDIO_STREAM_CHUNK"
        await bus.handle_binary_frame("room-memo", voice_data, sender=sender_ws)

        self.assertEqual(sender_ws.received, [])
        self.assertEqual(receiver_ws.received, [voice_data])

    async def test_room_bus_binary_dead_socket_cleanup(self):
        bus = RoomEventBus()

        class DeadWs:
            async def send_bytes(self, data: bytes):
                raise ConnectionResetError("client disconnected abruptly")

        dead_ws = DeadWs()
        await bus.subscribe("dead-room", dead_ws)
        self.assertIn("dead-room", bus._subscribers)

        await bus.broadcast_bytes("dead-room", b"test")
        self.assertNotIn("dead-room", bus._subscribers)


if __name__ == "__main__":
    unittest.main()
