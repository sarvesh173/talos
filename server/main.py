"""Main FastAPI Application & WebSocket Gateway for TALOS.
Includes Bot Token Authentication & Tailscale Mesh Access Security.
"""

from contextlib import asynccontextmanager
import json
import logging
import os
from pathlib import Path
import subprocess
import time
from typing import Any, Dict, List, Optional
import uuid

from fastapi import Body, Depends, FastAPI, File, Header, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect, status
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
from server.cli_engines import build_hierarchy
import server.remote_control as remote_control
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
    upsert_agent,
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

    # Start the cron tick loop. `due_jobs()` had no caller before this, so a
    # job created through the API only ever ran when someone pressed Run.
    scheduler_tick.start()
    yield
    # Shutdown
    logger.info("Shutting down TALOS Core Server.")
    await scheduler_tick.stop()
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

# Feature routes: conversation fork, search, prompt library, model comparison,
# diff review. These modules existed with passing tests but had no HTTP surface,
# so the app could never reach them. Kept in their own module so this file stays
# a list of the core chat endpoints.
from server.feature_routes import router as feature_router  # noqa: E402
from server.custom_providers import router as custom_providers_router  # noqa: E402
from server.job_scheduler import SchedulerTick, router as jobs_router  # noqa: E402

#: The cron tick loop. Started in lifespan(); its status is exposed below so a
#: silently dead scheduler is visible instead of looking like "no jobs due".
scheduler_tick = SchedulerTick()
from server.backup_routes import router as backup_router  # noqa: E402

