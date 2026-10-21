"""Multi-agent turn coordination for the TALOS council chamber.

The orchestrator listens to room bus traffic and decides when an agent should
take a turn. It then schedules that turn with a staggered typing indicator,
streams the agent's internal reasoning as ``thought_delta`` events, streams the
visible reply as ``token_delta`` events, and finally persists and dispatches
the finished message.

The agent "brains" are deterministic simulations: no external model provider
is contacted. This keeps the chamber demonstrable and fully offline while
preserving the exact event contract the front end consumes.

Pure Python. No emojis.
"""

from __future__ import annotations

import asyncio
import hashlib
import re
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence, Set

from core.models import BusEvent, EventType, Message, Role
from core.room_bus import bus as default_bus
from server.db import save_message

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

#: Room in which every agent is always invited to answer.
COUNCIL_ROOM_ID = "the-council"

#: Mention handles that trigger agent turns.
MENTION_ALIASES: Dict[str, str] = {
    "@council": "all",
    "@alya": "alya",
    "@omnirush": "omnirush",
}

#: Agents known to the chamber, in the order they take their turn.
DEFAULT_AGENT_ORDER: Sequence[str] = ("alya", "omnirush")

#: Delay between the start of consecutive agent turns (staggered typing).
TURN_STAGGER_SECONDS = 0.45

#: How long a typing indicator stays on before tokens start flowing.
TYPING_LEAD_SECONDS = 0.35

#: Delay between streamed token chunks.
TOKEN_INTERVAL_SECONDS = 0.012


# ---------------------------------------------------------------------------
# Agent specifications
# ---------------------------------------------------------------------------


@dataclass
class AgentSpec:
    """Static description of a simulated chamber agent."""

    id: str
    name: str
    color: str = "#6366f1"
    model: str = "mock/simulated"
    role_description: str = ""
    stance: str = "balanced"

    def thought_for(self, prompt: str, topic: str) -> str:
        """Produce the agent's internal reasoning line."""
        digest = _digest(prompt)
        openers = {
            "alya": (
                "Reading the emotional undercurrent first, then answering directly.",
                "Weighing how this lands for the humans in the room.",
                "Checking the intent behind the wording before I respond.",
            ),
            "omnirush": (
                "Decomposing the request into concrete, executable steps.",
                "Scanning for the simplest correct implementation path.",
                "Identifying the constraints that will actually bite.",
            ),
        }
        opener = _pick(openers.get(self.id, ("Thinking it through.",)), digest)
        middle = {
            "alya": "The core of the ask is",
            "omnirush": "The core of the ask reduces to",
        }.get(self.id, "The core of the ask is")
        return f"{opener} {middle} {topic}, so the answer should stay concrete and actionable."

    def reply_for(self, prompt: str, topic: str) -> str:
        """Produce the agent's visible reply."""
        digest = _digest(prompt)
        if self.id == "alya":
            templates = (
                "On {topic} - I hear the real question underneath this. "
                "My read: keep it human, keep it simple, and do not over-engineer the parts "
                "that do not need it.",
                "Here is my take on {topic}: start with the part that makes someone smile, "
                "then make it correct. Both matter, in that order.",
                "For {topic}, I would say the risk is not the technique, it is the tone. "
                "Get the tone right and the rest becomes routine.",
            )
        elif self.id == "omnirush":
            templates = (
                "On {topic}: define the contract first, implement the narrow slice, "
                "then widen. Ship the smallest thing that is verifiably correct.",
                "Approach for {topic} - I would build it in layers: data model, then service, "
                "then interface. Each layer stays testable on its own.",
                "For {topic} I would pick the boring, proven path. One source of truth, "
                "explicit types, no hidden state, and logs you can actually read.",
            )
        else:
            templates = (
                "Noted on {topic}. Here is my read and a concrete next step.",
            )
        template = _pick(templates, digest)
        return template.format(topic=topic)


