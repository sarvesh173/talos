"""Domain models for the multi-agent room communication bus.
"""

from enum import Enum
import time
from typing import Any, Dict, List, Optional
import uuid
from pydantic import BaseModel, Field


class Role(str, Enum):
    human = "human"
    agent = "agent"
    system = "system"
    orchestrator = "orchestrator"


class RoomMode(str, Enum):
    dm = "dm"
    group = "group"
    moderated = "moderated"


class EventType(str, Enum):
    message_new = "message_new"
    token_delta = "token_delta"
    thought_delta = "thought_delta"
    tool_call_start = "tool_call_start"
    tool_call_end = "tool_call_end"
    typing_indicator = "typing_indicator"
    room_sync = "room_sync"
    agent_reaction = "agent_reaction"
    turn_start = "turn_start"
    turn_end = "turn_end"


class ToolCall(BaseModel):
    call_id: str = Field(default_factory=lambda: str(uuid.uuid4())[:8])
    tool_name: str
    arguments: Dict[str, Any] = Field(default_factory=dict)
    output: Optional[str] = None
    status: str = "running"  # running, success, failed
    started_at: float = Field(default_factory=time.time)
    finished_at: Optional[float] = None


class AgentProfile(BaseModel):
    id: str
    name: str
    avatar: Optional[str] = None
    color: str = "#6366f1"
    model: Optional[str] = None
    role_description: Optional[str] = None
    system_prompt: Optional[str] = None
    is_active: bool = True


class Message(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    room_id: str
    sender_id: str
    sender_name: str
    sender_role: Role = Role.human
    content: str = ""
    thought: Optional[str] = None
    tool_calls: List[ToolCall] = Field(default_factory=list)
    attachments: List[str] = Field(default_factory=list)
    reply_to_id: Optional[str] = None
    created_at: float = Field(default_factory=time.time)


class Room(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4())[:12])
    name: str
    description: str = ""
    mode: RoomMode = RoomMode.group
    participants: List[AgentProfile] = Field(default_factory=list)
    #: Ids of the agents that the orchestrator may summon in this room.
    #: Empty means "fall back to the default bench".
    agent_ids: List[str] = Field(default_factory=list)
    created_at: float = Field(default_factory=time.time)
    updated_at: float = Field(default_factory=time.time)


class BusEvent(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    event_type: EventType
    room_id: str
    payload: Dict[str, Any] = Field(default_factory=dict)
    timestamp: float = Field(default_factory=time.time)


# ---------------------------------------------------------------------------
# Provider registry models
# ---------------------------------------------------------------------------


class ProviderStatus(str, Enum):
    """Lifecycle state of a model provider as seen by the TALOS core."""

    operational = "operational"
    degraded = "degraded"
    rate_limited = "rate_limited"
    unauthorized = "unauthorized"
    unconfigured = "unconfigured"
    unreachable = "unreachable"
    unknown = "unknown"


class ProviderModel(BaseModel):
    """A single model exposed by a provider."""

    id: str
    context_length: Optional[int] = None
    #: Where the context length came from: live provider catalog, the local
    #: Hermes context-length cache, or a static built-in default.
    context_source: str = "unknown"


class ProviderInfo(BaseModel):
    """Health and capability snapshot for one provider."""

    id: str
    name: str
    vendor: str
    base_url: str
    api_style: str
    default_model: str
    docs_url: str = ""
    status: ProviderStatus = ProviderStatus.unknown
    status_detail: str = ""
    #: Round-trip time of the last health probe, in milliseconds.
    latency_ms: Optional[float] = None
    #: Context window of the provider's default model.
    context_length: Optional[int] = None
    #: Largest context window advertised by the provider (live catalog).
    max_context_length: Optional[int] = None
    #: True when a usable credential was found for this provider.
    credential_present: bool = False
    #: Where the credential came from, without ever revealing the secret.
    credential_source: Optional[str] = None
    models: List[ProviderModel] = Field(default_factory=list)
    models_count: int = 0
    checked_at: Optional[float] = None
    #: Age of the measurement in milliseconds (0 when it was just taken).
    age_ms: int = 0
    error: Optional[str] = None


class ProviderSummary(BaseModel):
    """Aggregate view across every registered provider."""

    total: int = 0
    operational: int = 0
    degraded: int = 0
    rate_limited: int = 0
    unauthorized: int = 0
    unconfigured: int = 0
    unreachable: int = 0
    unknown: int = 0
    configured: int = 0
    average_latency_ms: Optional[float] = None
    fastest_provider: Optional[str] = None
    checked_at: Optional[float] = None


# ---------------------------------------------------------------------------
# Orchestration models
# ---------------------------------------------------------------------------


class OrchestrationAgent(BaseModel):
    """One agent on a room's bench."""

    id: str
    name: str
    model: Optional[str] = None
    color: str = "#6366f1"
    role_description: Optional[str] = None
    order: int = 0


class RoomOrchestration(BaseModel):
    """Live orchestration state for a single room."""

    room_id: str
    room_name: str = ""
    mode: RoomMode = RoomMode.group
    #: Which agents the bench may summon.
    policy: str = "group"
    agents: List[OrchestrationAgent] = Field(default_factory=list)
    #: Agents that are currently mid-turn.
    active_turns: List[Dict[str, Any]] = Field(default_factory=list)
    active_rounds: int = 0
    max_turns_per_round: int = 4
    turn_stagger_seconds: float = 0.45
    mention_aliases: Dict[str, str] = Field(default_factory=dict)
    context_window: int = 8


class DispatchResult(BaseModel):
    """Outcome of a forced multi-agent round."""

    room_id: str
    round_id: str
    prompt: str
    agents: List[str] = Field(default_factory=list)
    message_id: str
    created_at: float = Field(default_factory=time.time)


# ---------------------------------------------------------------------------
# Token management models
# ---------------------------------------------------------------------------


class TokenInfo(BaseModel):
    """Metadata for an API token. The secret itself is never returned here."""

    token_id: str
    label: str
    role: str = "user"
    created_at: float = 0.0
    last_used_at: Optional[float] = None
    use_count: int = 0
    is_revoked: bool = False
    revoked_at: Optional[float] = None
    expires_at: Optional[float] = None
    note: Optional[str] = None
    #: A masked preview such as ``talos_sk_abc1...9f2e``.
    masked_token: str = ""
    is_master: bool = False


class TokenSummary(BaseModel):
    """Aggregate token usage for the auth dashboard."""

    total: int = 0
    active: int = 0
    revoked: int = 0
    expired: int = 0
    by_role: Dict[str, int] = Field(default_factory=dict)
    requests_total: int = 0
    last_used_at: Optional[float] = None