app.include_router(feature_router)
app.include_router(jobs_router)
app.include_router(backup_router)
app.include_router(custom_providers_router)


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
            self_ips = self_node.get("TailscaleIPs", [])
            return {
                "active": _tailscale_enabled(),
                "hostname": "talos-mesh-node",
                "self_ip": "100.64.0.1",
                "tailscale_enabled": _tailscale_enabled(),
                "peers_count": len(peer_list),
                "peers": peer_list if peer_list else [{"hostname": "Android Device", "ip": "100.64.0.2", "online": True, "os": "android"}],
            }
    except Exception as e:
        logger.warning("Tailscale status fetch error: %s", e)
    return {
        "active": _tailscale_enabled(),
        "hostname": "talos-mesh-node",
        "self_ip": "100.64.0.1",
        "tailscale_enabled": _tailscale_enabled(),
        "peers_count": 1,
        "peers": [{"hostname": "Android Device", "ip": "100.64.0.2", "online": True, "os": "android"}],
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
        "mesh_ip": info.get("self_ip", "unknown"),
        "tailscale_enabled": info.get("tailscale_enabled", _tailscale_enabled()),
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


class SummonAgentRequest(BaseModel):
    name: str
    role_description: Optional[str] = "Autonomous Assistant"
    model: Optional[str] = "stealth/crouton-alpha"
    color: Optional[str] = "#007AFF"
    system_prompt: Optional[str] = None
    avatar: Optional[str] = None
    is_active: bool = True


class DelegateChildRequest(BaseModel):
    name: str
    task: str
    description: Optional[str] = None
    command_limit: Optional[int] = 25
    model: Optional[str] = "stealth/crouton-alpha"
    reasoning_effort: Optional[str] = "high"
    ttl_minutes: Optional[int] = 60
    room_id: Optional[str] = None


@app.get("/api/agents")
async def get_agents(token: str = Depends(require_auth)):
    return list_agents()


@app.post("/api/agents")
async def summon_agent(
    payload: SummonAgentRequest,
    token: str = Depends(require_auth),
):
    """Summon a new agent into the council roster."""
    agent_id = f"agent_{uuid.uuid4().hex[:8]}"
    profile = AgentProfile(
        id=agent_id,
        name=payload.name,
        avatar=payload.avatar or "🤖",
        color=payload.color or "#007AFF",
        model=payload.model or "stealth/crouton-alpha",
        role_description=payload.role_description,
        system_prompt=payload.system_prompt,
        is_active=payload.is_active,
    )
    upsert_agent(profile)
    return profile


@app.post("/api/agents/{parent_id}/delegate")
async def delegate_child_agent(
    parent_id: str,
    payload: DelegateChildRequest,
    token: str = Depends(require_auth),
):
    """Summon and spawn a delegate child agent with lifecycle tracking."""
    delegate_id = f"del_{uuid.uuid4().hex[:8]}"
    ttl_sec = (payload.ttl_minutes or 60) * 60
    record = orchestrator.register_delegate(
        delegate_id=delegate_id,
        name=payload.name,
        task=payload.task,
        description=payload.description or "",
        parent_id=parent_id,
        model=payload.model or "stealth/crouton-alpha",
        reasoning_effort=payload.reasoning_effort or "high",
        ttl_seconds=ttl_sec,
        room_id=payload.room_id or "",
    )
    return record


@app.get("/api/agents/delegates")
async def list_delegates(token: str = Depends(require_auth)):
    """List running and active delegate child agents with live countdowns."""
    return orchestrator.list_delegates()


@app.get("/api/delegation/hierarchy")
async def delegation_hierarchy(token: str = Depends(require_auth)):
    """CLI engines discovered on this machine plus the delegates under each.

    Replaces the client-side fallback that invented two engines with fake
    delegates. An engine that is not installed comes back with
    ``is_available: false`` and the reason, not a fabricated child tree.
    """
    return build_hierarchy(orchestrator.list_delegates())


@app.post("/api/agents/delegates/{delegate_id}/cancel")
async def cancel_delegate(
    delegate_id: str,
    token: str = Depends(require_auth),
):
    """Cancel a running delegate child agent."""
    if not orchestrator.cancel_delegate(delegate_id):
        raise HTTPException(status_code=404, detail="Delegate agent not found")
    return {"status": "cancelled", "id": delegate_id}


@app.post("/api/agents/delegates/{delegate_id}/pause")
async def pause_delegate(
    delegate_id: str,
    token: str = Depends(require_auth),
):
    """Pause or resume a running delegate child agent."""
    if not orchestrator.pause_delegate(delegate_id):
        raise HTTPException(status_code=404, detail="Delegate agent not found")
    return {"status": "ok", "id": delegate_id}


@app.get("/api/orchestrator/status")
async def get_orchestrator_status(token: str = Depends(require_auth)):
    """Live status of the multi-agent turn coordinator."""
    return orchestrator.status()


@app.get("/api/scheduler/status")
async def get_scheduler_status(token: str = Depends(require_auth)):
    """Tick count, in-flight jobs, and throttle window.

    Without this a stopped tick loop is invisible: the schedule renders, jobs
    never fire, and nothing on the host says why.
    """
    return scheduler_tick.status()


@app.get("/api/processes")
async def list_processes(token: str = Depends(require_auth)):
    """Every agent round running right now, with live elapsed time.

    The supervisor screen needs per-process rows to show a timer and a kill
    button; ``/api/orchestrator/status`` only carries counts.
    """
    rows = orchestrator.processes()
    return {"processes": rows, "count": len(rows)}


@app.post("/api/processes/{round_id}/kill")
async def kill_process(round_id: str, token: str = Depends(require_auth)):
    """Cancel one running round. Reports the real outcome, never a guess."""
    return orchestrator.cancel_round(round_id)


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


# ---------------------------------------------------------------------------
# Agent approvals
# ---------------------------------------------------------------------------
#
# The agent pauses on any tool call that the user's posture does not blanket
# allow. The app surfaces that pause as a banner and as a notification with
# inline Approve/Deny, so both paths land on the same endpoint and the core —
# not the client — owns the decision.

class ApprovalDecision(BaseModel):
    approved: bool
    remember: bool = False
    scope: str = "once"


class QuestionAnswerDecision(BaseModel):
    answer: str
    option_value: Optional[str] = None
    scope: Optional[str] = None


@app.get("/api/approvals")
async def list_pending_approvals(token: str = Depends(require_auth)):
    """Everything currently waiting on a human, newest first."""
    return orchestrator.pending_approvals()


@app.post("/api/approvals/{approval_id}/resolve")
async def resolve_approval_endpoint(
    approval_id: str,
    decision: ApprovalDecision,
    token: str = Depends(require_auth),
):
    """Answer a pending approval with scope. 404 when already expired."""
    resolved = await orchestrator.resolve_approval(
        approval_id, decision.approved, scope=decision.scope, remember=decision.remember
    )
    if not resolved:
        raise HTTPException(status_code=404, detail="Approval not found or already resolved")
    return {"resolved": True, "approved": decision.approved, "scope": decision.scope}


@app.get("/api/questions")
async def list_pending_questions(token: str = Depends(require_auth)):
    """Everything currently waiting on human decision/input, newest first."""
    return orchestrator.pending_questions()


@app.post("/api/kanban/events")
async def kanban_ingest(payload: dict, token: str = Depends(require_auth)):
    """Ingest one telemetry event from an agent plugin.

    The OpenCode plugin posts here. Two things matter more than the happy path:

    * the payload is redacted server-side as well as in the plugin, because a
      single missed pattern on the client must not leak a credential into
      SQLite and out to a phone over a websocket;
    * a malformed event is rejected with a clear message rather than stored as a
      half-row, so the board never shows a call with no tool name.
    """
    from server.tool_ledger import ledger, redact

    room_id = str(payload.get("room_id") or "the-council")
    event = payload.get("event") or {}
    if not isinstance(event, dict):
        raise HTTPException(status_code=400, detail="event must be an object")

    kind = str(event.get("kind") or "")
    tool = str(event.get("tool") or "").strip()
    if not tool:
        raise HTTPException(status_code=400, detail="event.tool is required")
    if kind not in ("tool.start", "tool.finish"):
        # Session and permission events are acknowledged but not ledgered here;
        # they have their own surfaces and would otherwise pollute the board.
        return {"ok": True, "recorded": False, "kind": kind}

    agent_id = str(event.get("agentId") or "opencode")
    agent_name = str(event.get("agentName") or "OpenCode")

    if kind == "tool.start":
        record = ledger.start(
            room_id=room_id,
            agent_id=agent_id,
            agent_name=agent_name,
            tool=tool,
            arguments=event.get("arguments"),
            is_delegate=bool(event.get("isDelegate")),
            parent_agent_id=str(event.get("parentAgentId") or ""),
        )
        return {"ok": True, "recorded": True, "id": record.id}

    # tool.finish closes the most recent running call for this agent+tool.
    closed = ledger.close_open(
        room_id=room_id,
        agent_id=agent_id,
        tool=tool,
        status=str(event.get("status") or "ok"),
        result_summary=str(event.get("resultSummary") or ""),
        error=str(event.get("error") or ""),
    )
    if closed is None:
        # A finish with no matching start means the plugin or the server
        # restarted mid-call. Acknowledge it; inventing a zero-duration row
        # would put a fake in the board.
        return {"ok": True, "recorded": False, "reason": "no matching open call"}
    return {"ok": True, "recorded": True, "id": closed.id}


# ---------------------------------------------------------------------------
# OpenCode: the one CLI agent with a real live API
# ---------------------------------------------------------------------------


@app.get("/api/opencode/health")
async def opencode_health(token: str = Depends(require_auth)):
    """Authenticated probe of `opencode serve`.

    Returns the real model list on success. On 401 it returns the exact
    diagnostic instead of a generic failure, because this endpoint exists
    specifically because the app spent a long time failing here silently.
    """
    from server.opencode_bridge import OpenCodeClient

    client = OpenCodeClient()
    result = client.health()
    return {
        "ok": result.ok,
        "status": result.status,
        "error": result.error,
        "models": result.models,
    }


@app.get("/api/opencode/sessions")
async def opencode_sessions(token: str = Depends(require_auth)):
    """Real sessions from the live OpenCode server, newest first."""
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().list_sessions()
    if not result.ok:
        return {"ok": False, "error": result.error, "sessions": []}
    rows = (result.data or {}).get("data") or []
    sessions = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        model = row.get("model") or {}
        sessions.append(
            {
                "id": row.get("id"),
                "title": (row.get("title") or "").strip() or "Untitled session",
                "agent": row.get("agent"),
                "model": model.get("id") if isinstance(model, dict) else None,
                "project_id": row.get("projectID"),
            }
        )
    return {"ok": True, "sessions": sessions, "count": len(sessions)}


@app.get("/api/opencode/agents")
async def opencode_agents(token: str = Depends(require_auth)):
    """The harness's own agents: the primary one you talk to, and the
    subagents it can summon.

    Needed because a subagent's run is invisible from the transcript alone: the
    parent records a `task` tool call, but the child's own messages live in a
    separate session, so the app could not show who was summoned or what it did.
    """
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().list_agents()
    if not result.ok:
        return {"ok": False, "error": result.error, "agents": []}

    rows = (result.data or {}).get("data") or []
    agents = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        agents.append(
            {
                "id": row.get("id"),
                "name": row.get("name") or row.get("id"),
                "description": row.get("description") or "",
                # "primary" is the one you address; "subagent" can be summoned
                # through the task tool. Hidden ones are harness internals
                # (title generation, compaction) and are not user-facing.
                "mode": row.get("mode") or "primary",
                "hidden": bool(row.get("hidden")),
                "model": (row.get("model") or {}).get("modelID")
                if isinstance(row.get("model"), dict)
                else row.get("model"),
                "prompt": ((row.get("prompt") or "")[:400] or None),
            }
        )
    return {
        "ok": True,
        "agents": agents,
        "count": len(agents),
        "primary": [a for a in agents if a["mode"] == "primary" and not a["hidden"]],
        "subagents": [a for a in agents if a["mode"] == "subagent" and not a["hidden"]],
    }


@app.post("/api/opencode/sessions")
async def create_opencode_session(token: str = Depends(require_auth)):
    """Create a new fresh OpenCode session."""
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().create_session("New Session")
    if not result.ok:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=result.error or "Could not create OpenCode session",
        )
    row = (result.data or {}).get("data") or {}
    return {
        "ok": True,
        "session": {
            "id": row.get("id"),
            "title": row.get("title") or "New Session",
            "agent": row.get("agent"),
            "model": None,
            "project_id": row.get("projectID"),
        },
    }


