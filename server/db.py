"""Lightweight SQLite persistence for rooms, messages, and agent profiles.
"""

import json
from pathlib import Path
import sqlite3
import time
from typing import List, Optional
from core.models import AgentProfile, Message, Role, Room, RoomMode, ToolCall

_DATA_DIR = Path(__file__).resolve().parent / "data"
_DB_PATH = _DATA_DIR / "talos.db"


def _get_connection() -> sqlite3.Connection:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(_DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    """Initialize schema if not present."""
    conn = _get_connection()
    cur = conn.cursor()

    cur.execute("""
    CREATE TABLE IF NOT EXISTS rooms (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT DEFAULT '',
        mode TEXT NOT NULL,
        participants_json TEXT DEFAULT '[]',
        created_at REAL,
        updated_at REAL
    );
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS messages (
        id TEXT PRIMARY KEY,
        room_id TEXT NOT NULL,
        sender_id TEXT NOT NULL,
        sender_name TEXT NOT NULL,
        sender_role TEXT NOT NULL,
        content TEXT NOT NULL,
        thought TEXT,
        tool_calls_json TEXT DEFAULT '[]',
        attachments_json TEXT DEFAULT '[]',
        reply_to_id TEXT,
        created_at REAL
    );
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS agents (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        avatar TEXT,
        color TEXT DEFAULT '#6366f1',
        model TEXT,
        role_description TEXT,
        system_prompt TEXT,
        is_active INTEGER DEFAULT 1
    );
    """)

    conn.commit()

    # Seed default profiles and rooms if table is empty
    cur.execute("SELECT COUNT(*) as cnt FROM rooms")
    if cur.fetchone()["cnt"] == 0:
        _seed_defaults(conn)

    conn.close()


def _seed_defaults(conn: sqlite3.Connection) -> None:
    cur = conn.cursor()
    now = time.time()

    # Default Agents: Core & OmniRush
    core_agent = AgentProfile(
        id="core",
        name="TALOS Core",
        color="#38bdf8",
        model="hermes-agent",
        role_description="Core reasoning orchestrator and primary interface.",
        is_active=True
    )
    omnirush = AgentProfile(
        id="omnirush",
        name="OmniRush",
        color="#a855f7",
        model="omnirush-cli",
        role_description="Autonomous coding powerhouse & systems architect.",
        is_active=True
    )

    for agent in [core_agent, omnirush]:
        cur.execute("""
        INSERT OR REPLACE INTO agents (id, name, avatar, color, model, role_description, system_prompt, is_active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            agent.id, agent.name, agent.avatar, agent.color,
            agent.model, agent.role_description, agent.system_prompt, 1 if agent.is_active else 0
        ))

    # Room 1: Direct link (Core DM)
    cur.execute("""
    INSERT INTO rooms (id, name, description, mode, participants_json, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (
        "core-dm",
        "Direct Link",
        "Direct point-to-point chamber with core reasoning engine.",
        RoomMode.dm.value,
        json.dumps([core_agent.model_dump()]),
        now,
        now
    ))

    # Room 2: The Council (Multi-agent room)
    cur.execute("""
    INSERT INTO rooms (id, name, description, mode, participants_json, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (
        "the-council",
        "The Council",
        "Multi-agent collaborative chamber. Core + OmniRush + Specialist Agents.",
        RoomMode.group.value,
        json.dumps([core_agent.model_dump(), omnirush.model_dump()]),
        now,
        now
    ))

    conn.commit()


def list_rooms() -> List[Room]:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM rooms ORDER BY updated_at DESC")
    rows = cur.fetchall()
    rooms = []
    for r in rows:
        participants = [AgentProfile(**p) for p in json.loads(r["participants_json"] or "[]")]
        rooms.append(Room(
            id=r["id"],
            name=r["name"],
            description=r["description"],
            mode=RoomMode(r["mode"]),
            participants=participants,
            created_at=r["created_at"],
            updated_at=r["updated_at"]
        ))
    conn.close()
    return rooms


def get_room(room_id: str) -> Optional[Room]:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM rooms WHERE id = ?", (room_id,))
    r = cur.fetchone()
    conn.close()
    if not r:
        return None
    participants = [AgentProfile(**p) for p in json.loads(r["participants_json"] or "[]")]
    return Room(
        id=r["id"],
        name=r["name"],
        description=r["description"],
        mode=RoomMode(r["mode"]),
        participants=participants,
        created_at=r["created_at"],
        updated_at=r["updated_at"]
    )


def save_message(msg: Message) -> None:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("""
    INSERT INTO messages (
        id, room_id, sender_id, sender_name, sender_role,
        content, thought, tool_calls_json, attachments_json,
        reply_to_id, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        msg.id,
        msg.room_id,
        msg.sender_id,
        msg.sender_name,
        msg.sender_role.value if isinstance(msg.sender_role, Role) else str(msg.sender_role),
        msg.content,
        msg.thought,
        json.dumps([t.model_dump() for t in msg.tool_calls]),
        json.dumps(msg.attachments),
        msg.reply_to_id,
        msg.created_at
    ))
    # Update room updated_at
    cur.execute("UPDATE rooms SET updated_at = ? WHERE id = ?", (msg.created_at, msg.room_id))
    conn.commit()
    conn.close()


def get_room_messages(room_id: str, limit: int = 100) -> List[Message]:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("""
    SELECT * FROM (
        SELECT * FROM messages WHERE room_id = ? ORDER BY created_at DESC LIMIT ?
    ) ORDER BY created_at ASC
    """, (room_id, limit))
    rows = cur.fetchall()
    messages = []
    for r in rows:
        tool_calls = [ToolCall(**t) for t in json.loads(r["tool_calls_json"] or "[]")]
        attachments = json.loads(r["attachments_json"] or "[]")
        messages.append(Message(
            id=r["id"],
            room_id=r["room_id"],
            sender_id=r["sender_id"],
            sender_name=r["sender_name"],
            sender_role=Role(r["sender_role"]),
            content=r["content"],
            thought=r["thought"],
            tool_calls=tool_calls,
            attachments=attachments,
            reply_to_id=r["reply_to_id"],
            created_at=r["created_at"]
        ))
    conn.close()
    return messages


def list_agents() -> List[AgentProfile]:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM agents WHERE is_active = 1")
    rows = cur.fetchall()
    agents = []
    for r in rows:
        agents.append(AgentProfile(
            id=r["id"],
            name=r["name"],
            avatar=r["avatar"],
            color=r["color"],
            model=r["model"],
            role_description=r["role_description"],
            system_prompt=r["system_prompt"],
            is_active=bool(r["is_active"])
        ))
    conn.close()
    return agents
