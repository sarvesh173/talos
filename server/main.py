"""Main FastAPI Application & WebSocket Gateway for TALOS.
"""

from contextlib import asynccontextmanager
import json
import logging
import os
from pathlib import Path
import time
from typing import Any, Dict, List, Optional
import uuid

from fastapi import FastAPI, File, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from core.brand import BRAND
from core.models import BusEvent, EventType, Message, Role, Room, RoomMode
from core.room_bus import bus
from server.db import (
    get_room,
    get_room_messages,
    init_db,
    list_agents,
    list_rooms,
    save_message,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("talos_server")

_UPLOADS_DIR = Path(__file__).resolve().parent / "uploads"
_UPLOADS_DIR.mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Initializing SQLite database...")
    init_db()
    logger.info("TALOS Core Server ready.")
    yield
    # Shutdown
    logger.info("Shutting down TALOS Core Server.")


app = FastAPI(
    title=BRAND.get("name", "TALOS"),
    description=BRAND.get("tagline", "AI-Native Multi-Agent Hub"),
    version=BRAND.get("version", "0.1.0"),
    lifespan=lifespan,
)

# Enable CORS for local network and Tailscale mesh
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount media uploads directory
app.mount("/uploads", StaticFiles(directory=str(_UPLOADS_DIR)), name="uploads")


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "app": BRAND.get("name", "TALOS"),
        "version": BRAND.get("version", "0.1.0"),
        "timestamp": time.time(),
    }


@app.get("/api/brand")
async def get_brand():
    return BRAND


@app.get("/api/rooms", response_model=List[Room])
async def get_rooms():
    return list_rooms()


@app.get("/api/rooms/{room_id}", response_model=Room)
async def get_room_by_id(room_id: str):
    room = get_room(room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


@app.get("/api/rooms/{room_id}/messages", response_model=List[Message])
async def get_messages(room_id: str, limit: int = 100):
    return get_room_messages(room_id, limit=limit)


@app.get("/api/agents")
async def get_agents():
    return list_agents()


@app.post("/api/upload")
async def upload_media(file: UploadFile = File(...)):
    ext = Path(file.filename or "").suffix.lower() or ".bin"
    unique_filename = f"{uuid.uuid4().hex[:12]}{ext}"
    dest_path = _UPLOADS_DIR / unique_filename

    contents = await file.read()
    with open(dest_path, "wb") as f:
        f.write(contents)

    return {
        "url": f"/uploads/{unique_filename}",
        "filename": unique_filename,
        "size": len(contents),
    }


class SendMessageRequest(BaseModel):
    room_id: str
    sender_id: str
    sender_name: str
    sender_role: Role = Role.human
    content: str
    thought: Optional[str] = None
    attachments: List[str] = []
    reply_to_id: Optional[str] = None


@app.post("/api/messages", response_model=Message)
async def post_message(req: SendMessageRequest):
    msg = Message(
        room_id=req.room_id,
        sender_id=req.sender_id,
        sender_name=req.sender_name,
        sender_role=req.sender_role,
        content=req.content,
        thought=req.thought,
        attachments=req.attachments,
        reply_to_id=req.reply_to_id,
        created_at=time.time(),
    )
    save_message(msg)
    await bus.dispatch_message(msg)
    return msg


@app.websocket("/ws/{room_id}/{client_id}")
async def websocket_endpoint(websocket: WebSocket, room_id: str, client_id: str):
    await websocket.accept()
    await bus.subscribe(room_id, websocket)

    # Send room sync notification
    await websocket.send_text(
        BusEvent(
            event_type=EventType.room_sync,
            room_id=room_id,
            payload={"client_id": client_id, "status": "connected"},
        ).model_dump_json()
    )

    try:
        while True:
            data_text = await websocket.receive_text()
            try:
                data = json.loads(data_text)
                # Handle client inbound message
                if data.get("type") == "message":
                    msg = Message(
                        room_id=room_id,
                        sender_id=client_id,
                        sender_name=data.get("sender_name", "User"),
                        sender_role=Role.human,
                        content=data.get("content", ""),
                        attachments=data.get("attachments", []),
                        reply_to_id=data.get("reply_to_id"),
                        created_at=time.time(),
                    )
                    save_message(msg)
                    await bus.dispatch_message(msg)
                elif data.get("type") == "typing":
                    await bus.broadcast(
                        BusEvent(
                            event_type=EventType.typing_indicator,
                            room_id=room_id,
                            payload={"client_id": client_id, "is_typing": data.get("is_typing", True)},
                        )
                    )
            except json.JSONDecodeError:
                logger.warning("Invalid JSON received from %s", client_id)
    except WebSocketDisconnect:
        await bus.unsubscribe(room_id, websocket)
    except Exception as e:
        logger.error("WebSocket error for %s in room %s: %s", client_id, room_id, e)
        await bus.unsubscribe(room_id, websocket)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server.main:app", host="0.0.0.0", port=8000, reload=True)