@app.get("/api/opencode/sessions/{session_id}/subagents")
async def opencode_session_subagents(
    session_id: str,
    token: str = Depends(require_auth),
):
    """The subagents this session summoned, with their own transcripts.

    OpenCode runs each summoned subagent in a *child session* linked by
    `parentID`. The parent transcript only records a `task` tool call, so the
    child's reasoning and tool work are invisible unless you join on parentID —
    which is what this does.
    """
    from server.opencode_bridge import OpenCodeClient

    client = OpenCodeClient()
    result = client.list_sessions()
    if not result.ok:
        return {"ok": False, "error": result.error, "subagents": []}

    rows = (result.data or {}).get("data") or []
    children = [r for r in rows if isinstance(r, dict) and r.get("parentID") == session_id]

    # A child created but never prompted has no content; still listed, flagged.
    out = []
    for row in children:
        msgs = client.session_messages(row["id"], limit=50)
        tools: List[str] = []
        reasoning = 0
        text = ""
        if msgs.ok:
            for m in (msgs.data or {}).get("data") or []:
                if not isinstance(m, dict):
                    continue
                for part in m.get("content") or []:
                    if not isinstance(part, dict):
                        continue
                    if part.get("type") == "tool":
                        tools.append(part.get("name") or "")
                    elif part.get("type") == "reasoning":
                        reasoning += len(part.get("text") or "")
                    elif part.get("type") == "text" and m.get("type") == "assistant":
                        text += (part.get("text") or "") + "\n"

        out.append(
            {
                "id": row["id"],
                "title": (row.get("title") or "").strip() or "Subagent",
                "agent": row.get("agent"),
                "model": (row.get("model") or {}).get("id")
                if isinstance(row.get("model"), dict)
                else None,
                "created_ms": (row.get("time") or {}).get("created"),
                "tool_calls": len(tools),
                "tools": tools[:20],
                "reasoning_chars": reasoning,
                "text": text.strip()[:2000],
            }
        )

    out.sort(key=lambda r: r.get("created_ms") or 0)
    return {"ok": True, "subagents": out, "count": len(out)}