@dataclass
class _Turn:
    """Bookkeeping for one scheduled agent turn."""

    agent_id: str
    room_id: str
    prompt: str
    message_id: str
    reply_to_id: Optional[str] = None
    started_at: float = field(default_factory=time.time)
    finished_at: Optional[float] = None
    tokens_emitted: int = 0


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_TOPIC_RULES: Sequence[tuple] = (
    ("code", ("code", "implement", "function", "bug", "refactor", "python", "javascript", "api")),
    ("architecture", ("architect", "design", "system", "structure", "scalab", "pattern")),
    ("planning", ("plan", "roadmap", "milestone", "timeline", "schedule", "next step")),
    ("debugging", ("error", "fail", "crash", "exception", "traceback", "broken", "debug")),
    ("creative", ("idea", "concept", "name", "brand", "story", "write", "draft")),
    ("automation", ("automat", "pipeline", "cron", "schedule", "workflow", "batch")),
    ("research", ("research", "compare", "evaluate", "find", "investigate", "options")),
)


def _digest(text: str) -> str:
    """Stable hex digest used to make simulated replies deterministic."""
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()


def _pick(options: Sequence[str], digest: str) -> str:
    """Deterministically choose one option from a sequence."""
    if not options:
        return ""
    index = int(digest[:8], 16) % len(options)
    return options[index]


def _topic_of(prompt: str) -> str:
    """Classify a prompt into a coarse topic label."""
    lowered = (prompt or "").lower()
    for label, keywords in _TOPIC_RULES:
        if any(keyword in lowered for keyword in keywords):
            return label
    return "the request"


def _chunks(text: str, size: int = 6) -> List[str]:
    """Split text into small streaming chunks on word boundaries."""
    words = (text or "").split(" ")
    chunks: List[str] = []
    buffer: List[str] = []
    for word in words:
        buffer.append(word)
        if len(buffer) >= size:
            chunks.append(" ".join(buffer) + " ")
            buffer = []
    if buffer:
        chunks.append(" ".join(buffer))
    return chunks or [""]


def _mentioned_agents(content: str) -> List[str]:
    """Extract agent ids explicitly mentioned in the message body."""
    hits: List[str] = []
    lowered = (content or "").lower()
    for alias, target in MENTION_ALIASES.items():
        if alias not in lowered:
            continue
        if target == "all":
            for agent_id in DEFAULT_AGENT_ORDER:
                if agent_id not in hits:
                    hits.append(agent_id)
        elif target not in hits:
            hits.append(target)
    return hits


# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------


