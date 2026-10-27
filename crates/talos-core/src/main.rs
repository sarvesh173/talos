mod auth;
mod consensus;
mod db;
mod discovery;
mod mesh;
mod models;
mod providers;
mod room_bus;
mod theme;
mod agents;

use auth::AuthEngine;
use axum::{
    extract::{
        ws::{WebSocket, WebSocketUpgrade},
        Path, Query, State,
    },
    http::StatusCode,
    response::IntoResponse,
    routing::{delete, get, post},
    Json, Router,
};
use consensus::ConsensusEngine;
use db::Database;
use futures_util::{SinkExt, StreamExt};
use mesh::MeshDiscovery;
use models::*;
use providers::ProviderRegistry;
use room_bus::RoomBus;
use serde::Deserialize;
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tower_http::cors::{Any, CorsLayer};
use tracing::info;
use uuid::Uuid;

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

#[derive(Clone)]
pub struct AppState {
    pub db: Arc<Database>,
    pub bus: Arc<RoomBus>,
    pub supervisor: Arc<agents::AgentSupervisor>,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter("info,talos_core=debug")
        .init();

    info!("Initializing TALOS M3 Expressive Core (Rust-First)...");

    let db_path = std::env::var("TALOS_DB_PATH").unwrap_or_else(|_| "talos.db".to_string());
    let db = Arc::new(Database::new(&db_path)?);
    let bus = Arc::new(RoomBus::new());
    let supervisor = Arc::new(agents::AgentSupervisor::new(2));
    supervisor.rescan().await;

    let state = AppState { db, bus, supervisor };

    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    let app = Router::new()
        .route("/health", get(health_check))
        // Mesh & Discovery
        .route("/api/mesh/status", get(mesh_status))
        .route("/api/mesh/peers/discover", get(mesh_peers))
        .route("/api/auth/pair", post(auth_pair))
        // Agent-first pairing: the agent on the host fetches a one-time
        // pairing URI and hands it to the phone (link or QR). Works over
        // the overlay, so it also works when the phone is on mobile data.
        .route("/api/pairing/invite", get(pairing_invite))
        // Auth & Tokens
        .route("/api/tokens", get(list_tokens).post(create_token))
        .route("/api/tokens/:id", delete(revoke_token))
        // Rooms & Deliberation
        .route("/api/rooms", get(list_rooms))
        .route("/api/rooms/:id/messages", get(list_messages).post(post_message))
        // Democratic Consensus
        .route("/api/rooms/:id/consensus", get(list_consensus).post(create_consensus))
        .route("/api/rooms/:id/consensus/:pid/vote", post(cast_vote))
        // Providers & Failover
        .route("/api/providers/health", get(provider_health))
        .route("/api/providers/failover", get(provider_failover))
        // Flexible theme tokens (client applies as ColorScheme overrides)
        .route("/api/theme", get(get_theme).put(put_theme))
        // Dynamic Agent Registry & Process Supervisor
        .route("/api/agents", get(list_agents))
        .route("/api/agents/scan", post(scan_agents))
        .route("/api/agents/register", post(register_custom_agent))
        .route("/api/agents/pool", get(pool_status))
        .route("/api/agents/:id", get(get_agent_detail))
        .route("/api/agents/:id/run", post(run_agent_task))
        .route("/api/agents/:id/setup", post(setup_agent))
        // Realtime WebSocket Room Event Bus
        .route("/ws/:room_id/:client_id", get(ws_handler))
        .layer(cors)
        .with_state(state);

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8000);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    info!("TALOS Core listening on http://{}", addr);

    // Zero-config pairing beacon: lets the app auto-detect this core on the
    // LAN without any IP being typed or displayed.
    tokio::spawn(discovery::run_discovery_responder());

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}

async fn health_check() -> impl IntoResponse {
    Json(serde_json::json!({
        "status": "operational",
        "engine": "talos-core-rust",
        "version": "0.2.0",
        "design_system": "Material 3 Expressive (M3E)",
        "timestamp": now_ts()
    }))
}