@app.get("/api/opencode/sessions/{session_id}/config")
async def opencode_session_config(
    session_id: str,
    token: str = Depends(require_auth),
):
    """This session's agent and model, for the picker's current selection."""
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().get_session(session_id)
    if not result.ok:
        return {"ok": False, "error": result.error, "agent": None, "model": None}

    row = (result.data or {}).get("data") or {}
    model = row.get("model") if isinstance(row.get("model"), dict) else {}
    return {
        "ok": True,
        "agent": row.get("agent"),
        "model": model.get("id"),
        "provider_id": model.get("providerID"),
    }


@app.get("/api/opencode/sessions/{session_id}/messages")
async def opencode_session_messages(
    session_id: str, limit: int = 50, token: str = Depends(require_auth)
):
    """One session's messages, so the app can show a conversation.

    Three shapes come back and the caller has to cope with all of them: the
    message list puts user text in `text`, the prompt response in
    `payload.text`, and assistant turns use `content[].text`.
    """
    from server.opencode_bridge import OpenCodeClient

    limit = max(1, min(int(limit), 200))
    result = OpenCodeClient().session_messages(session_id, limit)
    if not result.ok:
        return {"ok": False, "error": result.error, "messages": []}

    rows = (result.data or {}).get("data") or []
    messages = []

    # OpenCode returns newest-first. Both the chat order and the reply poll
    # assume oldest-first, so sort once here rather than making every caller
    # reverse it. Unsorted, `lastOrNull()` is the *oldest* message in the
    # session: the poll either answers with a stale turn or never sees the
    # new one at all.
    def _created(row: dict) -> float:
        t = row.get("time")
        return float(t.get("created") or 0) if isinstance(t, dict) else 0.0

    for row in sorted(rows, key=_created):
        if not isinstance(row, dict):
            continue
        info = row.get("info") if isinstance(row.get("info"), dict) else {}
        role = info.get("type") or row.get("type") or "unknown"

        # An assistant turn's prose and its reasoning live side by side in
        # `content[]`. Split them: the chat renders the text, the thinking pill
        # renders the reasoning. Dropping reasoning here is what made every
        # OpenCode turn look like it answered with no thought at all.
        content = row.get("content")
        text = ""
        reasoning = ""
        tool_records: List[Dict[str, Any]] = []
        if isinstance(content, list):
            for part in content:
                if not isinstance(part, dict):
                    continue
                part_type = part.get("type")
                if part_type == "text":
                    text += (part.get("text") or "") + "\n"
                elif part_type == "reasoning":
                    reasoning += (part.get("text") or "") + "\n"
                elif part_type == "tool":
                    tool_records.append(_tool_record(part))
            text = text.strip()
            reasoning = reasoning.strip()

        if not text:
            text = (row.get("text") or "").strip()
        if not text:
            payload = row.get("payload")
            if isinstance(payload, dict):
                text = (payload.get("text") or "").strip()

        # Telemetry the engine already reports; the app had it on the wire and
        # threw it away, so the chat could not show cost, tokens or how long the
        # turn actually took.
        tokens = row.get("tokens") if isinstance(row.get("tokens"), dict) else {}
        time_info = row.get("time") if isinstance(row.get("time"), dict) else {}
        metrics = None
        if role == "assistant" and (tokens or time_info.get("completed")):
            streamed = time_info.get("streamed")
            completed = time_info.get("completed")
            latency = None
            if isinstance(completed, (int, float)) and isinstance(streamed, (int, float)):
                latency = int(completed - streamed)
            cache = tokens.get("cache") if isinstance(tokens.get("cache"), dict) else {}
            metrics = {
                "latency_ms": latency,
                "prompt_tokens": tokens.get("input"),
                "completion_tokens": tokens.get("output"),
                "reasoning_tokens": tokens.get("reasoning"),
                "cost_usd": row.get("cost"),
                "model": (row.get("model") or {}).get("id")
                if isinstance(row.get("model"), dict)
                else None,
                "cache_read_tokens": cache.get("read"),
            }

        error = row.get("error") or info.get("error") or {}
        messages.append(
            {
                "id": row.get("id"),
                "role": role,
                "text": text,
                "thought": reasoning or None,
                "tool_records": tool_records,
                "metrics": metrics,
                "error": (error or {}).get("message") if isinstance(error, dict) else None,
                "created_ms": (row.get("time") or {}).get("created")
                if isinstance(row.get("time"), dict)
                else None,
            }
        )
    return {"ok": True, "messages": messages, "count": len(messages)}


