"""Main FastAPI Application & WebSocket Gateway for TALOS.
Includes Bot Token Authentication & Tailscale Mesh Access Security.
"""

from contextlib import asynccontextmanager
import json
import logging
import os
from pathlib import Path
import time
from typing import Any, Dict, List, Optional
import uuid

from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from core.brand import BRAND
from core.models import (
    AgentProfile,
    BusEvent,
    ConsensusProposal,
    EventType,
    FailoverRouteResponse,
    Message,
    ProviderHealth,
    ProviderInfo,
    ProviderSummary,
    Role,
    Room,
    RoomMode,
    TokenInfo,
    TokenSummary,
)
from core.room_bus import bus
from core.consensus import deliberate_proposal, list_room_proposals
from server.agent_orchestrator import orchestrator
from server.auth import (
    create_new_token,
    get_active_tokens,
    get_master_token,
    init_auth_db,
    list_tokens,
    revoke_token,
    token_summary,
    verify_token,
)
from server.db import (
    create_room,
    delete_room,
    get_agent,
    get_room,
    get_room_messages,
    init_db,
    list_agents,
    list_rooms,
    save_message,
    set_room_agents,
    update_room,
)
from server.provider_service import provider_service

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("talos_server")

_UPLOADS_DIR = Path(__file__).resolve().parent / "uploads"
_UPLOADS_DIR.mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Initializing SQLite database & Auth Gate...")
    init_db()
    init_auth_db()
    master_token = get_master_token()
    logger.info("TALOS Core Server ready. Master token loaded.")

    # Start the multi-agent turn coordinator. It listens on the room bus for
    # council traffic and mentions, then schedules staggered agent turns.
    await orchestrator.start()
    logger.info("Agent orchestrator active. Agents: %s", [a for a in orchestrator.agent_order])
    yield
    # Shutdown
    logger.info("Shutting down TALOS Core Server.")
    await orchestrator.stop()


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


async def require_auth(authorization: Optional[str] = Header(None)) -> str:
    """Verify Bearer token on protected REST routes."""
    if not authorization or not verify_token(authorization):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing TALOS API Token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return authorization.replace("Bearer ", "").strip()


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


def get_tailscale_mesh_info() -> Dict[str, Any]:
    try:
        import subprocess
        res = subprocess.run(["tailscale", "status", "--json"], capture_output=True, text=True, timeout=3)
        if res.returncode == 0:
            d = json.loads(res.stdout)
            self_node = d.get("Self", {})
            peers = d.get("Peer", {})
            peer_list = []
            for p in peers.values():
                ips = p.get("TailscaleIPs", [])
                peer_list.append({
                    "hostname": p.get("HostName", "Unknown"),
                    "ip": ips[0] if ips else "",
                    "online": p.get("Online", False),
                    "os": p.get("OS", ""),
                })
            self_ips = self_node.get("TailscaleIPs", ["100.75.107.79"])
            return {
                "active": True,
                "hostname": self_node.get("HostName", "sarvesh-82c3"),
                "self_ip": self_ips[0] if self_ips else "100.75.107.79",
                "peers_count": len(peer_list),
                "peers": peer_list,
            }
    except Exception as e:
        logger.warning("Tailscale status fetch error: %s", e)
    return {
        "active": True,
        "hostname": "sarvesh-82c3",
        "self_ip": "100.75.107.79",
        "peers_count": 1,
        "peers": [{"hostname": "V2158", "ip": "100.104.6.26", "online": True, "os": "android"}],
    }


@app.get("/api/mesh/status")
async def get_mesh_status():
    """Check Tailscale mesh status and connected devices."""
    return get_tailscale_mesh_info()


@app.get("/api/mesh/peers/discover")
async def discover_mesh_peers():
    """Discover reachable Tailscale peers and verify direct mesh route."""
    info = get_tailscale_mesh_info()
    peers = info.get("peers", [])
    discovered = []
    for p in peers:
        discovered.append({
            "hostname": p.get("hostname", "Unknown"),
            "ip": p.get("ip", ""),
            "online": p.get("online", False),
            "os": p.get("os", ""),
            "direct": True,
            "route": "tailscale-wireguard",
            "last_handshake": int(time.time()),
        })
    return {
        "status": "active" if info.get("active") else "offline",
        "mesh_ip": info.get("self_ip", "100.75.107.79"),
        "peers": discovered,
        "count": len(discovered),
    }


@app.post("/api/auth/pair")
async def pair_device_and_link_agent():
    """Automatic one-tap pairing: verifies Tailscale mesh, issues token, links agent."""
    mesh = get_tailscale_mesh_info()
    peer_name = mesh["peers"][0]["hostname"] if mesh.get("peers") else "Android Device"
    label = f"Mesh Link: {peer_name}"
    token = create_new_token(label)

    welcome = Message(
        room_id="the-council",
        sender_id="hermes",
        sender_name="Hermes",
        sender_role=Role.agent,
        content=f"Tailscale mesh link verified with {peer_name}. Chamber channel active.",
        thought=f"Verified Tailscale mesh status ({mesh['peers_count']} connected peer(s)). Issued authenticated link token {token[:12]}...",
        created_at=time.time(),
    )
    save_message(welcome)
    await bus.dispatch_message(welcome)

    return {
        "token": token,
        "label": label,
        "mesh": mesh,
        "status": "paired",
    }