async fn mesh_status() -> impl IntoResponse {
    Json(MeshDiscovery::get_node_status())
}

async fn mesh_peers() -> impl IntoResponse {
    Json(MeshDiscovery::discover_peers())
}

/// One-time pairing invite for the Android client.
///
/// The overlay address is read at runtime (never hardcoded) and the URI is
/// handed to the phone by the agent as a link or QR. The phone never types
/// or displays an address.
async fn pairing_invite(State(state): State<AppState>) -> Result<impl IntoResponse, StatusCode> {
    let status = MeshDiscovery::get_node_status();
    let host = if status.tailscale_ip.is_empty() || status.tailscale_ip == "unknown" {
        // No overlay address yet: fall back to loopback so the agent still
        // gets a usable (same-device) invite.
        "127.0.0.1".to_string()
    } else {
        status.tailscale_ip.clone()
    };
    let port = std::env::var("PORT").unwrap_or_else(|_| "8000".to_string());

    let (plain, hash, masked) = AuthEngine::generate_token("Android pairing");
    state
        .db
        .insert_token(&Uuid::new_v4().to_string(), &hash, &masked, "Android pairing", "user")
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let base_url = format!("http://{host}:{port}");
    let uri = format!("talos://pair?url={}&token={}", base_url, plain);
    Ok(Json(serde_json::json!({
        "status": "invite_ready",
        "url": base_url,
        "token": plain,
        "masked_token": masked,
        "uri": uri,
        "overlay": status.tailscale_enabled,
        "hint": "Open this uri on the phone, or render it as a QR. It pairs the app without typing an address."
    })))
}

#[derive(Deserialize)]
struct PairRequest {
    token: Option<String>,
}

async fn auth_pair(
    State(state): State<AppState>,
    Json(req): Json<PairRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if let Some(token) = req.token {
        let hash = AuthEngine::hash_token(&token);
        if let Ok(Some((id, role))) = state.db.verify_token_hash(&hash) {
            return Ok(Json(serde_json::json!({
                "status": "paired",
                "token_id": id,
                "role": role,
                "mesh_verified": true
            })));
        }
    }
    // If no token or invalid, issue a fresh guest session token
    let (plain, hash, masked) = AuthEngine::generate_token("Mesh Pairing Node");
    state
        .db
        .insert_token(&Uuid::new_v4().to_string(), &hash, &masked, "Mesh Pairing Node", "user")
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(serde_json::json!({
        "status": "paired",
        "token": plain,
        "masked_token": masked,
        "role": "user",
        "mesh_verified": true
    })))
}

async fn list_tokens(State(state): State<AppState>) -> Result<impl IntoResponse, StatusCode> {
    let tokens = state.db.get_tokens().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(tokens))
}

#[derive(Deserialize)]
struct CreateTokenRequest {
    label: String,
    role: Option<String>,
}

async fn create_token(
    State(state): State<AppState>,
    Json(req): Json<CreateTokenRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    let (plain, hash, masked) = AuthEngine::generate_token(&req.label);
    let id = format!("tok-{}", &Uuid::new_v4().to_string().replace('-', "")[..8]);
    let role = req.role.unwrap_or_else(|| "user".to_string());

    state
        .db
        .insert_token(&id, &hash, &masked, &req.label, &role)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(serde_json::json!({
        "token_id": id,
        "token": plain,
        "masked_token": masked,
        "label": req.label,
        "role": role,
        "created_at": now_ts()
    })))
}

async fn revoke_token(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<impl IntoResponse, StatusCode> {
    state.db.revoke_token(&id).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(serde_json::json!({ "status": "revoked", "token_id": id })))
}

async fn list_rooms(State(state): State<AppState>) -> Result<impl IntoResponse, StatusCode> {
    let rooms = state.db.get_rooms().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(rooms))
}

#[derive(Deserialize)]
struct MessagesQuery {
    limit: Option<usize>,
}

async fn list_messages(
    State(state): State<AppState>,
    Path(room_id): Path<String>,
    Query(q): Query<MessagesQuery>,
) -> Result<impl IntoResponse, StatusCode> {
    let limit = q.limit.unwrap_or(100);
    let msgs = state
        .db
        .get_messages(&room_id, limit)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(msgs))
}