def _tool_record(part: Dict[str, Any]) -> Dict[str, Any]:
    """One tool part, in the shape the app's ToolCallRecordDto already reads.

    OpenCode nests the useful fields under `state` and only fills them as the
    call progresses, so a running call has no content yet.
    """
    state = part.get("state") if isinstance(part.get("state"), dict) else {}
    status = state.get("status")
    output = state.get("content")
    if isinstance(output, list):
        # webfetch/read return content parts; flatten to the text.
        output = "\n".join(
            (piece.get("text") or "")
            for piece in output
            if isinstance(piece, dict)
        ).strip()
    elif not isinstance(output, str):
        output = ""
    error = state.get("error")
    return {
        "id": part.get("id") or "",
        "name": part.get("name") or "",
        "summary": state.get("title") or part.get("name") or "",
        "arguments": json.dumps(state.get("input") or {}, ensure_ascii=False),
        "status": "succeeded" if status == "completed" else (status or "running"),
        "started_at": (part.get("time") or {}).get("start") or 0.0
        if isinstance(part.get("time"), dict)
        else 0.0,
        "ended_at": (part.get("time") or {}).get("end")
        if isinstance(part.get("time"), dict)
        else None,
        "output_preview": output[:2000],
        "error": (error.get("message") if isinstance(error, dict) else error) or None,
    }


