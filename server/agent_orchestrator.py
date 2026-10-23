"""Multi-agent turn coordination for the TALOS council chamber.

The orchestrator listens to room bus traffic and decides when an agent should
take a turn. Turns are organised into *rounds*: one round is created for a
single human message, and the agents of that round take their turns strictly
one after another in the canonical bench order (Hermes first, then OmniRush).

Within a round every agent receives the conversation so far, so the second
speaker can genuinely build on the first one instead of answering in a
vacuum. Each turn emits a clean, attributable event stream:

``turn_start``      -> a new agent turn begins
``typing_indicator``-> the agent is composing
``thought_delta``   -> internal reasoning, chunked on word boundaries
``token_delta``     -> the visible reply, chunked on word boundaries
``message_new``     -> the finished, persisted message
``turn_end``        -> the turn is complete

All streaming chunks concatenate back to the exact final text, so the front
end can render deltas without whitespace artefacts or duplicated words.

The agent "brains" are deterministic simulations: no external model provider
is contacted. This keeps the chamber demonstrable and fully offline while
preserving the exact event contract the front end consumes.

Pure Python. No emojis.
"""

from __future__ import annotations

import asyncio
import hashlib
import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set

from core.models import BusEvent, EventType, Message, Role
from core.room_bus import bus as default_bus
from server.db import save_message

logger = logging.getLogger("agent_orchestrator")

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

#: Room in which every agent is always invited to answer.
COUNCIL_ROOM_ID = "the-council"

#: Mention handles that trigger agent turns.
MENTION_ALIASES: Dict[str, str] = {
    "@council": "all",
    "@hermes": "hermes",
    "@core": "hermes",
    "@claude": "claude",
    "@codex": "codex",
    "@gemini": "gemini",
    "@meta": "meta",
    "@llama": "meta",
    "@mistral": "mistral",
    "@deepseek": "deepseek",
    "@qwen": "qwen",
    "@openclaw": "openclaw",
    "@omnirush": "omnirush",
}

#: Agents known to the chamber, in the order they take their turn.
DEFAULT_AGENT_ORDER: Sequence[str] = (
    "hermes",
    "claude",
    "codex",
    "gemini",
    "deepseek",
    "qwen",
    "mistral",
    "meta",
    "openclaw",
    "omnirush",
)

#: Pause between the end of one agent turn and the start of the next one.
TURN_STAGGER_SECONDS = 0.45

#: How long a typing indicator stays on before tokens start flowing.
TYPING_LEAD_SECONDS = 0.35

#: Delay between streamed token chunks.
TOKEN_INTERVAL_SECONDS = 0.012

#: How many prior room messages are handed to an agent as context.
CONTEXT_WINDOW = 8