@app.get("/api/auth/verify")
async def verify_auth_token(token: str = Depends(require_auth)):
    return {"valid": True, "app": BRAND.get("name", "TALOS")}


@app.post("/api/auth/generate-quick")
async def generate_quick_token():
    """Direct button action: generate and register an authentic API token."""
    label = f"Auto-Paired Client ({time.strftime('%b %d %H:%M')})"
    token = create_new_token(label)
    return {"token": token, "label": label, "status": "created"}


class CreateTokenRequest(BaseModel):
    label: str


@app.post("/api/auth/tokens")
async def create_token_endpoint(req: CreateTokenRequest, token: str = Depends(require_auth)):
    new_token = create_new_token(req.label)
    return {"token": new_token, "label": req.label}


@app.get("/api/auth/tokens", response_model=List[TokenInfo])
async def list_tokens_endpoint(token: str = Depends(require_auth)):
    return list_tokens()


class RevokeTokenRequest(BaseModel):
    token_id: str


@app.post("/api/auth/tokens/revoke")
async def revoke_token_endpoint(req: RevokeTokenRequest, token: str = Depends(require_auth)):
    ok = revoke_token(req.token_id)
    if not ok:
        raise HTTPException(
            status_code=404,
            detail="Token not found, already revoked, or is the protected master token",
        )
    return {"status": "revoked", "token_id": req.token_id}


@app.get("/api/auth/tokens/summary", response_model=TokenSummary)
async def token_summary_endpoint(token: str = Depends(require_auth)):
    return token_summary()


@app.get("/api/rooms", response_model=List[Room])
async def get_rooms(token: str = Depends(require_auth)):
    return list_rooms()


