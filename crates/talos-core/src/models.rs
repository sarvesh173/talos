use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Role {
    Human,
    Agent,
    System,
    Orchestrator,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RoomMode {
    Dm,
    Group,
    Moderated,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum EventType {
    MessageNew,
    TokenDelta,
    ThoughtDelta,
    ToolCallStart,
    ToolCallEnd,
    TypingIndicator,
    RoomSync,
    AgentReaction,
    TurnStart,
    TurnEnd,
    VoiceChunk,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub call_id: String,
    pub tool_name: String,
    pub arguments: serde_json::Value,
    pub output: Option<String>,
    pub status: String, // running, success, failed
    pub started_at: f64,
    pub finished_at: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentProfile {
    pub id: String,
    pub name: String,
    pub avatar: Option<String>,
    pub color: String,
    pub model: Option<String>,
    pub role_description: Option<String>,
    pub system_prompt: Option<String>,
    pub is_active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub room_id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub sender_role: Role,
    pub content: String,
    pub thought: Option<String>,
    pub tool_calls: Vec<ToolCall>,
    pub attachments: Vec<String>,
    pub reply_to_id: Option<String>,
    pub created_at: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Room {
    pub id: String,
    pub name: String,
    pub description: String,
    pub mode: RoomMode,
    pub participants: Vec<AgentProfile>,
    pub agent_ids: Vec<String>,
    pub created_at: f64,
    pub updated_at: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BusEvent {
    pub event_id: String,
    pub event_type: EventType,
    pub room_id: String,
    pub payload: serde_json::Value,
    pub timestamp: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum VoteChoice {
    Approve,
    Reject,
    Abstain,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentVote {
    pub agent_id: String,
    pub agent_name: String,
    pub vote: VoteChoice,
    pub rationale: String,
    pub confidence: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConsensusProposal {
    pub id: String,
    pub room_id: String,
    pub question: String,
    pub votes: Vec<AgentVote>,
    pub outcome: String, // "open", "passed", "rejected", "tied"
    pub consensus_ratio: f64,
    pub status: String,  // "open", "resolved"
    pub created_at: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ProviderStatus {
    Operational,
    Degraded,
    RateLimited,
    Unauthorized,
    Unconfigured,
    Unreachable,
    Unknown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderModel {
    pub id: String,
    pub context_length: Option<usize>,
    pub context_source: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderInfo {
    pub id: String,
    pub name: String,
    pub vendor: String,
    pub base_url: String,
    pub default_model: String,
    pub status: ProviderStatus,
    pub latency_ms: Option<f64>,
    pub context_length: Option<usize>,
    pub models: Vec<ProviderModel>,
    pub models_count: usize,
    pub checked_at: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderRanking {
    pub id: String,
    pub name: String,
    pub latency_ms: Option<f64>,
    pub status: String,
    pub tier: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FailoverRouteResponse {
    pub primary_provider: String,
    pub failover_chain: Vec<String>,
    pub rankings: Vec<ProviderRanking>,
    pub benchmark_timestamp: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenInfo {
    pub token_id: String,
    pub label: String,
    pub role: String,
    pub created_at: f64,
    pub last_used_at: Option<f64>,
    pub use_count: u64,
    pub is_revoked: bool,
    pub masked_token: String,
    pub is_master: bool,
}

/// Mesh state exposed to clients.
///
/// Privacy rule: the node's own identity (node id, hostname, overlay IP) is
/// NEVER serialized. Clients only need to know whether the overlay is up and
/// how many nodes are reachable - host identity is private information and
/// must not leak into another app's UI.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MeshNodeStatus {
    #[serde(skip_serializing)]
    pub node_id: String,
    #[serde(skip_serializing)]
    pub hostname: String,
    #[serde(skip_serializing)]
    pub tailscale_ip: String,
    pub is_online: bool,
    pub wireguard_active: bool,
    /// Single boolean the clients use: the node is reachable AND the overlay
    /// is up. Without it clients had to guess from two fields and guessed
    /// wrong (the app read a field the core never sent).
    pub active: bool,
    /// True only when a live Tailscale tunnel exists on this host.
    /// The app refuses to run without it (the overlay carries the encryption
    /// layer), so this must reflect reality - never a hardcoded assumption.
    pub tailscale_enabled: bool,
    pub peers_count: usize,
    pub last_handshake: f64,
}

/// A reachable peer. Identity fields are never serialized: no names, no
/// device models, no overlay IPs - a client only learns that N nodes exist.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MeshPeer {
    #[serde(skip_serializing)]
    pub id: String,
    #[serde(skip_serializing)]
    pub name: String,
    #[serde(skip_serializing)]
    pub ip: String,
    #[serde(skip_serializing)]
    pub os: String,
    pub status: String,
    pub latency_ms: f64,
}
