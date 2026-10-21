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
    created_at: float = Field(default_factory=time.time)
    updated_at: float = Field(default_factory=time.time)


class BusEvent(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    event_type: EventType
    room_id: str
    payload: Dict[str, Any] = Field(default_factory=dict)
    timestamp: float = Field(default_factory=time.time)