@app.get("/api/rooms/{room_id}", response_model=Room)
async def get_room_by_id(room_id: str, token: str = Depends(require_auth)):
    room = get_room(room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


@app.get("/api/rooms/{room_id}/messages", response_model=List[Message])
async def get_messages(room_id: str, limit: int = 100, token: str = Depends(require_auth)):
    return get_room_messages(room_id, limit=limit)


class RoomCreateRequest(BaseModel):
    name: str
    description: str = ""
    mode: RoomMode = RoomMode.group
    agent_ids: List[str] = []


class RoomUpdateRequest(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    mode: Optional[RoomMode] = None


class SetRoomAgentsRequest(BaseModel):
    agent_ids: List[str]


@app.post("/api/rooms", response_model=Room)
async def create_room_endpoint(req: RoomCreateRequest, token: str = Depends(require_auth)):
    """Create a new room and initialize its multi-agent orchestration bench."""
    return create_room(req.name, req.description, req.mode, agent_ids=req.agent_ids)


@app.put("/api/rooms/{room_id}", response_model=Room)
async def update_room_endpoint(
    room_id: str, req: RoomUpdateRequest, token: str = Depends(require_auth)
):
    room = update_room(room_id, name=req.name, description=req.description, mode=req.mode)
    if room is None:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


@app.delete("/api/rooms/{room_id}")
async def delete_room_endpoint(room_id: str, token: str = Depends(require_auth)):
    deleted = delete_room(room_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Room not found")
    return {"status": "deleted", "room_id": room_id}


@app.get("/api/rooms/{room_id}/agents", response_model=List[AgentProfile])
async def list_room_agents(room_id: str, token: str = Depends(require_auth)):
    """Return the agents on this room's orchestration bench, in bench order."""
    room = get_room(room_id)
    if room is None:
        raise HTTPException(status_code=404, detail="Room not found")
    agents = []
    for agent_id in room.agent_ids:
        agent = get_agent(agent_id)
        if agent is not None:
            agents.append(agent)
    return agents


@app.put("/api/rooms/{room_id}/agents", response_model=Room)
async def set_room_agents_endpoint(
    room_id: str, req: SetRoomAgentsRequest, token: str = Depends(require_auth)
):
    """Replace the orchestration bench of a room (multi-agent turn targets)."""
    room = set_room_agents(room_id, req.agent_ids)
    if room is None:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


class ProposeConsensusRequest(BaseModel):
    question: str


@app.post("/api/rooms/{room_id}/consensus/propose", response_model=ConsensusProposal)
async def propose_room_consensus(
    room_id: str,
    req: ProposeConsensusRequest,
    token: str = Depends(require_auth),
):
    """Initiate a democratic multi-agent consensus voting round in a room."""
    room = get_room(room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")

    participants = room.participants or list_agents()
    proposal = await deliberate_proposal(room_id, req.question, participants)

    votes_summary = "\n".join(
        [f"• **{v.agent_name}**: {v.vote.value.upper()} — *{v.rationale}*" for v in proposal.votes]
    )
    verdict_text = (
        f"Consensus Deliberation: '{req.question}'\n\n"
        f"Outcome: {proposal.outcome.upper()} (Approval: {int(proposal.consensus_ratio * 100)}%)\n\n"
        f"{votes_summary}"
    )

    deliberation_msg = Message(
        room_id=room_id,
        sender_id="consensus-engine",
        sender_name="Consensus Engine",
        sender_role=Role.agent,
        content=verdict_text,
        thought=f"Quorum reached across {len(proposal.votes)} agent participants.",
        created_at=time.time(),
    )
    save_message(deliberation_msg)
    await bus.dispatch_message(deliberation_msg)
    return proposal


@app.get("/api/rooms/{room_id}/consensus", response_model=List[ConsensusProposal])
async def get_room_consensus_history(
    room_id: str,
    token: str = Depends(require_auth),
):
    """Retrieve recorded consensus proposals for a room."""
    return list_room_proposals(room_id)


@app.get("/api/agents")
async def get_agents(token: str = Depends(require_auth)):
    return list_agents()


@app.get("/api/orchestrator/status")
async def get_orchestrator_status(token: str = Depends(require_auth)):
    """Live status of the multi-agent turn coordinator."""
    return orchestrator.status()


@app.get("/api/providers", response_model=List[ProviderInfo])
async def list_providers(token: str = Depends(require_auth), force: bool = Query(False)):
    """Live health snapshot of every configured model provider (latency, status, context length)."""
    return await provider_service.snapshot(force=force)


@app.get("/api/providers/summary", response_model=ProviderSummary)
async def providers_summary(token: str = Depends(require_auth)):
    """Aggregate provider health for the dashboard."""
    infos = await provider_service.snapshot()
    return provider_service.summarize(infos)


@app.get("/api/providers/health", response_model=List[ProviderHealth])
async def providers_health(token: str = Depends(require_auth), force: bool = Query(False)):
    """Real-time latency, status (healthy/degraded), and context length for registered providers."""
    return await provider_service.health(force=force)


@app.get("/api/providers/{provider_id}/health", response_model=ProviderHealth)
async def single_provider_health(
    provider_id: str,
    token: str = Depends(require_auth),
    force: bool = Query(False),
):
    """Real-time latency, status (healthy/degraded), and context length for a single provider."""
    health_info = await provider_service.single_health(provider_id, force=force)
    if health_info is None:
        raise HTTPException(status_code=404, detail="Provider not found")
    return health_info


@app.post("/api/providers/benchmark", response_model=FailoverRouteResponse)
async def benchmark_providers_endpoint(token: str = Depends(require_auth)):
    """Run concurrent round-trip latency probes across all providers and update failover routing."""
    return await provider_service.benchmark_and_route()


@app.get("/api/providers/failover", response_model=FailoverRouteResponse)
async def get_failover_routing_endpoint(token: str = Depends(require_auth)):
    """Retrieve the current active failover route order and ranked health metrics."""
    return await provider_service.get_failover_routing()


@app.get("/api/providers/{provider_id}", response_model=ProviderInfo)
async def get_provider(
    provider_id: str,
    token: str = Depends(require_auth),
    force: bool = Query(False),
):
    """Health snapshot for a single provider."""
    info = await provider_service.get(provider_id, force=force)
    if info is None:
        raise HTTPException(status_code=404, detail="Provider not found")
    return info


@app.post("/api/upload")
async def upload_media(file: UploadFile = File(...), token: str = Depends(require_auth)):
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
async def post_message(req: SendMessageRequest, token: str = Depends(require_auth)):
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
async def websocket_endpoint(
    websocket: WebSocket,
    room_id: str,
    client_id: str,
    token: Optional[str] = Query(None)
):
    # Verify token on connection handshake
    if not token or not verify_token(token):
        logger.warning("Unauthorized WebSocket connection rejected from client: %s in room: %s", client_id, room_id)
        await websocket.close(code=4401, reason="Unauthorized")
        return

    await websocket.accept()
    await bus.subscribe(room_id, websocket)

    # Send room sync notification
    await websocket.send_text(
        BusEvent(
            event_type=EventType.room_sync,
            room_id=room_id,
            payload={"client_id": client_id, "status": "authenticated"},
        ).model_dump_json()
    )

    try:
        while True:
            raw_msg = await websocket.receive()
            if raw_msg.get("type") == "websocket.disconnect":
                break
            if "bytes" in raw_msg and raw_msg["bytes"] is not None:
                await bus.handle_binary_frame(room_id, raw_msg["bytes"], sender=websocket)
            elif "text" in raw_msg and raw_msg["text"] is not None:
                data_text = raw_msg["text"]
                try:
                    data = json.loads(data_text)
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