#[derive(Deserialize)]
struct PostMessageRequest {
    sender_name: Option<String>,
    content: String,
    thought: Option<String>,
    reply_to_id: Option<String>,
}

async fn post_message(
    State(state): State<AppState>,
    Path(room_id): Path<String>,
    Json(req): Json<PostMessageRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    let msg = Message {
        id: Uuid::new_v4().to_string(),
        room_id,
        sender_id: "human-01".to_string(),
        sender_name: req.sender_name.unwrap_or_else(|| "Operator".to_string()),
        sender_role: Role::Human,
        content: req.content,
        thought: req.thought,
        tool_calls: Vec::new(),
        attachments: Vec::new(),
        reply_to_id: req.reply_to_id,
        created_at: now_ts(),
    };

    state.db.insert_message(&msg).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    state.bus.broadcast_message(&msg);

    Ok(Json(msg))
}

async fn list_consensus(
    State(state): State<AppState>,
    Path(room_id): Path<String>,
) -> Result<impl IntoResponse, StatusCode> {
    let proposals = state
        .db
        .get_proposals(&room_id)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(proposals))
}

#[derive(Deserialize)]
struct CreateConsensusRequest {
    question: String,
}

async fn create_consensus(
    State(state): State<AppState>,
    Path(room_id): Path<String>,
    Json(req): Json<CreateConsensusRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    let proposal = ConsensusEngine::create_proposal(&room_id, &req.question);
    state.db.insert_proposal(&proposal).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(proposal))
}

#[derive(Deserialize)]
struct VoteRequest {
    agent_id: String,
    agent_name: String,
    vote: VoteChoice,
    rationale: String,
    confidence: Option<f64>,
}

async fn cast_vote(
    State(state): State<AppState>,
    Path((_room_id, proposal_id)): Path<(String, String)>,
    Json(req): Json<VoteRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    let vote = AgentVote {
        agent_id: req.agent_id,
        agent_name: req.agent_name,
        vote: req.vote,
        rationale: req.rationale,
        confidence: req.confidence.unwrap_or(1.0),
    };

    let updated = state
        .db
        .update_proposal_vote(&proposal_id, vote)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
        .ok_or(StatusCode::NOT_FOUND)?;

    Ok(Json(updated))
}

async fn provider_health() -> impl IntoResponse {
    Json(ProviderRegistry::get_default_providers())
}

async fn provider_failover() -> impl IntoResponse {
    Json(ProviderRegistry::get_failover_route())
}

/// Serve the active theme config from the database.
async fn get_theme(State(state): State<AppState>) -> impl IntoResponse {
    Json(state.db.get_theme())
}

/// Replace the theme config. Validates hex roles before persisting.
async fn put_theme(
    State(state): State<AppState>,
    Json(cfg): Json<crate::theme::ThemeConfig>,
) -> impl IntoResponse {
    if let Err(problem) = cfg.validate() {
        return (
            axum::http::StatusCode::UNPROCESSABLE_ENTITY,
            Json(serde_json::json!({ "error": problem })),
        );
    }
    match state.db.save_theme(&cfg) {
        Ok(()) => (
            axum::http::StatusCode::OK,
            Json(serde_json::json!({ "status": "theme_updated", "name": cfg.name })),
        ),
        Err(e) => (
            axum::http::StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::json!({ "error": e.to_string() })),
        ),
    }
}

// -----------------------------------------------------------------------------
// Realtime WebSocket Handler
// -----------------------------------------------------------------------------
#[allow(dead_code)]
#[derive(Deserialize)]
struct WsQuery {
    token: Option<String>,
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    Path((room_id, client_id)): Path<(String, String)>,
    Query(_query): Query<WsQuery>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, room_id, client_id, state))
}