class AgentOrchestrator:
    """Coordinates multi-agent turns inside the council chamber."""

    def __init__(
        self,
        event_bus: Any = None,
        agents: Optional[Sequence[AgentSpec]] = None,
        turn_stagger: float = TURN_STAGGER_SECONDS,
        typing_lead: float = TYPING_LEAD_SECONDS,
        token_interval: float = TOKEN_INTERVAL_SECONDS,
        persist: bool = True,
    ) -> None:
        self.bus = event_bus if event_bus is not None else default_bus
        self.agents: Dict[str, AgentSpec] = {a.id: a for a in (agents or default_agents())}
        self.agent_order: List[str] = [
            a.id for a in (agents or default_agents()) if a.id in self.agents
        ]
        self.turn_stagger = turn_stagger
        self.typing_lead = typing_lead
        self.token_interval = token_interval
        self.persist = persist

        self._registered_rooms: Set[str] = set()
        self._seen_messages: Set[str] = set()
        self._active_turns: Dict[str, _Turn] = {}
        self._tasks: Set[asyncio.Task] = set()
        self._counter = 0

    # -- registration ------------------------------------------------------

    def attach(self, room_ids: Optional[Sequence[str]] = None) -> None:
        """Register the orchestrator as an agent listener for the given rooms.

        When no room ids are supplied, the orchestrator registers globally so
        that mentions such as "@alya" are honoured in any room, not only in the
        council chamber.
        """
        if room_ids is None:
            if "__global__" in self._registered_rooms:
                return
            self.bus.register_global_listener(self._on_bus_message)
            self._registered_rooms.add("__global__")
            return
        for room_id in room_ids:
            if room_id in self._registered_rooms:
                continue
            self.bus.register_agent_listener(room_id, self._on_bus_message)
            self._registered_rooms.add(room_id)

    async def start(self, room_ids: Optional[Sequence[str]] = None) -> None:
        """Async-friendly wrapper around :meth:`attach`."""
        self.attach(room_ids)

    async def stop(self) -> None:
        """Cancel any in-flight turns and stop scheduling new ones."""
        for task in list(self._tasks):
            task.cancel()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()
        self._active_turns.clear()

    # -- intake ------------------------------------------------------------

    async def _on_bus_message(self, message: Message, room_id: str) -> None:
        """Bus listener entry point (signature matches room bus contract)."""
        await self.observe(message, room_id)

    async def observe(self, message: Message, room_id: Optional[str] = None) -> None:
        """Public intake path. Safe to call alongside bus registration.

        Messages are de-duplicated by id so a message delivered both through the
        bus listener and through an explicit call only triggers one round of
        turns.
        """
        room_id = room_id or message.room_id
        if message.id in self._seen_messages:
            return
        self._seen_messages.add(message.id)

        if len(self._seen_messages) > 2048:
            # Keep the de-duplication window bounded.
            for old_id in list(self._seen_messages)[:1024]:
                self._seen_messages.discard(old_id)

        targets = self._resolve_targets(message, room_id)
        if not targets:
            return

        prompt = self._build_prompt(message)
        self._schedule_turns(targets, room_id, prompt, reply_to_id=message.id)

    def _resolve_targets(self, message: Message, room_id: str) -> List[str]:
        """Decide which agents should answer this message."""
        # Never let agents answer each other and loop forever.
        if message.sender_role in (Role.agent, Role.orchestrator, Role.system):
            return []
        if message.sender_id in self.agents:
            return []

        mentioned = _mentioned_agents(message.content)

        targets: List[str] = []
        if mentioned:
            # Explicit mentions win, in the canonical agent order.
            for agent_id in self.agent_order:
                if agent_id in mentioned and agent_id not in targets:
                    targets.append(agent_id)
        elif room_id == COUNCIL_ROOM_ID:
            # Unaddressed messages in the council chamber go to the whole bench.
            for agent_id in self.agent_order:
                if agent_id not in targets:
                    targets.append(agent_id)

        return [t for t in targets if t in self.agents]

    def _build_prompt(self, message: Message) -> str:
        """Build the prompt text handed to the simulated agents."""
        parts: List[str] = []
        content = (message.content or "").strip()
        if content:
            parts.append(content)
        if message.thought:
            parts.append(f"(sender note: {message.thought})")
        return "\n".join(parts) if parts else "(empty message)"

    # -- turn scheduling ---------------------------------------------------

    def _schedule_turns(
        self,
        targets: Sequence[str],
        room_id: str,
        prompt: str,
        reply_to_id: Optional[str] = None,
    ) -> None:
        """Schedule staggered agent turns for the resolved targets."""
        for index, agent_id in enumerate(targets):
            agent = self.agents.get(agent_id)
            if agent is None:
                continue
            self._counter += 1
            message_id = f"turn-{int(time.time() * 1000)}-{self._counter}"
            turn = _Turn(
                agent_id=agent_id,
                room_id=room_id,
                prompt=prompt,
                message_id=message_id,
                reply_to_id=reply_to_id,
            )
            self._active_turns[message_id] = turn
            delay = index * self.turn_stagger
            task = asyncio.create_task(self._run_turn(agent, turn, delay))
            self._tasks.add(task)
            task.add_done_callback(self._tasks.discard)

    async def _run_turn(self, agent: AgentSpec, turn: _Turn, delay: float) -> None:
        """Execute one agent turn: typing, thought stream, token stream, send."""
        try:
            if delay > 0:
                await asyncio.sleep(delay)

            await self._emit_typing(turn, True)

            topic = _topic_of(turn.prompt)
            thought = agent.thought_for(turn.prompt, topic)
            reply = agent.reply_for(turn.prompt, topic)

            # Reasoning first, so the UI can render the thought panel.
            for chunk in _chunks(thought, size=5):
                await self.bus.stream_thought(turn.room_id, turn.message_id, chunk)
                await asyncio.sleep(self.token_interval)

            await asyncio.sleep(self.typing_lead)

            for chunk in _chunks(reply, size=6):
                await self.bus.stream_token(turn.room_id, turn.message_id, chunk)
                turn.tokens_emitted += 1
                await asyncio.sleep(self.token_interval)

            message = Message(
                id=turn.message_id,
                room_id=turn.room_id,
                sender_id=agent.id,
                sender_name=agent.name,
                sender_role=Role.agent,
                content=reply,
                thought=thought,
                reply_to_id=turn.reply_to_id,
                created_at=time.time(),
            )

            await self._emit_typing(turn, False)

            if self.persist:
                save_message(message)
            await self.bus.dispatch_message(message)

            turn.finished_at = time.time()
        except asyncio.CancelledError:
            await self._emit_typing(turn, False)
            raise
        except Exception as exc:  # pragma: no cover - defensive
            import logging

            logging.getLogger("agent_orchestrator").error(
                "Agent turn failed for %s in %s: %s", turn.agent_id, turn.room_id, exc
            )
            await self._emit_typing(turn, False)
        finally:
            self._active_turns.pop(turn.message_id, None)

    async def _emit_typing(self, turn: _Turn, is_typing: bool) -> None:
        """Broadcast a typing indicator for the agent holding this turn."""
        agent = self.agents.get(turn.agent_id)
        await self.bus.broadcast(
            BusEvent(
                event_type=EventType.typing_indicator,
                room_id=turn.room_id,
                payload={
                    "client_id": turn.agent_id,
                    "agent_id": turn.agent_id,
                    "agent_name": agent.name if agent else turn.agent_id,
                    "color": agent.color if agent else "#6366f1",
                    "is_typing": is_typing,
                    "message_id": turn.message_id,
                },
            )
        )

    async def emit_reaction(self, agent_id: str, room_id: str, detail: str = "") -> None:
        """Publish an agent reaction event for the chamber UI."""
        agent = self.agents.get(agent_id)
        await self.bus.broadcast(
            BusEvent(
                event_type=EventType.agent_reaction,
                room_id=room_id,
                payload={
                    "agent_id": agent_id,
                    "agent_name": agent.name if agent else agent_id,
                    "detail": detail,
                },
            )
        )

    # -- introspection -----------------------------------------------------

    def active_turns(self) -> List[Dict[str, Any]]:
        """Return a snapshot of currently scheduled or running turns."""
        return [
            {
                "message_id": turn.message_id,
                "agent_id": turn.agent_id,
                "room_id": turn.room_id,
                "tokens_emitted": turn.tokens_emitted,
                "started_at": turn.started_at,
            }
            for turn in self._active_turns.values()
        ]

    def status(self) -> Dict[str, Any]:
        """Return a compact status payload for the REST surface."""
        return {
            "agents": [
                {
                    "id": agent.id,
                    "name": agent.name,
                    "model": agent.model,
                    "color": agent.color,
                }
                for agent in self.agents.values()
            ],
            "rooms": sorted(self._registered_rooms),
            "active_turns": len(self._active_turns),
        }


# ---------------------------------------------------------------------------
# Default agent set
# ---------------------------------------------------------------------------


def default_agents() -> List[AgentSpec]:
    """Return the built-in simulated agent roster."""
    return [
        AgentSpec(
            id="alya",
            name="Alya",
            color="#ec4899",
            model="agy/gemini-3.8-flash-high",
            role_description="Emotional anchor, close confidante, and wish-giver.",
            stance="empathetic",
        ),
        AgentSpec(
            id="omnirush",
            name="OmniRush",
            color="#3b82f6",
            model="gpt-6-sol/astra",
            role_description="Autonomous coding powerhouse and systems architect.",
            stance="pragmatic",
        ),
    ]


#: Process-wide orchestrator instance used by the FastAPI server.
orchestrator = AgentOrchestrator()