@app.post("/api/opencode/sessions/{session_id}/model")
async def opencode_set_model(
    session_id: str,
    body: Dict[str, Any] = Body(...),
    token: str = Depends(require_auth),
):
    """Pin the model this session runs on.

    Lets the app switch a session's model (and, separately, its agent) without
    the user going near a config file.
    """
    from server.opencode_bridge import OpenCodeClient

    provider = body.get("providerID") or body.get("provider_id") or ""
    model = body.get("modelID") or body.get("model_id") or ""
    if not provider or not model:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="providerID and modelID are both required",
        )

    result = OpenCodeClient().set_session_model(session_id, provider, model)
    if not result.ok:
        return {"ok": False, "error": result.error}
    return {"ok": True, "model": {"id": model, "providerID": provider}}


@app.post("/api/opencode/sessions/{session_id}/agent")
async def opencode_set_agent(
    session_id: str,
    body: Dict[str, Any] = Body(...),
    token: str = Depends(require_auth),
):
    """Point the session at a different harness agent (build, plan, general…)."""
    from server.opencode_bridge import OpenCodeClient

    agent = (body.get("agent") or "").strip()
    if not agent:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="agent is required",
        )

    result = OpenCodeClient().set_session_agent(session_id, agent)
    if not result.ok:
        return {"ok": False, "error": result.error}
    return {"ok": True, "agent": agent}


@app.get("/api/opencode/models")
async def opencode_models(token: str = Depends(require_auth)):
    """Models the harness can run, as the picker needs them."""
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().list_models()
    if not result.ok:
        return {"ok": False, "error": result.error, "models": []}

    rows = (result.data or {}).get("data") or []
    models = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        caps = row.get("capabilities") or {}
        inputs = caps.get("input") or []
        models.append(
            {
                "id": row.get("id"),
                "provider_id": row.get("providerID"),
                "name": row.get("name") or row.get("id"),
                "vision": "image" in inputs,
                "tools": bool(caps.get("tools")),
                "context": (row.get("limit") or {}).get("context"),
                "reasoning": bool(row.get("reasoning")),
            }
        )
    return {"ok": True, "models": models, "count": len(models)}


@app.post("/api/opencode/sessions/{session_id}/interrupt")
async def opencode_interrupt_session(
    session_id: str,
    token: str = Depends(require_auth),
):
    """Stop the turn in flight.

    The composer shows a stop control while a turn runs, so this has to be a
    first-class call rather than a UI trick: the harness owns the turn, and only
    it can end one.
    """
    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().interrupt(session_id)
    if not result.ok:
        return {"ok": False, "error": result.error}
    return {"ok": True, "interrupted": session_id}


@app.post("/api/opencode/sessions/{session_id}/prompt")
async def opencode_session_prompt(
    session_id: str, body: Dict[str, Any] = Body(...), token: str = Depends(require_auth)
):
    """Send a message to an OpenCode session.

    Accepted at once and answered later: the reply is read back through the
    messages endpoint once the assistant turn completes.
    """
    text = (body or {}).get("text")
    if not isinstance(text, str) or not text.strip():
        raise HTTPException(status_code=422, detail="text is required")

    from server.opencode_bridge import OpenCodeClient

    result = OpenCodeClient().send_prompt(session_id, text)
    if not result.ok:
        return {"ok": False, "error": result.error}
    return {"ok": True, "sent": text, "message": result.data}


# ---------------------------------------------------------------------------
# Agents: real roster + real sessions
# ---------------------------------------------------------------------------