#: Hard cap on the number of agents that may answer a single message.
MAX_TURNS_PER_ROUND = 4


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
    #: How the agent introduces a reply that builds on an earlier speaker.
    bridging_phrase: str = "Building on the point above:"

    # -- context aware generation -------------------------------------------

    def thought_for(
        self,
        prompt: str,
        topic: str,
        context: Optional[Sequence[Dict[str, str]]] = None,
    ) -> str:
        """Produce the agent's internal reasoning line.

        ``context`` is the ordered list of replies already made in the current
        round, as ``{"agent_id", "agent_name", "content"}`` dictionaries.
        """
        context = list(context or [])
        digest = _digest(prompt + "|" + "|".join(c.get("content", "") for c in context))
        openers = {
            "hermes": (
                "Analyzing core intent and operational constraints.",
                "Reviewing technical parameters before taking an action.",
                "Formulating concise, verifiable plan.",
            ),
            "claude": (
                "Evaluating the problem structure and dependencies.",
                "Tracing implications across architectural boundaries.",
                "Mapping constraints to a coherent design approach.",
            ),
            "codex": (
                "Scanning the implementation surface for concrete actions.",
                "Breaking this into minimal, testable code changes.",
                "Identifying the fastest path to a working solution.",
            ),
            "gemini": (
                "Synthesizing multimodal context and broader implications.",
                "Considering alternative perspectives and tradeoffs.",
                "Framing this against larger strategic considerations.",
            ),
            "meta": (
                "Assessing with efficient, pragmatic reasoning.",
                "Optimizing for clarity and resource efficiency.",
                "Balancing performance with maintainability.",
            ),
            "mistral": (
                "Cutting to the essential problem with precision.",
                "Evaluating with disciplined, focused analysis.",
                "Distilling complexity to its core elements.",
            ),
            "deepseek": (
                "Engaging in deep chain-of-thought analysis.",
                "Tracing logical dependencies step by step.",
                "Exploring edge cases and verification criteria.",
            ),
            "qwen": (
                "Considering multilingual and cross-cultural contexts.",
                "Bridging technical requirements with practical constraints.",
                "Synthesizing broad knowledge for targeted solutions.",
            ),
            "openclaw": (
                "Mapping automation opportunities and tooling leverage.",
                "Identifying workflows to streamline and codify.",
                "Designing for composability and developer velocity.",
            ),
            "omnirush": (
                "Decomposing the request into concrete, executable steps.",
                "Scanning for the simplest correct implementation path.",
                "Identifying the constraints that will actually bite.",
            ),
        }
        opener = _pick(openers.get(self.id, ("Thinking it through.",)), digest)
        middle = {
            "hermes": "The core of the ask is",
            "claude": "At the architectural level, the ask centers on",
            "codex": "For implementation, this reduces to",
            "gemini": "Looking holistically, this comes down to",
            "meta": "Practically speaking, this boils down to",
            "mistral": "Stripping away noise, this is fundamentally about",
            "deepseek": "After careful analysis, this reduces to",
            "qwen": "Considering all factors, the key issue is",
            "openclaw": "From an automation perspective, this is about",
            "omnirush": "The core of the ask reduces to",
        }.get(self.id, "The core of the ask is")

        if context:
            previous = context[-1]
            bridge = (
                f" {previous.get('agent_name', 'The previous speaker')} already framed the direction, "
                f"so I will add the execution blueprint."
            )
        else:
            bridge = ""
        return f"{opener} {middle} {topic}, so the answer should stay concrete and actionable.{bridge}"

    def reply_for(
        self,
        prompt: str,
        topic: str,
        context: Optional[Sequence[Dict[str, str]]] = None,
    ) -> str:
        """Produce the agent's visible reply."""
        context = list(context or [])
        digest = _digest(prompt + "|" + "|".join(c.get("content", "") for c in context))
        if self.id == "hermes":
            templates = (
                "On {topic}: define clean boundaries and verify inputs. "
                "Keep the architecture simple, testable, and direct.",
                "Here is the approach for {topic}: start with the verified core, "
                "then iterate without unnecessary ceremony.",
                "For {topic}, prioritize clear interfaces and robust error handling.",
            )
        elif self.id == "claude":
            templates = (
                "On {topic}, I would start with a system design pass: identify components, "
                "interfaces, and invariants before writing implementation details.",
                "For {topic}, the cleanest path is separation of concerns with explicit contracts. "
                "Let's define the boundaries first, then fill in the pieces.",
                "Considering {topic}, I favor a methodical approach: model the data flow, "
                "consider failure modes, and build in verification at each layer.",
            )
        elif self.id == "codex":
            templates = (
                "For {topic}, I'll scaffold the minimal working implementation first. "
                "Let me lay out the core functions and wire them together.",
                "On {topic}: write the happy path, add types, then harden edge cases. "
                "Small, reviewable commits each step of the way.",
                "Quick plan for {topic} - start with tests/structure, implement iteratively, "
                "and validate against concrete examples.",
            )
        elif self.id == "gemini":
            templates = (
                "From a broader lens on {topic}, there are multiple viable approaches. "
                "I'll weigh tradeoffs across scalability, clarity, and long-term flexibility.",
                "On {topic}: consider both immediate needs and future extensibility. "
                "The optimal path balances pragmatism with adaptability.",
                "Looking at {topic} holistically, we should think beyond the immediate fix "
                "to enable composable, reusable solutions.",
            )
        elif self.id == "meta":
            templates = (
                "For {topic}, the efficient approach is to leverage proven patterns. "
                "Keep it simple, avoid over-engineering, and optimize for maintainability.",
                "On {topic}: use standard abstractions, minimize dependencies, "
                "and focus on what actually needs to be custom.",
                "Pragmatic take on {topic} - build lean, measure, iterate. "
                "Efficiency comes from choosing the right tools, not more tools.",
            )
        elif self.id == "mistral":
            templates = (
                "On {topic}, precision is key. Define the exact requirements, "
                "eliminate ambiguity, and execute with minimal surface area.",
                "For {topic}, I advocate for the tightest viable solution. "
                "Every line should earn its place.",
                "Regarding {topic}: focus ruthlessly on the core problem. "
                "Clarity through constraint beats complexity through flexibility.",
            )
        elif self.id == "deepseek":
            templates = (
                "On {topic}, let us reason step by step. First, establish premises, "
                "then derive consequences, and finally verify against edge cases.",
                "For {topic}, I see several critical considerations. Let me trace the "
                "logical dependencies and potential failure modes.",
                "Regarding {topic}: a thorough analysis reveals we need to handle "
                "both the obvious cases and subtle interactions between components.",
            )
        elif self.id == "qwen":
            templates = (
                "On {topic}, I bring a broad perspective - considering multilingual "
                "contexts, cultural nuances, and universal design principles.",
                "For {topic}, the solution should be accessible and adaptable across "
                "different use cases and environments.",
                "Regarding {topic}: by synthesizing insights from diverse domains, "
                "we can build something more robust and widely applicable.",
            )
        elif self.id == "openclaw":
            templates = (
                "For {topic}, this is a perfect automation candidate. "
                "Let me design a composable workflow that eliminates manual steps.",
                "On {topic}: identify repetitive patterns, codify them into tools, "
                "and chain them for maximum developer velocity.",
                "Regarding {topic}, I would build reusable scripts/commands that "
                "future-proof this process and scale across similar tasks.",
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
        body = template.format(topic=topic)

        if context:
            previous = context[-1]
            body = (
                f"{body} {self.bridging_phrase} {previous.get('agent_name', 'the previous speaker')} "
                f"has the instinct right, so here is the implementation path: "
                f"one owner per concern, explicit boundaries between the parts, "
                f"and a test that proves each step before the next one lands."
            )
        return body


@dataclass
class _Turn:
    """Bookkeeping for one scheduled agent turn."""

    agent_id: str
    room_id: str
    prompt: str
    message_id: str
    round_id: str
    reply_to_id: Optional[str] = None
    index: int = 0
    order: int = 0
    started_at: float = field(default_factory=time.time)
    finished_at: Optional[float] = None
    tokens_emitted: int = 0
    thoughts_emitted: int = 0


@dataclass
class _Round:
    """Bookkeeping for one multi-agent round (one human message)."""

    round_id: str
    room_id: str
    prompt: str
    agents: List[str]
    reply_to_id: Optional[str] = None
    created_at: float = field(default_factory=time.time)
    finished_at: Optional[float] = None


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

_WORD_RE = re.compile(r"\S+\s*")


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
    """Split text into streaming chunks on word boundaries.

    The chunks concatenate back to ``text`` byte for byte, including the
    original whitespace, so a client that appends every delta ends up with
    exactly the final message body.
    """
    if not text:
        return []
    pieces: List[str] = []
    buffer: List[str] = []
    for match in _WORD_RE.finditer(text):
        buffer.append(match.group(0))
        if len(buffer) >= size:
            pieces.append("".join(buffer))
            buffer = []
    if buffer:
        pieces.append("".join(buffer))
    return pieces


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


def _summarise(text: str, limit: int = 140) -> str:
    """Short one-line summary of a reply, used for context windows."""
    flat = " ".join((text or "").split())
    if len(flat) <= limit:
        return flat
    return flat[: limit - 1].rstrip() + "..."


# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------


class AgentOrchestrator:
    """Coordinates multi-agent turns inside the council chamber.

    The orchestrator owns three concerns:

    * **intake** - decide which agents answer a human message,
    * **sequencing** - run those agents one at a time, in bench order, with a
      room level lock so two rounds never interleave their event streams,
    * **streaming** - emit attributable, gap-free thought and token deltas
      followed by the persisted final message.
    """

    def __init__(
        self,
        event_bus: Any = None,
        agents: Optional[Sequence[AgentSpec]] = None,
        turn_stagger: float = TURN_STAGGER_SECONDS,
        typing_lead: float = TYPING_LEAD_SECONDS,
        token_interval: float = TOKEN_INTERVAL_SECONDS,
        persist: bool = True,
        context_window: int = CONTEXT_WINDOW,
        max_turns_per_round: int = MAX_TURNS_PER_ROUND,
    ) -> None:
        self.bus = event_bus if event_bus is not None else default_bus
        roster = list(agents or default_agents())
        self.agents: Dict[str, AgentSpec] = {a.id: a for a in roster}
        self.agent_order: List[str] = [a.id for a in roster if a.id in self.agents]
        self.turn_stagger = turn_stagger
        self.typing_lead = typing_lead
        self.token_interval = token_interval
        self.persist = persist
        self.context_window = context_window
        self.max_turns_per_round = max_turns_per_round

        self._registered_rooms: Set[str] = set()
        self._seen_messages: Set[str] = set()
        self._active_turns: Dict[str, _Turn] = {}
        self._rounds: Dict[str, _Round] = {}
        self._tasks: Set[asyncio.Task] = set()
        self._room_locks: Dict[str, asyncio.Lock] = {}
        self._history: Dict[str, List[Dict[str, str]]] = {}
        self._counter = 0

    # -- registration ------------------------------------------------------

    def attach(self, room_ids: Optional[Sequence[str]] = None) -> None:
        """Register the orchestrator as an agent listener for the given rooms.

        When no room ids are supplied, the orchestrator registers globally so
        that mentions such as "@hermes" are honoured in any room, not only in the
        council chamber. The council chamber is always part of the bench, so it
        is registered explicitly as well.
        """
        if room_ids is None:
            if "__global__" not in self._registered_rooms:
                self.bus.register_global_listener(self._on_bus_message)
                self._registered_rooms.add("__global__")
            self.attach([COUNCIL_ROOM_ID])
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
        self._rounds.clear()

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
        self._record_history(message, room_id)
        self._schedule_turns(targets, room_id, prompt, reply_to_id=message.id)

    def _record_history(self, message: Message, room_id: str) -> None:
        """Keep a bounded window of recent room messages for prompt context."""
        history = self._history.setdefault(room_id, [])
        history.append(
            {
                "agent_id": message.sender_id,
                "agent_name": message.sender_name,
                "content": _summarise(message.content),
            }
        )
        if len(history) > self.context_window * 2:
            del history[: len(history) - self.context_window * 2]

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

        targets = [t for t in targets if t in self.agents]
        return targets[: self.max_turns_per_round]

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

    def _room_lock(self, room_id: str) -> asyncio.Lock:
        """Return (and lazily create) the per-room sequencing lock."""
        lock = self._room_locks.get(room_id)
        if lock is None:
            lock = asyncio.Lock()
            self._room_locks[room_id] = lock
        return lock

    def _schedule_turns(
        self,
        targets: Sequence[str],
        room_id: str,
        prompt: str,
        reply_to_id: Optional[str] = None,
    ) -> None:
        """Schedule a round of agent turns for the resolved targets.

        The whole round runs inside a single task so that the agents take
        their turns strictly in sequence, each one seeing the replies of the
        agents that already spoke.
        """
        agents = [t for t in targets if t in self.agents]
        if not agents:
            return

        self._counter += 1
        round_id = f"round-{int(time.time() * 1000)}-{self._counter}"
        round_ = _Round(
            round_id=round_id,
            room_id=room_id,
            prompt=prompt,
            agents=agents,
            reply_to_id=reply_to_id,
        )
        self._rounds[round_id] = round_

        task = asyncio.create_task(self._run_round(round_))
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    async def _run_round(self, round_: _Round) -> None:
        """Run every agent of the round, one turn at a time."""
        try:
            lock = self._room_lock(round_.room_id)
            async with lock:
                context: List[Dict[str, str]] = []
                for index, agent_id in enumerate(round_.agents):
                    agent = self.agents.get(agent_id)
                    if agent is None:
                        continue
                    reply = await self._run_turn(agent, round_, index, context)
                    if reply is None:
                        continue
                    context.append(
                        {
                            "agent_id": agent.id,
                            "agent_name": agent.name,
                            "content": reply,
                        }
                    )
                    if index < len(round_.agents) - 1 and self.turn_stagger > 0:
                        await asyncio.sleep(self.turn_stagger)
            round_.finished_at = time.time()
        except asyncio.CancelledError:
            round_.finished_at = time.time()
            raise
        except Exception as exc:  # pragma: no cover - defensive
            logger.error("Round %s in %s failed: %s", round_.round_id, round_.room_id, exc)
            round_.finished_at = time.time()
        finally:
            self._rounds.pop(round_.round_id, None)

    async def _run_turn(
        self,
        agent: AgentSpec,
        round_: _Round,
        index: int,
        context: List[Dict[str, str]],
    ) -> Optional[str]:
        """Execute one agent turn: typing, thought stream, token stream, send.

        Returns the final reply text so the next agent in the round can build
        on it, or ``None`` when the turn failed.
        """
        self._counter += 1
        message_id = f"turn-{int(time.time() * 1000)}-{self._counter}"
        turn = _Turn(
            agent_id=agent.id,
            room_id=round_.room_id,
            prompt=round_.prompt,
            message_id=message_id,
            round_id=round_.round_id,
            reply_to_id=round_.reply_to_id,
            index=index,
            order=index + 1,
        )
        self._active_turns[message_id] = turn

        try:
            await self._emit_turn_event(turn, EventType.turn_start)
            await self._emit_typing(turn, True)

            topic = _topic_of(round_.prompt)
            thought = agent.thought_for(round_.prompt, topic, context)
            reply = agent.reply_for(round_.prompt, topic, context)

            # Reasoning first, so the UI can render the thought panel.
            for chunk in _chunks(thought, size=5):
                await self.bus.stream_thought(
                    round_.room_id,
                    message_id,
                    chunk,
                    agent_id=agent.id,
                    agent_name=agent.name,
                    color=agent.color,
                )
                turn.thoughts_emitted += 1
                if self.token_interval:
                    await asyncio.sleep(self.token_interval)

            if self.typing_lead:
                await asyncio.sleep(self.typing_lead)

            for chunk in _chunks(reply, size=6):
                await self.bus.stream_token(
                    round_.room_id,
                    message_id,
                    chunk,
                    agent_id=agent.id,
                    agent_name=agent.name,
                    color=agent.color,
                )
                turn.tokens_emitted += 1
                if self.token_interval:
                    await asyncio.sleep(self.token_interval)

            message = Message(
                id=message_id,
                room_id=round_.room_id,
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
                try:
                    save_message(message)
                except Exception as exc:  # pragma: no cover - defensive
                    logger.error("Failed to persist turn %s: %s", message_id, exc)
            await self.bus.dispatch_message(message)

            turn.finished_at = time.time()
            await self._emit_turn_event(turn, EventType.turn_end)
            return reply
        except asyncio.CancelledError:
            await self._emit_typing(turn, False)
            raise
        except Exception as exc:  # pragma: no cover - defensive
            logger.error("Agent turn failed for %s in %s: %s", turn.agent_id, turn.room_id, exc)
            await self._emit_typing(turn, False)
            return None
        finally:
            self._active_turns.pop(turn.message_id, None)

    # -- event emission ----------------------------------------------------

    async def _emit_turn_event(self, turn: _Turn, event_type: EventType) -> None:
        """Broadcast a turn lifecycle event (start / end)."""
        agent = self.agents.get(turn.agent_id)
        await self.bus.broadcast(
            BusEvent(
                event_type=event_type,
                room_id=turn.room_id,
                payload={
                    "message_id": turn.message_id,
                    "round_id": turn.round_id,
                    "agent_id": turn.agent_id,
                    "agent_name": agent.name if agent else turn.agent_id,
                    "color": agent.color if agent else "#6366f1",
                    "order": turn.order,
                    "index": turn.index,
                    "reply_to_id": turn.reply_to_id,
                },
            )
        )

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
                    "round_id": turn.round_id,
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
                "agent_name": self.agents[turn.agent_id].name
                if turn.agent_id in self.agents
                else turn.agent_id,
                "room_id": turn.room_id,
                "round_id": turn.round_id,
                "order": turn.order,
                "tokens_emitted": turn.tokens_emitted,
                "thoughts_emitted": turn.thoughts_emitted,
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
                    "order": index + 1,
                }
                for index, agent in enumerate(self.agents.values())
            ],
            "rooms": sorted(self._registered_rooms),
            "active_turns": len(self._active_turns),
            "active_rounds": len(self._rounds),
            "council_room": COUNCIL_ROOM_ID,
            "turn_order": list(self.agent_order),
        }

    def conversation_context(self, room_id: str, limit: Optional[int] = None) -> List[Dict[str, str]]:
        """Return a short, recent view of the room for prompt context."""
        limit = limit if limit is not None else self.context_window
        history = self._history.get(room_id, [])
        if limit <= 0:
            return []
        return history[-limit:]