async fn handle_socket(socket: WebSocket, room_id: String, client_id: String, state: AppState) {
    let (mut sender, mut receiver) = socket.split();
    let channel = state.bus.get_or_create_channel(&room_id);
    let mut rx = channel.sender.subscribe();

    info!("WebSocket client connected: {} (room: {})", client_id, room_id);

    // Forward broadcast messages to this client
    let mut send_task = tokio::spawn(async move {
        while let Ok(msg_text) = rx.recv().await {
            if sender.send(axum::extract::ws::Message::Text(msg_text)).await.is_err() {
                break;
            }
        }
    });

    // Receive messages from this client
    let bus_clone = state.bus.clone();
    let db_clone = state.db.clone();
    let r_id = room_id.clone();
    let c_id = client_id.clone();

    let mut recv_task = tokio::spawn(async move {
        while let Some(Ok(ws_msg)) = receiver.next().await {
            if let axum::extract::ws::Message::Text(text) = ws_msg {
                if let Ok(v) = serde_json::from_str::<serde_json::Value>(&text) {
                    let msg_type = v.get("type").and_then(|t| t.as_str()).unwrap_or("message");
                    match msg_type {
                        "message" => {
                            let content = v.get("content").and_then(|c| c.as_str()).unwrap_or("");
                            let sender_name = v.get("sender_name").and_then(|s| s.as_str()).unwrap_or("Operator");
                            let msg = Message {
                                id: Uuid::new_v4().to_string(),
                                room_id: r_id.clone(),
                                sender_id: c_id.clone(),
                                sender_name: sender_name.to_string(),
                                sender_role: Role::Human,
                                content: content.to_string(),
                                thought: None,
                                tool_calls: Vec::new(),
                                attachments: Vec::new(),
                                reply_to_id: None,
                                created_at: now_ts(),
                            };
                            let _ = db_clone.insert_message(&msg);
                            bus_clone.broadcast_message(&msg);
                        }
                        "typing" => {
                            let is_typing = v.get("is_typing").and_then(|b| b.as_bool()).unwrap_or(false);
                            bus_clone.broadcast_typing(&r_id, &c_id, is_typing);
                        }
                        _ => {}
                    }
                }
            }
        }
    });

    tokio::select! {
        _ = (&mut send_task) => recv_task.abort(),
        _ = (&mut recv_task) => send_task.abort(),
    };

    info!("WebSocket client disconnected: {} (room: {})", client_id, room_id);
}


// ---------------------------------------------------------
// Dynamic Agent Registry & Process Supervisor Handlers
// ---------------------------------------------------------

async fn list_agents(State(state): State<AppState>) -> Json<Vec<agents::AgentInfo>> {
    Json(state.supervisor.list_agents().await)
}

async fn scan_agents(State(state): State<AppState>) -> Json<Vec<agents::AgentInfo>> {
    state.supervisor.rescan().await;
    Json(state.supervisor.list_agents().await)
}

async fn register_custom_agent(
    State(state): State<AppState>,
    Json(manifest): Json<agents::CustomAgentManifest>,
) -> Result<Json<agents::AgentInfo>, (StatusCode, String)> {
    state
        .supervisor
        .register_custom(manifest)
        .await
        .map(Json)
        .map_err(|e| (StatusCode::BAD_REQUEST, e))
}

async fn pool_status(State(state): State<AppState>) -> Json<agents::PoolStatus> {
    Json(state.supervisor.get_pool_status().await)
}

async fn get_agent_detail(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<agents::AgentInfo>, StatusCode> {
    state
        .supervisor
        .get_agent(&id)
        .await
        .map(Json)
        .ok_or(StatusCode::NOT_FOUND)
}

async fn run_agent_task(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(req): Json<agents::AgentRunRequest>,
) -> Result<Json<agents::AgentRunResult>, (StatusCode, String)> {
    state
        .supervisor
        .execute_task(&id, req)
        .await
        .map(Json)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e))
}

async fn setup_agent(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(req): Json<agents::AgentSetupRequest>,
) -> Result<Json<agents::AgentSetupResult>, (StatusCode, String)> {
    state
        .supervisor
        .run_setup(&id, req)
        .await
        .map(Json)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e))
}