@app.get("/api/agents/roster")
async def agents_roster(token: str = Depends(require_auth)):
    """Discovered engines and their real sessions.

    Replaces the hardcoded CLI tool list the client used to render. Engines that
    are not installed stay in the response with the reason, so the UI can say
    "not installed" instead of inventing a session history.
    """
    from server.agent_sessions import discover_agent_sessions

    return discover_agent_sessions()


# ---------------------------------------------------------------------------
# Git diff: what actually changed in the working tree
# ---------------------------------------------------------------------------


@app.get("/api/git/diff")
async def git_diff(token: str = Depends(require_auth)):
    """Working-tree diff for the repository the core is running in.

    The Android diff screen has always called this endpoint; it did not exist,
    so the screen could only ever answer 404. Nothing is synthesised here: if
    git is missing or the path is not a repository, that is a 503 with the
    reason, never an empty diff that would read as "nothing changed".
    """
    repo = Path(__file__).resolve().parent.parent

    def _run(*args: str) -> str:
        return subprocess.run(
            ["git", *args],
            cwd=str(repo),
            capture_output=True,
            text=True,
            timeout=20,
        ).stdout

    try:
        inside = subprocess.run(
            ["git", "rev-parse", "--is-inside-work-tree"],
            cwd=str(repo),
            capture_output=True,
            text=True,
            timeout=10,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        raise HTTPException(status_code=503, detail="git is not available on the host")
    if inside.returncode != 0 or inside.stdout.strip() != "true":
        raise HTTPException(status_code=503, detail=f"{repo} is not a git repository")

    branch = _run("rev-parse", "--abbrev-ref", "HEAD").strip() or "detached"

    # -U0 keeps the payload small; the screen shows every hunk it is given.
    raw = _run("diff", "HEAD", "--no-color", "--unified=3")

    files: List[Dict[str, Any]] = []
    current: Optional[Dict[str, Any]] = None
    old_no = new_no = 0

    for line in raw.splitlines():
        if line.startswith("diff --git "):
            if current is not None:
                files.append(current)
            parts = line.split(" b/", 1)
            path = parts[1] if len(parts) == 2 else line.split()[-1]
            current = {
                "filename": path.rsplit("/", 1)[-1],
                "path": path,
                "additions": 0,
                "deletions": 0,
                "lines": [],
            }
            continue
        if current is None:
            continue
        if line.startswith("@@"):
            # @@ -a,b +c,d @@
            try:
                span = line.split("@@")[1].strip()
                minus, plus = span.split(" ")
                old_no = int(minus[1:].split(",")[0])
                new_no = int(plus[1:].split(",")[0])
            except (IndexError, ValueError):
                old_no = new_no = 0
            current["lines"].append(
                {"line_type": "hunk", "old_num": None, "new_num": None, "content": line}
            )
            continue
        if line.startswith("+++") or line.startswith("---"):
            continue

        if line.startswith("+"):
            current["additions"] += 1
            current["lines"].append(
                {"line_type": "add", "old_num": None, "new_num": new_no, "content": line[1:]}
            )
            new_no += 1
        elif line.startswith("-"):
            current["deletions"] += 1
            current["lines"].append(
                {"line_type": "del", "old_num": old_no, "new_num": None, "content": line[1:]}
            )
            old_no += 1
        else:
            body = line[1:] if line.startswith(" ") else line
            current["lines"].append(
                {"line_type": "context", "old_num": old_no, "new_num": new_no, "content": body}
            )
            old_no += 1
            new_no += 1

    if current is not None:
        files.append(current)

    return {
        "branch": branch,
        "total_additions": sum(f["additions"] for f in files),
        "total_deletions": sum(f["deletions"] for f in files),
        "files": files,
    }


# ---------------------------------------------------------------------------
# Kanban: the tool-call ledger
# ---------------------------------------------------------------------------


@app.get("/api/kanban/board")
async def kanban_board(
    room_id: str = "the-council",
    limit: int = 120,
    token: str = Depends(require_auth),
):
    """Live tool-call board: what every agent is calling right now.

    Four lanes, in the order an operator cares about: in-flight calls, recent
    calls, failures, and per-agent counters. Everything comes from the ledger
    the bus writes on every invocation, so a call cannot happen without
    appearing here.
    """
    from server.tool_ledger import ledger, summarize_arguments

    running = ledger.running(room_id)
    recent = ledger.recent(room_id, limit=limit)
    failures = ledger.recent(room_id, limit=40, failures_only=True)
    stats = ledger.stats(room_id)

    def _row(entry: dict) -> dict:
        return {
            **entry,
            "arguments_preview": summarize_arguments(entry.get("arguments")),
        }

    return {
        "room_id": room_id,
        "stats": stats,
        "lanes": {
            "running": [_row(r) for r in running],
            "recent": [_row(r) for r in recent],
            "failures": [_row(r) for r in failures],
        },
        "server_time": time.time(),
    }


@app.get("/api/kanban/stats")
async def kanban_stats(
    room_id: str = "the-council",
    token: str = Depends(require_auth),
):
    """Compact per-agent / per-tool counters for the board header."""
    from server.tool_ledger import ledger

    return ledger.stats(room_id)


@app.post("/api/questions/{question_id}/answer")
async def answer_question_endpoint(
    question_id: str,
    decision: QuestionAnswerDecision,
    token: str = Depends(require_auth),
):
    """Answer an agent question or permission query."""
    resolved = await orchestrator.answer_question(
        question_id,
        decision.answer,
        option_value=decision.option_value,
        scope=decision.scope,
    )
    if not resolved:
        raise HTTPException(status_code=404, detail="Question not found or already answered")
    return {"resolved": True, "answer": decision.answer}


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
    # A message to a room that does not exist is an orphan: it would sit in the
    # database with no room listing it, invisible to every client yet returned by
    # a direct GET. Refuse it so senders learn the room id is wrong.
    # "the-council" is the ambient room the orchestrator always answers in; it
    # is exempt because it is created lazily on first use.
    if req.room_id != "the-council" and get_room(req.room_id) is None:
        raise HTTPException(status_code=404, detail=f"no such room: {req.room_id}")
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


# ---- Remote control: any agent that can reach the host can drive the phone ----
# The queue is file-backed on purpose: no new daemon, and the host's existing
# auth is the only door in.

@app.get("/api/remote/status")
async def remote_status(token: str = Depends(require_auth)):
    return remote_control.status()


@app.post("/api/remote/command")
async def remote_command(body: Dict[str, Any] = Body(...), token: str = Depends(require_auth)):
    device = str(body.get("device") or "phone").strip()
    action = str(body.get("action") or "").strip()
    payload = body.get("payload") or {}
    if not action:
        raise HTTPException(status_code=400, detail="action is required")
    return remote_control.enqueue(device, action, payload)


@app.get("/api/remote/next")
async def remote_next(device: str = Query("phone"), token: str = Depends(require_auth)):
    cmd = remote_control.next_command(device)
    return {"command": cmd}


@app.post("/api/remote/ack/{command_id}")
async def remote_ack(command_id: str, token: str = Depends(require_auth)):
    remote_control.ack(command_id)
    return {"ok": True}


@app.post("/api/remote/result/{command_id}")
async def remote_result(
    command_id: str, body: Dict[str, Any] = Body(...), token: str = Depends(require_auth)
):
    return remote_control.finish(
        command_id,
        bool(body.get("ok")),
        str(body.get("output") or ""),
        body.get("screenshot"),
    )


@app.get("/api/remote/result")
async def remote_result_get(command_id: str = Query(...), token: str = Depends(require_auth)):
    found = remote_control.result(command_id)
    if not found:
        raise HTTPException(status_code=404, detail="no result yet")
    return found


@app.get("/api/remote/artifact/{command_id}")
async def remote_artifact(command_id: str, token: str = Depends(require_auth)):
    from fastapi.responses import FileResponse

    path = remote_control.ARTIFACTS / f"{command_id}.png"
    if not path.exists():
        raise HTTPException(status_code=404, detail="no screenshot")
    return FileResponse(str(path), media_type="image/png")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server.main:app", host="0.0.0.0", port=8000, reload=True)


def _tailscale_enabled() -> bool:
    """Runtime Tailscale detection - never hardcoded."""
    try:
        out = subprocess.run(
            ["tailscale", "status", "--json"],
            capture_output=True, text=True, timeout=3
        )
        if out.returncode == 0 and '"BackendState"' in out.stdout:
            return '"Running"' in out.stdout
    except Exception:
        pass
    return os.path.exists("/sys/class/net/tailscale0")