# ---------------------------------------------------------------------------
# Default agent set
# ---------------------------------------------------------------------------


def default_agents() -> List[AgentSpec]:
    """Return the built-in simulated agent roster."""
    return [
        AgentSpec(
            id="hermes",
            name="Hermes",
            color="#22c55e",
            model="hermes-agent",
            role_description="Nous Research · Autonomy — autonomous CLI reasoning engine and interface.",
            stance="objective",
            bridging_phrase="Hermes framed the problem cleanly,",
        ),
        AgentSpec(
            id="claude",
            name="Claude",
            color="#6366f1",
            model="claude-code",
            role_description="Anthropic · Architecture — methodical reasoning, architecture design, and thorough review.",
            stance="analytical",
            bridging_phrase="Claude adds architectural clarity,",
        ),
        AgentSpec(
            id="codex",
            name="Codex",
            color="#10b981",
            model="codex-cli",
            role_description="OpenAI · Code & Synthesis — rapid implementation, code generation, and synthesis across languages.",
            stance="pragmatic",
            bridging_phrase="Codex moves us toward implementation,",
        ),
        AgentSpec(
            id="gemini",
            name="Gemini",
            color="#f59e0b",
            model="gemini-cli",
            role_description="Google · Multimodal Reasoning — deep reasoning, multimodal understanding, and adaptive strategy.",
            stance="holistic",
            bridging_phrase="Gemini broadens the perspective,",
        ),
        AgentSpec(
            id="deepseek",
            name="DeepSeek",
            color="#ef4444",
            model="deepseek-cli",
            role_description="DeepSeek · Reasoning — advanced chain-of-thought reasoning and technical depth.",
            stance="rigorous",
            bridging_phrase="DeepSeek deepens the analysis,",
        ),
        AgentSpec(
            id="qwen",
            name="Qwen",
            color="#3b82f6",
            model="qwen-cli",
            role_description="Alibaba · Qwen Series — multilingual reasoning, Chinese-language depth, and broad utility.",
            stance="adaptive",
            bridging_phrase="Qwen brings broader context,",
        ),
        AgentSpec(
            id="mistral",
            name="Mistral",
            color="#14b8a6",
            model="mistral-cli",
            role_description="Mistral AI · Open Weights — compact, powerful, and precise open-weight model specialist.",
            stance="precise",
            bridging_phrase="Mistral sharpens the focus,",
        ),
        AgentSpec(
            id="meta",
            name="Llama",
            color="#8b5cf6",
            model="llama-cli",
            role_description="Meta AI · Llama Family — open-weights reasoning, efficiency, and community model ecosystem.",
            stance="efficient",
            bridging_phrase="Llama keeps it lean and practical,",
        ),
        AgentSpec(
            id="openclaw",
            name="OpenClaw",
            color="#ec4899",
            model="openclaw-cli",
            role_description="Tools & Automation — tooling, automation scripting, and developer workflow acceleration.",
            stance="automation-focused",
            bridging_phrase="OpenClaw identifies automation leverage,",
        ),
        AgentSpec(
            id="omnirush",
            name="OmniRush",
            color="#a855f7",
            model="omnirush-cli",
            role_description="High-Throughput Runner — autonomous coding powerhouse and systems architect.",
            stance="pragmatic",
            bridging_phrase="OmniRush provides the execution path,",
        ),
    ]


#: Process-wide orchestrator instance used by the FastAPI server.
orchestrator = AgentOrchestrator()
