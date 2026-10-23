"""Asynchronous Realtime Event Bus for Multi-Agent Rooms.
Coordinates event fan-out across WebSockets, human interfaces, and AI agent workers.
"""

import asyncio
from typing import Any, Callable, Coroutine, Dict, List, Optional, Set
import logging
from core.models import BusEvent, EventType, Message, ToolCall

logger = logging.getLogger("room_bus")


class RoomEventBus:
    """Manages pub/sub channels and room event streams."""

    def __init__(self):
        # room_id -> Set of active WebSocket connections
        self._subscribers: Dict[str, Set[Any]] = {}
        # room_id -> List of registered agent listener callbacks
        # callback signature: async def listener(msg: Message, room_id: str)
        self._agent_listeners: Dict[str, List[Callable[[Message, str], Coroutine[Any, Any, None]]]] = {}
        # Global listeners receive every message dispatched on any room.
        self._global_listeners: List[Callable[[Message, str], Coroutine[Any, Any, None]]] = []
        # room_id -> List of registered binary listeners for audio/voice payloads
        # callback signature: async def listener(payload: bytes, room_id: str)
        self._binary_listeners: Dict[str, List[Callable[[bytes, str], Coroutine[Any, Any, None]]]] = {}
        # Global binary listeners receive binary frames across all rooms
        self._global_binary_listeners: List[Callable[[bytes, str], Coroutine[Any, Any, None]]] = []
        self._lock = asyncio.Lock()

    async def subscribe(self, room_id: str, ws: Any) -> None:
        """Register a WebSocket client to a room stream."""
        async with self._lock:
            if room_id not in self._subscribers:
                self._subscribers[room_id] = set()
            self._subscribers[room_id].add(ws)
            logger.info("Client subscribed to room %s (total: %d)", room_id, len(self._subscribers[room_id]))

    async def unsubscribe(self, room_id: str, ws: Any) -> None:
        """Deregister a WebSocket client from a room stream."""
        async with self._lock:
            if room_id in self._subscribers:
                self._subscribers[room_id].discard(ws)
                if not self._subscribers[room_id]:
                    del self._subscribers[room_id]
            logger.info("Client unsubscribed from room %s", room_id)

    def register_agent_listener(
        self,
        room_id: str,
        callback: Callable[[Message, str], Coroutine[Any, Any, None]]
    ) -> None:
        """Register an autonomous agent's async listener for inbound chatter in a room."""
        if room_id not in self._agent_listeners:
            self._agent_listeners[room_id] = []
        self._agent_listeners[room_id].append(callback)

    def register_global_listener(
        self,
        callback: Callable[[Message, str], Coroutine[Any, Any, None]]
    ) -> None:
        """Register a listener that observes messages from every room."""
        self._global_listeners.append(callback)

    def register_binary_listener(
        self,
        room_id: str,
        callback: Callable[[bytes, str], Coroutine[Any, Any, None]]
    ) -> None:
        """Register a listener for inbound binary frames (e.g. streaming audio/voice memos) in a room."""
        if room_id not in self._binary_listeners:
            self._binary_listeners[room_id] = []
        self._binary_listeners[room_id].append(callback)

    def register_global_binary_listener(
        self,
        callback: Callable[[bytes, str], Coroutine[Any, Any, None]]
    ) -> None:
        """Register a global listener for binary frames across all rooms."""
        self._global_binary_listeners.append(callback)

    async def broadcast(self, event: BusEvent) -> None:
        """Fan out a BusEvent to all connected WebSockets in the target room."""
        room_id = event.room_id
        targets = set()
        async with self._lock:
            if room_id in self._subscribers:
                targets = set(self._subscribers[room_id])

        if not targets:
            return

        payload_json = event.model_dump_json()
        dead_sockets = set()

        for ws in targets:
            try:
                await ws.send_text(payload_json)
            except Exception as e:
                logger.warning("Failed to send event to socket in room %s: %s", room_id, e)
                dead_sockets.add(ws)

        if dead_sockets:
            async with self._lock:
                if room_id in self._subscribers:
                    for ws in dead_sockets:
                        self._subscribers[room_id].discard(ws)
                    if not self._subscribers[room_id]:
                        del self._subscribers[room_id]

    async def broadcast_bytes(self, room_id: str, data: bytes, exclude: Optional[Any] = None) -> None:
        """Fan out raw binary bytes (e.g. streaming audio/voice memos) to connected WebSockets in target room."""
        targets = set()
        async with self._lock:
            if room_id in self._subscribers:
                targets = set(self._subscribers[room_id])

        if not targets:
            return

        dead_sockets = set()
        for ws in targets:
            if exclude is not None and ws == exclude:
                continue
            try:
                if hasattr(ws, "send_bytes"):
                    await ws.send_bytes(data)
                elif hasattr(ws, "send"):
                    await ws.send(data)
            except Exception as e:
                logger.warning("Failed to send binary frame to socket in room %s: %s", room_id, e)
                dead_sockets.add(ws)

        if dead_sockets:
            async with self._lock:
                if room_id in self._subscribers:
                    for ws in dead_sockets:
                        self._subscribers[room_id].discard(ws)
                    if not self._subscribers[room_id]:
                        del self._subscribers[room_id]

    async def broadcast_binary(self, room_id: str, data: bytes, exclude: Optional[Any] = None) -> None:
        """Alias for broadcast_bytes."""
        await self.broadcast_bytes(room_id, data, exclude=exclude)

    async def handle_binary_frame(self, room_id: str, data: bytes, sender: Optional[Any] = None) -> None:
        """Handle incoming binary WebSocket frame (streaming voice memo payloads, audio chunks).

        Fans out the payload to other subscribers in the room (excluding sender),
        and dispatches to registered binary listeners.
        """
        await self.broadcast_bytes(room_id, data, exclude=sender)

        listeners = list(self._binary_listeners.get(room_id, []))
        listeners.extend(self._global_binary_listeners)
        for listener in listeners:
            asyncio.create_task(self._safe_invoke_binary_listener(listener, data, room_id))

    async def dispatch_binary(self, room_id: str, data: bytes, sender: Optional[Any] = None) -> None:
        """Alias for handle_binary_frame."""
        await self.handle_binary_frame(room_id, data, sender=sender)

    async def stream_voice_memo_chunk(self, room_id: str, chunk: bytes, sender: Optional[Any] = None) -> None:
        """Stream a voice memo audio payload chunk across the room bus."""
        await self.handle_binary_frame(room_id, chunk, sender=sender)

    async def _safe_invoke_binary_listener(
        self,
        listener: Callable[[bytes, str], Coroutine[Any, Any, None]],
        data: bytes,
        room_id: str,
    ) -> None:
        try:
            await listener(data, room_id)
        except Exception as e:
            logger.error("Binary listener exception in room %s: %s", room_id, e, exc_info=True)

    async def dispatch_message(self, message: Message) -> None:
        """Broadcast a new message and trigger agent listeners in the room."""
        event = BusEvent(
            event_type=EventType.message_new,
            room_id=message.room_id,
            payload=message.model_dump()
        )
        await self.broadcast(event)

        # Trigger registered agent listeners asynchronously
        listeners = list(self._agent_listeners.get(message.room_id, []))
        listeners.extend(self._global_listeners)
        for listener in listeners:
            asyncio.create_task(self._safe_invoke_listener(listener, message, message.room_id))

    async def _safe_invoke_listener(
        self,
        listener: Callable[[Message, str], Coroutine[Any, Any, None]],
        message: Message,
        room_id: str
    ) -> None:
        try:
            await listener(message, room_id)
        except Exception as e:
            logger.error("Agent listener exception in room %s: %s", room_id, e, exc_info=True)

    async def stream_token(
        self,
        room_id: str,
        message_id: str,
        delta: str,
        agent_id: Optional[str] = None,
        agent_name: Optional[str] = None,
        color: Optional[str] = None,
    ) -> None:
        """Stream a partial text token delta to UI clients."""
        payload: Dict[str, Any] = {"message_id": message_id, "delta": delta}
        if agent_id is not None:
            payload["agent_id"] = agent_id
        if agent_name is not None:
            payload["agent_name"] = agent_name
        if color is not None:
            payload["color"] = color
        event = BusEvent(
            event_type=EventType.token_delta,
            room_id=room_id,
            payload=payload
        )
        await self.broadcast(event)

    async def stream_thought(
        self,
        room_id: str,
        message_id: str,
        delta: str,
        agent_id: Optional[str] = None,
        agent_name: Optional[str] = None,
        color: Optional[str] = None,
    ) -> None:
        """Stream an internal thought/reasoning token delta to UI clients."""
        payload: Dict[str, Any] = {"message_id": message_id, "delta": delta}
        if agent_id is not None:
            payload["agent_id"] = agent_id
        if agent_name is not None:
            payload["agent_name"] = agent_name
        if color is not None:
            payload["color"] = color
        event = BusEvent(
            event_type=EventType.thought_delta,
            room_id=room_id,
            payload=payload
        )
        await self.broadcast(event)

    async def stream_tool_call(self, room_id: str, message_id: str, tool_call: ToolCall) -> None:
        """Publish tool execution start/update/finish state."""
        event = BusEvent(
            event_type=EventType.tool_call_start if tool_call.status == "running" else EventType.tool_call_end,
            room_id=room_id,
            payload={"message_id": message_id, "tool_call": tool_call.model_dump()}
        )
        await self.broadcast(event)


# Global singleton instance for the process
bus = RoomEventBus()
