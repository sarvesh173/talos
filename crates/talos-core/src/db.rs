use crate::models::*;
use rusqlite::{params, Connection, Result};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

pub struct Database {
    conn: Arc<Mutex<Connection>>,
}

impl Database {
    pub fn new(path: &str) -> Result<Self> {
        let conn = Connection::open(path)?;
        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.init_schema()?;
        db.seed_defaults()?;
        Ok(db)
    }

    pub fn new_in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.init_schema()?;
        db.seed_defaults()?;
        Ok(db)
    }

    fn init_schema(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute_batch(
            r#"
            CREATE TABLE IF NOT EXISTS rooms (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                mode TEXT NOT NULL,
                participants_json TEXT NOT NULL,
                agent_ids_json TEXT NOT NULL,
                created_at REAL NOT NULL,
                updated_at REAL NOT NULL
            );

            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                room_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                sender_name TEXT NOT NULL,
                sender_role TEXT NOT NULL,
                content TEXT NOT NULL,
                thought TEXT,
                tool_calls_json TEXT NOT NULL,
                attachments_json TEXT NOT NULL,
                reply_to_id TEXT,
                created_at REAL NOT NULL
            );

            CREATE TABLE IF NOT EXISTS tokens (
                token_id TEXT PRIMARY KEY,
                token_hash TEXT NOT NULL,
                masked_token TEXT NOT NULL,
                label TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at REAL NOT NULL,
                last_used_at REAL,
                use_count INTEGER NOT NULL DEFAULT 0,
                is_revoked INTEGER NOT NULL DEFAULT 0,
                is_master INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS consensus_proposals (
                id TEXT PRIMARY KEY,
                room_id TEXT NOT NULL,
                question TEXT NOT NULL,
                votes_json TEXT NOT NULL,
                outcome TEXT NOT NULL,
                consensus_ratio REAL NOT NULL,
                status TEXT NOT NULL,
                created_at REAL NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_messages_room ON messages(room_id, created_at);
            CREATE INDEX IF NOT EXISTS idx_tokens_hash ON tokens(token_hash);
            "#,
        )?;
        Ok(())
    }

    fn seed_defaults(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();

        // Seed Default Rooms if empty
        let count: i64 = conn.query_row("SELECT count(*) FROM rooms", [], |r| r.get(0))?;
        if count == 0 {
            let now = now_ts();
            let default_rooms = [
                (
                    "the-council",
                    "The Council",
                    "Multi-agent democratic deliberation & strategic planning",
                    "group",
                    r##"[{"id":"alya","name":"Alya","avatar":"","color":"#D0BCFF","model":"gemini-3.8-flash","role_description":"Strategic Leader & Anchor","system_prompt":"","is_active":true},{"id":"atlas","name":"Atlas","avatar":"","color":"#38BDF8","model":"codex-cli","role_description":"Systems Architect","system_prompt":"","is_active":true},{"id":"sentinel","name":"Sentinel","avatar":"","color":"#10B981","model":"security-auditor","role_description":"Security & Consensus Guard","system_prompt":"","is_active":true}]"##,
                    r##"["alya","atlas","sentinel"]"##
                ),
                (
                    "war-room",
                    "War Room",
                    "Zero-latency crisis response & mesh diagnostic quorum",
                    "group",
                    r##"[{"id":"sentinel","name":"Sentinel","avatar":"","color":"#EF4444","model":"security-auditor","role_description":"Incident Commander","system_prompt":"","is_active":true}]"##,
                    r##"["sentinel"]"##
                ),
                (
                    "direct-comms",
                    "Direct Comms",
                    "Private peer communication link with primary anchor",
                    "dm",
                    r##"[{"id":"alya","name":"Alya","avatar":"","color":"#D0BCFF","model":"gemini-3.8-flash","role_description":"Personal Anchor","system_prompt":"","is_active":true}]"##,
                    r##"["alya"]"##
                )
            ];

            for (id, name, desc, mode, part, agents) in default_rooms {
                conn.execute(
                    "INSERT INTO rooms (id, name, description, mode, participants_json, agent_ids_json, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
                    params![id, name, desc, mode, part, agents, now, now],
                )?;
            }

            // Seed initial welcome message in the-council
            conn.execute(
                "INSERT INTO messages (id, room_id, sender_id, sender_name, sender_role, content, thought, tool_calls_json, attachments_json, reply_to_id, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
                params![
                    "msg-init-01",
                    "the-council",
                    "alya",
                    "Alya",
                    "agent",
                    "TALOS M3 Expressive Core initialized. Mesh synchronization verified over WireGuard encrypted conduits. All chambers standing by for multi-agent deliberation.",
                    "Synthesizing network topology and initial quorum parameters. Verified zero-elevation rendering and fluid spring motion physics.",
                    "[]",
                    "[]",
                    Option::<String>::None,
                    now
                ],
            )?;
        }

        // Seed master dev token if tokens table empty
        let token_count: i64 = conn.query_row("SELECT count(*) FROM tokens", [], |r| r.get(0))?;
        if token_count == 0 {
            let now = now_ts();
            // Pre-seed "talos_sk_dev_master_key" hash for instant local access
            let dev_hash = "f34083329e4e69b59e3778a481c00df0b27e4db95bc92040b2a7593282b8812c";
            conn.execute(
                "INSERT INTO tokens (token_id, token_hash, masked_token, label, role, created_at, is_master) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 1)",
                params!["tok-master-01", dev_hash, "talos_sk_dev...001", "Master Mesh Key", "admin", now],
            )?;
        }

        Ok(())
    }

    pub fn get_rooms(&self) -> Result<Vec<Room>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, description, mode, participants_json, agent_ids_json, created_at, updated_at FROM rooms ORDER BY created_at ASC")?;
        let rows = stmt.query_map([], |r| {
            let mode_str: String = r.get(3)?;
            let mode = match mode_str.as_str() {
                "dm" => RoomMode::Dm,
                "moderated" => RoomMode::Moderated,
                _ => RoomMode::Group,
            };
            let part_str: String = r.get(4)?;
            let agents_str: String = r.get(5)?;
            Ok(Room {
                id: r.get(0)?,
                name: r.get(1)?,
                description: r.get(2)?,
                mode,
                participants: serde_json::from_str(&part_str).unwrap_or_default(),
                agent_ids: serde_json::from_str(&agents_str).unwrap_or_default(),
                created_at: r.get(6)?,
                updated_at: r.get(7)?,
            })
        })?;

        let mut rooms = Vec::new();
        for r in rows {
            rooms.push(r?);
        }
        Ok(rooms)
    }

    pub fn get_messages(&self, room_id: &str, limit: usize) -> Result<Vec<Message>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, room_id, sender_id, sender_name, sender_role, content, thought, tool_calls_json, attachments_json, reply_to_id, created_at FROM messages WHERE room_id = ?1 ORDER BY created_at ASC LIMIT ?2"
        )?;

        let rows = stmt.query_map(params![room_id, limit as i64], |r| {
            let role_str: String = r.get(4)?;
            let role = match role_str.as_str() {
                "agent" => Role::Agent,
                "system" => Role::System,
                "orchestrator" => Role::Orchestrator,
                _ => Role::Human,
            };
            let tool_str: String = r.get(7)?;
            let attach_str: String = r.get(8)?;

            Ok(Message {
                id: r.get(0)?,
                room_id: r.get(1)?,
                sender_id: r.get(2)?,
                sender_name: r.get(3)?,
                sender_role: role,
                content: r.get(5)?,
                thought: r.get(6)?,
                tool_calls: serde_json::from_str(&tool_str).unwrap_or_default(),
                attachments: serde_json::from_str(&attach_str).unwrap_or_default(),
                reply_to_id: r.get(9)?,
                created_at: r.get(10)?,
            })
        })?;

        let mut msgs = Vec::new();
        for m in rows {
            msgs.push(m?);
        }
        Ok(msgs)
    }

    pub fn insert_message(&self, msg: &Message) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let tool_calls_json = serde_json::to_string(&msg.tool_calls).unwrap_or_else(|_| "[]".into());
        let attachments_json = serde_json::to_string(&msg.attachments).unwrap_or_else(|_| "[]".into());
        let role_str = match msg.sender_role {
            Role::Agent => "agent",
            Role::System => "system",
            Role::Orchestrator => "orchestrator",
            Role::Human => "human",
        };

        conn.execute(
            "INSERT INTO messages (id, room_id, sender_id, sender_name, sender_role, content, thought, tool_calls_json, attachments_json, reply_to_id, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![
                msg.id,
                msg.room_id,
                msg.sender_id,
                msg.sender_name,
                role_str,
                msg.content,
                msg.thought,
                tool_calls_json,
                attachments_json,
                msg.reply_to_id,
                msg.created_at
            ],
        )?;
        Ok(())
    }

    pub fn get_tokens(&self) -> Result<Vec<TokenInfo>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT token_id, label, role, created_at, last_used_at, use_count, is_revoked, masked_token, is_master FROM tokens ORDER BY created_at DESC"
        )?;

        let rows = stmt.query_map([], |r| {
            Ok(TokenInfo {
                token_id: r.get(0)?,
                label: r.get(1)?,
                role: r.get(2)?,
                created_at: r.get(3)?,
                last_used_at: r.get(4)?,
                use_count: r.get(5)?,
                is_revoked: r.get::<_, i64>(6)? != 0,
                masked_token: r.get(7)?,
                is_master: r.get::<_, i64>(8)? != 0,
            })
        })?;

        let mut tokens = Vec::new();
        for t in rows {
            tokens.push(t?);
        }
        Ok(tokens)
    }

    pub fn insert_token(&self, id: &str, hash: &str, masked: &str, label: &str, role: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let now = now_ts();
        conn.execute(
            "INSERT INTO tokens (token_id, token_hash, masked_token, label, role, created_at, is_revoked, is_master) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0, 0)",
            params![id, hash, masked, label, role, now],
        )?;
        Ok(())
    }

    pub fn revoke_token(&self, id: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("UPDATE tokens SET is_revoked = 1 WHERE token_id = ?1", params![id])?;
        Ok(())
    }

    pub fn verify_token_hash(&self, hash: &str) -> Result<Option<(String, String)>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT token_id, role, is_revoked FROM tokens WHERE token_hash = ?1")?;
        let mut rows = stmt.query(params![hash])?;

        if let Some(row) = rows.next()? {
            let is_revoked: i64 = row.get(2)?;
            if is_revoked == 0 {
                let id: String = row.get(0)?;
                let role: String = row.get(1)?;
                // Update usage
                let now = now_ts();
                let _ = conn.execute(
                    "UPDATE tokens SET last_used_at = ?1, use_count = use_count + 1 WHERE token_id = ?2",
                    params![now, id],
                );
                return Ok(Some((id, role)));
            }
        }
        Ok(None)
    }

    pub fn get_proposals(&self, room_id: &str) -> Result<Vec<ConsensusProposal>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, room_id, question, votes_json, outcome, consensus_ratio, status, created_at FROM consensus_proposals WHERE room_id = ?1 ORDER BY created_at DESC"
        )?;

        let rows = stmt.query_map(params![room_id], |r| {
            let votes_str: String = r.get(3)?;
            Ok(ConsensusProposal {
                id: r.get(0)?,
                room_id: r.get(1)?,
                question: r.get(2)?,
                votes: serde_json::from_str(&votes_str).unwrap_or_default(),
                outcome: r.get(4)?,
                consensus_ratio: r.get(5)?,
                status: r.get(6)?,
                created_at: r.get(7)?,
            })
        })?;

        let mut proposals = Vec::new();
        for p in rows {
            proposals.push(p?);
        }
        Ok(proposals)
    }

    pub fn insert_proposal(&self, p: &ConsensusProposal) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let votes_json = serde_json::to_string(&p.votes).unwrap_or_else(|_| "[]".into());
        conn.execute(
            "INSERT INTO consensus_proposals (id, room_id, question, votes_json, outcome, consensus_ratio, status, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![p.id, p.room_id, p.question, votes_json, p.outcome, p.consensus_ratio, p.status, p.created_at],
        )?;
        Ok(())
    }

    pub fn update_proposal_vote(&self, proposal_id: &str, vote: AgentVote) -> Result<Option<ConsensusProposal>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, room_id, question, votes_json, status, created_at FROM consensus_proposals WHERE id = ?1"
        )?;
        let proposal_opt = stmt.query_row(params![proposal_id], |r| {
            let votes_str: String = r.get(3)?;
            Ok((
                r.get::<_, String>(0)?,
                r.get::<_, String>(1)?,
                r.get::<_, String>(2)?,
                votes_str,
                r.get::<_, String>(4)?,
                r.get::<_, f64>(5)?,
            ))
        });

        if let Ok((id, room_id, question, votes_str, status, created_at)) = proposal_opt {
            let mut votes: Vec<AgentVote> = serde_json::from_str(&votes_str).unwrap_or_default();
            // Replace existing vote if agent already voted
            if let Some(pos) = votes.iter().position(|v| v.agent_id == vote.agent_id) {
                votes[pos] = vote;
            } else {
                votes.push(vote);
            }

            // Calculate consensus outcome
            let total = votes.len();
            let mut approves = 0;
            let mut rejects = 0;
            for v in &votes {
                match v.vote {
                    VoteChoice::Approve => approves += 1,
                    VoteChoice::Reject => rejects += 1,
                    VoteChoice::Abstain => {}
                }
            }

            let ratio = if total > 0 { approves as f64 / total as f64 } else { 0.0 };
            let outcome = if approves > total / 2 {
                "passed"
            } else if rejects >= (total + 1) / 2 {
                "rejected"
            } else if approves == rejects && total >= 2 {
                "tied"
            } else {
                "open"
            };

            let updated_votes = serde_json::to_string(&votes).unwrap_or_else(|_| "[]".into());
            let _ = conn.execute(
                "UPDATE consensus_proposals SET votes_json = ?1, outcome = ?2, consensus_ratio = ?3 WHERE id = ?4",
                params![updated_votes, outcome, ratio, id],
            );

            return Ok(Some(ConsensusProposal {
                id,
                room_id,
                question,
                votes,
                outcome: outcome.to_string(),
                consensus_ratio: ratio,
                status,
                created_at,
            }));
        }

        Ok(None)
    }
}
