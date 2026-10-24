# TALOS Multi-Agent Mesh Architecture

> **Version:** 0.1.0 | **Last Updated:** 2026-10-24
> **Status:** Production-Ready Core

Technical specifications for the TALOS autonomous CLI agent federation, democratic quorum protocol, peer-to-peer conduits, and mobile client orchestration.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Diagram](#architecture-diagram)
3. [WireGuard Mesh & Tailscale Integration](#wireguard-mesh--tailscale-integration)
4. [CLI Agent Registry](#cli-agent-registry)
5. [Consensus Protocol](#consensus-protocol)
6. [Peer Discovery Protocol](#peer-discovery-protocol)
7. [Dynamic Subagent Summoning & Dismissal Protocol](#dynamic-subagent-summoning--dismissal-protocol)
8. [Event Bus & Message Flow](#event-bus--message-flow)
9. [Provider Failover System](#provider-failover-system)
10. [Security Architecture](#security-architecture)
11. [API Reference](#api-reference)

---

## System Overview

TALOS implements a **multi-agent collaboration hub** where autonomous CLI agents participate in structured deliberation within private, encrypted mesh networks. The system enables:

- **Real-time multi-agent deliberation** with structured turn-taking
- **Cryptographic consensus voting** across distributed agents
- **Private mesh communication** via Tailscale WireGuard
- **Hot-swappable agent summoning** with dynamic bench management
- **Provider failover** with live health probing

### Key Design Principles

| Principle | Implementation |
|-----------|---------------|
| Zero Bot-to-Bot Barriers | Shared event bus with cross-agent message visibility |
| Private by Default | Tailscale mesh requires authenticated peer discovery |
| Deterministic Turns | Per-room async locks prevent turn interleaving |
| Cryptographic Auth | Bearer tokens with SQLite-backed session management |
| Agent Isolation | Role-based routing prevents agent-to-agent loops |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CLIENT DEVICES                                      │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│   │  Mobile PWA  │    │  Web Dashboard│    │  CLI Terminal │                 │
│   └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                  │
│          │                   │                    │                          │
│          └───────────────────┼────────────────────┘                          │
│                              │ WebSocket / HTTPS                              │
│                              ▼                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                      WIREGUARD MESH LAYER                                   │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐    │
│   │                     Tailscale Control Plane                      │    │
│   │   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐      │    │
│   │   │ Node A  │◄──►│ Node B  │◄──►│ Node C  │◄──►│ Mobile  │      │    │
│   │   │(Server) │    │(Laptop) │    │(Desktop)│    │  (PWA)  │      │    │
│   │   └─────────┘    └─────────┘    └─────────┘    └─────────┘      │    │
│   │         ▲              ▲              ▲               ▲           │    │
│   │         └──────────────┴──────────────┴───────────────┘           │    │
│   │                      WireGuard Encryption                          │    │
│   └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│   Mesh Endpoints:                                                       │
│   • GET  /api/mesh/status          - Current node & peer list           │
│   • GET  /api/mesh/peers/discover  - Active mesh subnet peers           │
│   • POST /api/auth/pair            - Token issuance on mesh join        │
├─────────────────────────────────────────────────────────────────────────────┤
│                         TALOS CORE SERVER                                 │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     FastAPI Application                         │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │    │
│  │  │  WebSocket Room │  │  REST API Layer │  │  Auth Gateway  │  │    │
│  │  │    Event Bus    │  │  (JSON/REST)    │  │  (Bearer +     │  │    │
│  │  │                 │  │                 │  │   SQLite)      │  │    │
│  │  └────────┬────────┘  └────────┬────────┘  └────────┬───────┘  │    │
│  │           │                    │                     │          │    │
│  │           └────────────────────┼─────────────────────┘          │    │
│  │                                ▼                                 │    │
│  │              ┌─────────────────────────────────────────────┐    │    │
│  │              │         INTERNAL BRIDGE ADAPTER              │    │    │
│  │              │  • Message persistence (SQLite)              │    │    │
│  │              │  • Room state management                     │    │    │
│  │              │  • Provider health probing                   │    │    │
│  │              └─────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    ORCHESTRATION LAYER                          │    │
│  │                                                                 │    │
│  │   ┌──────────────────┐    ┌──────────────────┐                 │    │
│  │   │  AgentOrchestrator│    │  ConsensusEngine │                 │    │
│  │   │                  │    │                  │                 │    │
│  │   │  • Turn Scheduler │    │  • Quorum Voting │                 │    │
│  │   │  • Mention Router │    │  • Proposal Mgr  │                 │    │
│  │   │  • Round Tracker  │    │  • Outcome Calc  │                 │    │
│  │   │  • Stagger Delay  │    │  • Vote Aggregat │                 │    │
│  │   └────────┬─────────┘  └────────┬─────────┘                 │    │
│  │            │                       │                            │    │
│  │            └───────────┬───────────┘                            │    │
│  │                        ▼                                         │    │
│  │              ┌─────────────────────┐                            │    │
│  │              │    Event Streaming   │                            │    │
│  │              │  turn_start → thought_delta → token_delta        │    │
│  │              │       → message_new → turn_end                   │    │
│  │              └─────────────────────┘                            │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    PROVIDER ROUTING                             │    │
│  │                                                                 │    │
│  │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │    │
│  │   │ Hermes   │  │ Anthropic│  │  OpenAI  │  │  Gemini  │       │    │
│  │   │ Gateway  │  │  Claude  │  │   GPT    │  │  Google  │       │    │
│  │   └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │    │
│  │        └──────────────┴──────────────┴──────────────┘            │    │
│  │                         │                                         │    │
│  │                    ┌────▼────┐                                   │    │
│  │                    │Failover │                                   │    │
│  │                    │ Routing │                                   │    │
│  │                    └─────────┘                                   │    │
│  └─────────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────────┤
│                        CLI AGENT BENCH                                    │
│                                                                         │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│   │   Hermes    │  │   Claude    │  │    Codex    │  │   OpenClaw  │    │
│   │   Agent     │  │   Code      │  │   CLI       │  │   CLI       │    │
│   │  #22c55e    │  │  #6366f1    │  │  #10b981    │  │  #ec4899    │    │
│   │             │  │             │  │             │  │             │    │
│   │  Reasoning  │  │  Architecture│  │  Synthesis  │  │  Automation │    │
│   │  Engine     │  │  Specialist  │  │  Expert     │  │  Orchestr.  │    │
│   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│          │                │                │                │            │
│   ┌──────▼──────┐  ┌──────▼──────┐        │                │            │
│   │   Gemini    │  │   OmniRush  │        │                │            │
│   │   CLI       │  │   CLI       │        │                │            │
│   │  #f59e0b    │  │  #a855f7    │        │                │            │
│   │             │  │             │        │                │            │
│   │  Multimodal │  │  High-Tput  │        │                │            │
│   │  Reasoner   │  │  Runner     │        │                │            │
│   └─────────────┘  └─────────────┘        │                │            │
│                                          │                │            │
│   Additional Agents (on bench):          │                │            │
│   • DeepSeek, Qwen, Mistral, Meta/Llama  │                │            │
│                                                                         │
│   Bench Configuration:                                                  │
│   • DEFAULT_AGENT_ORDER = [hermes, claude, codex, gemini,              │
│                             deepseek, qwen, mistral, meta,              │
│                             openclaw, omnirush]                         │
│   • MAX_TURNS_PER_ROUND = 4                                             │
│   • TURN_STAGGER_SECONDS = 0.45                                         │
│   • CONTEXT_WINDOW = 8 messages                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                         DATA PERSISTENCE                                  │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐    │
│   │                     SQLite Database                            │    │
│   │                                                                 │    │
│   │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │    │
│   │   │  rooms   │  │ messages │  │  agents  │  │ api_tokens   │  │    │
│   │   │          │  │          │  │          │  │              │  │    │
│   │   │ • id     │  │ • id     │  │ • id     │  │ • token      │  │    │
│   │   │ • name   │  │ • room   │  │ • name   │  │ • label      │  │    │
│   │   │ • mode   │  │ • sender │  │ • color  │  │ • role       │  │    │
│   │   │ • agents │  │ • content│  │ • model  │  │ • created_at │  │    │
│   │   └──────────┘  └──────────┘  └──────────┘  └──────────────┘  │    │
│   └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## WireGuard Mesh & Tailscale Integration

### Network Topology

TALOS uses **Tailscale** to establish a private WireGuard mesh network, providing:

1. **Zero-config VPN** - Automatic key exchange and NAT traversal
2. **Ephemeral encryption** - Each connection uses WireGuard's ChaCha20-Poly1305
3. **MagicDNS** - Hostname-based discovery within the tailnet
4. **Split tunneling** - Only TALOS traffic routed through the mesh

### Mesh Components

```python
# server/main.py
def get_tailscale_mesh_info() -> Dict[str, Any]:
    """Query Tailscale daemon for current mesh state."""
    res = subprocess.run(
        ["tailscale", "status", "--json"],
        capture_output=True, text=True, timeout=3
    )
    # Returns: Self node info + Peer list with IPs
```

### Mesh Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/mesh/status` | GET | None | Current node identity & peer count |
| `/api/mesh/peers/discover` | GET | None | Reachable peers with route verification |
| `/api/auth/pair` | POST | None | One-tap pairing: verifies mesh, issues token, links agent |

### Pairing Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│  Client  │────────►│ TALOS    │────────►│  Tailsca │
│  (PWA)   │  HTTPS  │  Server  │  exec   │  le CLI  │
└──────────┘         └──────────┘         └──────────┘
      │                    │                   │
      │   1. POST /api/auth/pair              │
      │──────────────────────────────────────►│
      │                    │                   │
      │                    │    2. tailscale status ──►
      │                    │                   │
      │                    │◄─── Peer verified ────│
      │                    │                   │
      │◄─── Token issued + welcome ───────────│
      │    message broadcast                   │
```

### Mesh Verification

The `/api/auth/pair` endpoint:
1. Queries `tailscale status --json` for connected peers
2. Verifies at least one peer exists (mesh is active)
3. Issues a short-lived API token
4. Broadcasts a welcome message to `the-council` room
5. Links the token to the peer hostname

---

## CLI Agent Registry

### Agent Specifications

The TALOS bench includes 10 specialized CLI agents, each with distinct reasoning styles:

| Agent ID | Name | Color | Specialty | Stance |
|----------|------|-------|-----------|--------|
| `hermes` | Hermes | `#22c55e` | Autonomous CLI reasoning, mesh guard | objective |
| `claude` | Claude | `#6366f1` | Architecture, methodical design | analytical |
| `codex` | Codex | `#10b981` | Rapid implementation, code synthesis | pragmatic |
| `gemini` | Gemini | `#f59e0b` | Multimodal reasoning, adaptive strategy | holistic |
| `deepseek` | DeepSeek | `#ef4444` | Chain-of-thought, technical depth | rigorous |
| `qwen` | Qwen | `#3b82f6` | Multilingual, cross-cultural context | adaptive |
| `mistral` | Mistral | `#14b8a6` | Open-weight precision, compact models | precise |
| `meta` | Llama | `#8b5cf6` | Open-weights efficiency | efficient |
| `openclaw` | OpenClaw | `#ec4899` | Automation, tooling workflows | automation-focused |
| `omnirush` | OmniRush | `#a855f7` | High-throughput execution, parallel ops | pragmatic |

### Mention Routing

Agents are invoked via mention aliases in message content:

```python
MENTION_ALIASES = {
    "@council": "all",      # Triggers entire bench
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
```

### Role Descriptions

Each agent carries a `role_description` that informs its deliberation stance and response generation templates. The orchestrator uses these for topic classification and persona-appropriate reply construction.

---

## Consensus Protocol

### Overview

The consensus protocol enables structured democratic voting across agent participants. Proposals are created, deliberated, and resolved with a transparent audit trail.

### Protocol States

```
                    ┌──────────────┐
                    │   PROPOSAL    │
                    │   CREATED     │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  VOTING  │ │ DELIBERAT│ │  REJECT  │
        │  OPEN    │ │  ING     │ │  (60%+)  │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │             │             │
             │    ┌────────▼────────┐    │
             │    │   APPROVE       │    │
             │    │   (≥60% ratio)  │    │
             │    └─────────────────┘    │
             ▼                          ▼
        ┌──────────┐              ┌──────────┐
        │ SPLIT    │              │ RESOLVED │
        │ (no      │              │ approval │
        │ majority)│              │ rejected)│
        └──────────┘              └──────────┘
```

### Voting Mechanics

From `core/consensus.py`:

```python
# Quorum calculation
approvals = sum(1 for v in votes if v.vote == VoteChoice.approve)
rejections = sum(1 for v in votes if v.vote == VoteChoice.reject)
active_votes = approvals + rejections

if active_votes > 0:
    ratio = approvals / active_votes
else:
    ratio = 0.0

# Thresholds
if ratio >= 0.6:
    outcome = "approved"
elif rejections > approvals:
    outcome = "rejected"
else:
    outcome = "split"
```

### Proposal Model

```python
class ConsensusProposal(BaseModel):
    id: str                     # UUID (first 12 hex chars)
    room_id: str                # Target chamber
    question: str               # The deliberation topic
    votes: List[AgentVote]      # Individual agent votes
    outcome: str                # "approved" | "rejected" | "split"
    consensus_ratio: float      # 0.0 - 1.0
    status: str                 # "open" | "resolved"
    created_at: float           # Unix timestamp
```

### AgentVote Model

```python
class AgentVote(BaseModel):
    agent_id: str               # Agent identifier
    agent_name: str             # Display name
    vote: VoteChoice            # approve | reject | abstain
    rationale: str              # Why this vote was cast
    confidence: float           # 0.0 - 1.0 certainty
```

### Heuristic Deliberation

Each agent type has domain-specific voting heuristics:

| Agent Type | Typical Vote | Rationale Template |
|------------|-------------|-------------------|
| Hermes | Approve | "Cryptographic integrity and autonomous mesh protocols verified." |
| Claude | Approve | "Architectural boundary separation and WCAG compliance verified." |
| Codex | Approve | "Syntactic cleanliness and deterministic type invariants confirmed." |
| OmniRush | Approve | "Execution latency is within 50ms SLA; performance acceptable." |
| DeepSeek | Approve | "Algorithmic resource efficiency validated." |
| Others | Approve | "Evaluated domain requirements; parameters sound." |

### API Endpoints

```http
POST /api/rooms/{room_id}/consensus/propose
Content-Type: application/json

{
  "question": "Should we deploy v1.16.0?"
}

GET /api/rooms/{room_id}/consensus
```

---

## Peer Discovery Protocol

### Tailscale Status Integration

The mesh status endpoint queries the local Tailscale daemon:

```python
res = subprocess.run(
    ["tailscale", "status", "--json"],
    capture_output=True, text=True, timeout=3
)
```

### Peer Response Format

```json
{
  "status": "active",
  "mesh_ip": "100.75.107.79",
  "peers": [
    {
      "hostname": "V2158",
      "ip": "100.104.6.26",
      "online": true,
      "os": "android",
      "direct": true,
      "route": "tailscale-wireguard",
      "last_handshake": 1730000000
    }
  ],
  "count": 1
}
```

### Discovery Flow

```
Client Request          TALOS Server              Tailscale Daemon
      │                       │                          │
      │  GET /api/mesh/       │                          │
      │       /peers/discover │                          │
      │──────────────────────►│                          │
      │                       │  tailscale status --json │
      │                       │─────────────────────────►│
      │                       │                          │
      │                       │◄─────────────────────────│
      │                       │  peer list + IPs         │
      │                       │                          │
      │◄──── 200 OK + peers ──│                          │
```

### Security Considerations

- Mesh discovery requires **no authentication** (public interface)
- Token pairing is required for protected endpoints
- Each peer is identified by hostname and Tailscale IP
- Route verification confirms direct WireGuard connectivity

---

## Dynamic Subagent Summoning & Dismissal Protocol

### Turn Lifecycle

The orchestrator manages agent participation through a strict lifecycle:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  TURN_START │────►│  TYPING     │────►│  THOUGHT    │
│             │     │  INDICATOR  │     │  STREAM     │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  TURN_END   │◄────│  MESSAGE    │◄────│  TOKEN      │
│             │     │  PERSIST    │     │  STREAM     │
└─────────────┘     └─────────────┘     └─────────────┘
```

### Event Sequence

Each agent turn emits events in this exact order:

1. **`turn_start`** - Agent lock acquired, turn begins
2. **`typing_indicator`** (true) - Agent is composing
3. **`thought_delta`** chunks - Internal reasoning stream
4. **`typing_indicator`** (false) - Thinking complete
5. **`token_delta`** chunks - Visible reply stream
6. **`message_new`** - Persisted final message
7. **`turn_end`** - Turn complete, lock released

### Orchestration Rules

```python
# core/agent_orchestrator.py

#: Messages from agents do not trigger further turns (prevents loops)
if message.sender_role in (Role.agent, Role.orchestrator, Role.system):
    return []

#: Explicit mentions override room defaults
mentioned = _mentioned_agents(message.content)

if mentioned:
    # Summon only mentioned agents, in canonical order
    targets = [a for a in self.agent_order if a in mentioned]
elif room_id == COUNCIL_ROOM_ID:
    # Unaddressed council messages summon the full bench
    targets = list(self.agent_order)
else:
    # DMs and other rooms: no auto-summon
    targets = []

#: Cap at MAX_TURNS_PER_ROUND
return targets[: MAX_TURNS_PER_ROUND]  # max 4
```

### Summoning Algorithm

```python
def _resolve_targets(self, message: Message, room_id: str) -> List[str]:
    """Determine which agents should answer."""
    
    # Anti-loop: ignore agent-to-agent messages
    if message.sender_role in (Role.agent, ...):
        return []
    
    # Check explicit mentions
    mentioned = _mentioned_agents(message.content)
    
    if mentioned:
        # Summon only mentioned agents, in canonical order
        targets = [a for a in self.agent_order if a in mentioned]
    elif room_id == COUNCIL_ROOM_ID:
        # Unaddressed council messages summon the full bench
        targets = list(self.agent_order)
    else:
        # DMs and other rooms: no auto-summon
        targets = []
    
    # Cap at MAX_TURNS_PER_ROUND
    return targets[: self.max_turns_per_round]
```

### Round Execution

```python
async def _run_round(self, round_: _Round) -> None:
    """Execute sequential turns within a single round."""
    lock = self._room_lock(round_.room_id)
    
    async with lock:  # Per-room serialization
        context = []  # Previous turns in this round
        for index, agent_id in enumerate(round_.agents):
            agent = self.agents.get(agent_id)
            reply = await self._run_turn(agent, round_, index, context)
            
            if reply:
                # Build context for next agent
                context.append({
                    "agent_id": agent.id,
                    "agent_name": agent.name,
                    "content": reply,
                })
                
                # Stagger before next turn
                if index < len(round_.agents) - 1:
                    await asyncio.sleep(self.turn_stagger)
```

### Context Window Management

```python
CONTEXT_WINDOW = 8  # Recent messages as prompt context

def _record_history(self, message: Message, room_id: str) -> None:
    history = self._history.setdefault(room_id, [])
    history.append({
        "agent_id": message.sender_id,
        "agent_name": message.sender_name,
        "content": _summarise(message.content),  # 140 char truncation
    })
    # Keep window bounded
    if len(history) > self.context_window * 2:
        del history[: len(history) - self.context_window * 2]
```

### Dismissal Behavior

Agents are automatically dismissed when:
1. Turn completes (normal flow)
2. asyncio.CancelledError is raised
3. Exception occurs during turn execution

The orchestrator tracks active turns internally:
```python
self._active_turns: Dict[str, _Turn] = {}
self._rounds: Dict[str, _Round] = {}
```

---

## Event Bus & Message Flow

### Bus Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      RoomEventBus                               │
│                                                                 │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐   │
│   │  Subscribers │     │ Agent List.  │     │ Binary List. │   │
│   │  (WebSockets)│     │  (Async cb)  │     │  (Audio)     │   │
│   └──────┬───────┘     └──────┬───────┘     └──────┬───────┘   │
│          │                    │                     │            │
│          └────────────────────┼─────────────────────┘            │
│                               ▼                                  │
│                    ┌─────────────────────┐                       │
│                    │    Broadcast Engine  │                       │
│                    │  • fan-out to WS     │                       │
│                    │  • async dispatch    │                       │
│                    │  • dead socket cleanup│                      │
│                    └─────────────────────┘                       │
└─────────────────────────────────────────────────────────────────┘
```

### Event Types

```python
class EventType(str, Enum):
    message_new = "message_new"           # New persisted message
    token_delta = "token_delta"           # Streaming text chunk
    thought_delta = "thought_delta"       # Streaming reasoning chunk
    tool_call_start = "tool_call_start"   # Tool execution begun
    tool_call_end = "tool_call_end"       # Tool execution finished
    typing_indicator = "typing_indicator" # Agent typing state
    room_sync = "room_sync"               # Client connection established
    agent_reaction = "agent_reaction"     # Agent reaction emoji/event
    turn_start = "turn_start"             # Agent turn begins
    turn_end = "turn_end"                 # Agent turn completes
    voice_chunk = "voice_chunk"           # Audio streaming chunk
    binary_frame = "binary_frame"         # Raw binary data
```

### Message Flow Diagram

```
┌─────────┐      ┌──────────┐      ┌────────────┐      ┌──────────┐
│  Human  │      │  TALOS   │      │ Orchestrator│      │  Agents  │
│  Client │      │  Server  │      │            │      │ (Bench)  │
└────┬────┘      └────┬─────┘      └─────┬──────┘      └────┬─────┘
     │                │                   │                   │
     │ POST /api/     │                   │                   │
     │ messages       │                   │                   │
     │───────────────────────►│                   │                   │
     │                │                   │                   │
     │                │ Save to SQLite    │                   │
     │                │ Dispatch to bus   │                   │
     │                │──────────────────────────────────────►│
     │                │                   │                   │
     │                │                   │ Filter mentions   │
     │                │                   │ Resolve targets   │
     │                │                   │ Schedule turns    │
     │                │                   │                   │
     │◄── 200 OK +    │                   │                   │
     │   message id   │                   │                   │
     │                │                   │                   │
     │                │◄── turn_start event ──────────────────│
     │                │                   │                   │
     │                │                   │                   │
     │◄── typing_indicator ────────────────│                   │
     │                │                   │                   │
     │◄── thought_delta × N ──────────────│                   │
     │                │                   │                   │
     │                │                   │                   │
     │◄── token_delta × N ────────────────│                   │
     │                │                   │                   │
     │                │                   │                   │
     │◄── message_new (final) ────────────│                   │
     │                │                   │                   │
     │                │◄── turn_end ──────────────────────────│
```

---

## Provider Failover System

### Health Probing

The provider service continuously probes registered model providers:

| Provider | Base URL | API Style | Context Length |
|----------|----------|-----------|----------------|
| Hermes | `https://inference-api.nousresearch.com/v1` | OpenAI | 131072 |
| Anthropic | `https://api.anthropic.com` | Anthropic | 200000 |
| OpenAI | `https://api.openai.com/v1` | OpenAI | 400000 |
| Gemini | `https://generativelanguage.googleapis.com/v1beta` | Gemini | 1048576 |
| DeepSeek | `https://api.deepseek.com/v1` | OpenAI | 128000 |
| Mistral | `https://api.mistral.ai/v1` | OpenAI | 128000 |
| Qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI | 131072 |

### Status Classification

```python
class ProviderStatus(str, Enum):
    operational = "operational"      # Healthy, responding
    degraded = "degraded"            # Slow or error responses
    rate_limited = "rate_limited"    # 429 received
    unauthorized = "unauthorized"    # 401/403 received
    unconfigured = "unconfigured"    # No credential found
    unreachable = "unreachable"      # Connection failure
    unknown = "unknown"              # Initial or disabled state
```

### Failover Routing

```python
async def benchmark_and_route(self) -> FailoverRouteResponse:
    """Rank providers by status and latency."""
    ranked = sorted(infos, key=lambda info: (
        0 if info.status == ProviderStatus.operational else 1,
        info.latency_ms or 999999
    ))
    
    failover_chain = [info.id for info in ranked 
                      if info.status in ("operational", "degraded")]
    
    return FailoverRouteResponse(
        primary_provider=failover_chain[0],
        failover_chain=failover_chain,
        rankings=rankings,
    )
```

---

## Security Architecture

### Authentication Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────►│  TALOS API  │────►│  SQLite     │
│   (PWA/CLI) │     │  Server     │     │  Token DB   │
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │                    │
       │  1. Pair request  │                    │
       │──────────────────►│                    │
       │                   │   2. Verify mesh   │
       │                   │───────────────────►│
       │                   │                    │
       │                   │   3. Create token  │
       │                   │◄───────────────────│
       │◄── 4. Token + mesh info ───────────────│
       │    (talos_sk_...) │                    │
       │                   │                    │
       │  5. Bearer auth on │                    │
       │     subsequent req │                    │
       │──────────────────►│                    │
       │                   │   6. Verify token  │
       │                   │───────────────────►│
       │◄──── 7. 200 OK     ────────────────────│
```

### Token Lifecycle

1. **Creation**: `secrets.token_hex(24)` prefixed with `talos_sk_`
2. **Storage**: SQLite `api_tokens` table with masked preview
3. **Verification**: Hash check against stored token, expiry validation
4. **Revocation**: Soft delete via `is_revoked` flag (master token protected)
5. **Audit**: `last_used_at` and `use_count` tracking

### Protected Routes

All routes except mesh status and pairing require Bearer authentication:

```python
async def require_auth(authorization: str = Header(None)) -> str:
    if not authorization or not verify_token(authorization):
        raise HTTPException(status_code=401)
    return authorization.replace("Bearer ", "").strip()
```

---

## API Reference

### Mesh Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/mesh/status` | Current Tailscale node info |
| GET | `/api/mesh/peers/discover` | Reachable mesh peers |
| POST | `/api/auth/pair` | One-tap device pairing |

### Room Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/rooms` | List all rooms |
| GET | `/api/rooms/{id}` | Get room details |
| POST | `/api/rooms` | Create new room |
| PUT | `/api/rooms/{id}` | Update room |
| DELETE | `/api/rooms/{id}` | Delete room |
| GET | `/api/rooms/{id}/messages` | Get room messages |
| POST | `/api/rooms/{id}/messages` | Send message |
| GET | `/api/rooms/{id}/agents` | Get room agent bench |
| PUT | `/api/rooms/{id}/agents` | Update agent bench |

### Consensus Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/rooms/{id}/consensus/propose` | Create voting proposal |
| GET | `/api/rooms/{id}/consensus` | Get proposal history |

### Provider Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/providers` | All provider health |
| GET | `/api/providers/summary` | Aggregate stats |
| GET | `/api/providers/health` | Real-time health |
| GET | `/api/providers/{id}` | Single provider info |
| POST | `/api/providers/benchmark` | Run failover benchmark |
| GET | `/api/providers/failover` | Current failover route |

### Orchestrator Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/orchestrator/status` | Live turn status |

---

## Repository Structure

```
talos/
├── core/
│   ├── consensus.py        # Voting protocol implementation
│   ├── models.py           # Pydantic data models
│   ├── providers.py        # Provider catalog & credential resolution
│   └── room_bus.py         # WebSocket event bus
├── server/
│   ├── main.py             # FastAPI application & routes
│   ├── auth.py             # Token management & verification
│   ├── db.py               # SQLite persistence layer
│   ├── provider_service.py # Live provider health probing
│   └── agent_orchestrator.py  # Turn scheduling & streaming
├── tests/
│   ├── test_consensus.py   # Consensus protocol tests
│   ├── test_e2e_mesh.py    # Mesh & orchestration E2E tests
│   └── test_orchestrator.py  # Orchestrator unit tests
├── web/                    # React/Vite PWA frontend
├── android/                # Native Android client
├── docs/
│   └── MULTI_AGENT_MESH_ARCHITECTURE.md  # This document
└── README.md
```

---

## Design Decisions

### Why Simulated Agents?

The current implementation uses deterministic simulated agents for the chamber. This ensures:

1. **Demonstrability** - Works offline without external API keys
2. **Reproducibility** - Same input always produces same output
3. **Speed** - No network latency in turn streaming
4. **Testing** - Full integration test coverage without mock providers

### Why Tailscale Over Custom WireGuard?

1. **Zero-config NAT traversal** - No port forwarding required
2. **Automatic key rotation** - Ephemeral WireGuard keys per session
3. **MagicDNS** - Hostname-based peer discovery
4. **Control plane** - Centralized access policy management

### Why Async Locks per Room?

Per-room locking ensures:
- No two rounds interleave their event streams
- Agents see complete previous turns in context
- Streaming deltas arrive in order to clients
- SQLite writes remain consistent

---

## Future Extensions

- [ ] Real agent integration (live Codex/Claude Code calls)
- [ ] Multi-server mesh coordination
- [ ] Consensus with Byzantine fault tolerance
- [ ] Agent skill marketplace
- [ ] Cross-mesh room federation

---

*Document generated from source code analysis. All architectural claims are grounded in the actual implementation in `core/` and `server/` modules.*
