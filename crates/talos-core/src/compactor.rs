use serde::{Deserialize, Serialize};

/// Role of message sender in room interactions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SenderRole {
    Human,
    Agent,
    System,
    Orchestrator,
}

impl Default for SenderRole {
    fn default() -> Self {
        Self::Agent
    }
}

/// Representation of an active task obligation extracted or retained during compaction.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TaskObligation {
    pub id: String,
    pub description: String,
    pub assignee: Option<String>,
    pub status: String,
}

/// Representation of an agreed decision preserved during compaction.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DecisionEntry {
    pub topic: String,
    pub decision: String,
    pub decider: Option<String>,
}

/// Key participant summary stats preserved across compacted history.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ParticipantSummary {
    pub name: String,
    pub role: SenderRole,
    pub message_count: usize,
}

/// Structured snapshot summary preserving context, decisions, and active obligations.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CompactSnapshot {
    pub summary_text: String,
    pub active_obligations: Vec<TaskObligation>,
    pub preserved_decisions: Vec<DecisionEntry>,
    pub participant_stats: Vec<ParticipantSummary>,
    pub compacted_message_count: usize,
    pub tokens_before: usize,
    pub tokens_after: usize,
}

/// Compaction configuration parameters.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CompactorConfig {
    /// Context saturation threshold percentage (e.g. 80.0 for 80%) to trigger compaction.
    pub threshold_pct: f32,
    /// Maximum context window token capacity (e.g. 128_000, 1_000_000).
    pub max_context_tokens: usize,
    /// Number of recent turns to always keep verbatim.
    pub keep_recent_turns: usize,
    /// Target percentage of max capacity to reach after compaction (e.g. 50.0%).
    pub target_pct: f32,
}

impl Default for CompactorConfig {
    fn default() -> Self {
        Self {
            threshold_pct: 80.0,
            max_context_tokens: 128_000,
            keep_recent_turns: 4,
            target_pct: 50.0,
        }
    }
}

impl CompactorConfig {
    pub fn new(threshold_pct: f32, max_context_tokens: usize, keep_recent_turns: usize) -> Self {
        Self {
            threshold_pct,
            max_context_tokens,
            keep_recent_turns,
            target_pct: 50.0,
        }
    }

    /// Evaluates if current token count exceeds threshold.
    pub fn is_threshold_exceeded(&self, current_tokens: usize) -> bool {
        if self.max_context_tokens == 0 {
            return false;
        }
        let usage_pct = (current_tokens as f64 / self.max_context_tokens as f64) * 100.0;
        usage_pct >= self.threshold_pct as f64
    }
}

/// A compactable turn or message item for token estimation and summarization.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CompactableMessage {
    pub id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub role: SenderRole,
    pub content: String,
    pub thought: Option<String>,
    pub tool_calls_summary: Option<String>,
    pub timestamp: f64,
}

impl CompactableMessage {
    pub fn new(
        id: impl Into<String>,
        sender_id: impl Into<String>,
        sender_name: impl Into<String>,
        role: SenderRole,
        content: impl Into<String>,
    ) -> Self {
        Self {
            id: id.into(),
            sender_id: sender_id.into(),
            sender_name: sender_name.into(),
            role,
            content: content.into(),
            thought: None,
            tool_calls_summary: None,
            timestamp: 0.0,
        }
    }

    /// Estimates token count of this message using standard ~4 chars/token heuristic.
    pub fn estimate_tokens(&self) -> usize {
        let mut chars = self.sender_name.len() + self.content.len();
        if let Some(t) = &self.thought {
            chars += t.len();
        }
        if let Some(tc) = &self.tool_calls_summary {
            chars += tc.len();
        }
        // Baseline metadata token overhead per turn (role, framing, headers)
        let token_estimate = (chars + 3) / 4 + 4;
        token_estimate
    }
}

/// Result of a compaction operation.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CompactionResult {
    /// True if compaction was triggered and performed.
    pub compacted: bool,
    /// Total estimated tokens before compaction.
    pub tokens_before: usize,
    /// Total estimated tokens after compaction.
    pub tokens_after: usize,
    /// Number of messages compacted into snapshot.
    pub messages_compacted: usize,
    /// Structured snapshot summary preserving obligations & decisions.
    pub snapshot: Option<CompactSnapshot>,
    /// Retained verbatim recent messages.
    pub retained_messages: Vec<CompactableMessage>,
}

/// Autonomous Context Compactor for room message history.
#[derive(Debug, Clone)]
pub struct ContextCompactor {
    pub config: CompactorConfig,
}

impl ContextCompactor {
    pub fn new(config: CompactorConfig) -> Self {
        Self { config }
    }

    pub fn with_default_config() -> Self {
        Self::new(CompactorConfig::default())
    }

    /// Estimates total tokens across a slice of messages.
    pub fn estimate_total_tokens(messages: &[CompactableMessage]) -> usize {
        messages.iter().map(|m| m.estimate_tokens()).sum()
    }

    /// Evaluates whether context exceeds configured threshold (e.g. 80%).
    pub fn should_compact(&self, messages: &[CompactableMessage]) -> bool {
        let total_tokens = Self::estimate_total_tokens(messages);
        self.config.is_threshold_exceeded(total_tokens)
    }

    /// Compacts older message turns when context exceeds threshold, preserving active task
    /// obligations and decisions, returning structured snapshot and retained recent turns.
    pub fn compact_history(&self, messages: &[CompactableMessage]) -> CompactionResult {
        let total_tokens = Self::estimate_total_tokens(messages);

        if !self.config.is_threshold_exceeded(total_tokens) {
            return CompactionResult {
                compacted: false,
                tokens_before: total_tokens,
                tokens_after: total_tokens,
                messages_compacted: 0,
                snapshot: None,
                retained_messages: messages.to_vec(),
            };
        }

        // If message count is less than or equal to keep_recent_turns, cannot compact older turns
        if messages.len() <= self.config.keep_recent_turns {
            return CompactionResult {
                compacted: false,
                tokens_before: total_tokens,
                tokens_after: total_tokens,
                messages_compacted: 0,
                snapshot: None,
                retained_messages: messages.to_vec(),
            };
        }

        // Split history into older turns (to compact) and recent turns (to preserve verbatim)
        let split_idx = messages.len().saturating_sub(self.config.keep_recent_turns);
        let (older_turns, recent_turns) = messages.split_at(split_idx);

        let older_tokens = Self::estimate_total_tokens(older_turns);
        let recent_tokens = Self::estimate_total_tokens(recent_turns);

        // Generate structured snapshot summary preserving obligations and decisions
        let snapshot = self.generate_structured_snapshot(older_turns, older_tokens);
        let snapshot_tokens = snapshot.tokens_after;
        let tokens_after = snapshot_tokens + recent_tokens;

        CompactionResult {
            compacted: true,
            tokens_before: total_tokens,
            tokens_after,
            messages_compacted: older_turns.len(),
            snapshot: Some(snapshot),
            retained_messages: recent_turns.to_vec(),
        }
    }

    /// Inspects and extracts structured snapshot summaries:
    /// - Preserves active task obligations (TODOs, tasks, assignments).
    /// - Preserves decisions (resolved architectural/technical choices).
    /// - Compiles participant stats and cohesive narrative summary.
    pub fn generate_structured_snapshot(
        &self,
        compacted_messages: &[CompactableMessage],
        tokens_before: usize,
    ) -> CompactSnapshot {
        let mut obligations = Vec::new();
        let mut decisions = Vec::new();
        let mut participant_map = std::collections::HashMap::new();
        let mut narrative_lines = Vec::new();

        for (idx, msg) in compacted_messages.iter().enumerate() {
            // Track participant stats
            let stat = participant_map
                .entry((msg.sender_name.clone(), msg.role))
                .or_insert(0usize);
            *stat += 1;

            let content_lower = msg.content.to_lowercase();

            // Extract decisions
            if content_lower.contains("decided")
                || content_lower.contains("decision:")
                || content_lower.contains("agreed to")
                || content_lower.contains("consensus:")
                || content_lower.contains("approved")
            {
                decisions.push(DecisionEntry {
                    topic: format!("Turn #{} Topic", idx + 1),
                    decision: msg.content.trim().to_string(),
                    decider: Some(msg.sender_name.clone()),
                });
            }

            // Extract active obligations / tasks
            if content_lower.contains("todo:")
                || content_lower.contains("task:")
                || content_lower.contains("obligation:")
                || content_lower.contains("must ")
                || content_lower.contains("assigned to")
                || content_lower.contains("please ")
            {
                obligations.push(TaskObligation {
                    id: format!("task-{}", idx + 1),
                    description: msg.content.trim().to_string(),
                    assignee: Some(msg.sender_name.clone()),
                    status: "active".to_string(),
                });
            }

            // Narrative summary entry
            let first_line = msg
                .content
                .lines()
                .next()
                .unwrap_or("")
                .trim();
            if !first_line.is_empty() {
                narrative_lines.push(format!("{}: {}", msg.sender_name, first_line));
            }
        }

        let summary_text = if narrative_lines.is_empty() {
            "History compacted with no prior turns.".to_string()
        } else {
            let max_narrative_turns = 10;
            let display_lines: Vec<String> = if narrative_lines.len() > max_narrative_turns {
                let first_few = &narrative_lines[..3];
                let last_few = &narrative_lines[narrative_lines.len() - 3..];
                let mut combined = first_few.to_vec();
                combined.push(format!(
                    "... [{} intermediate turns omitted] ...",
                    narrative_lines.len() - 6
                ));
                combined.extend_from_slice(last_few);
                combined
            } else {
                narrative_lines
            };

            format!(
                "Compacted Summary ({} turns):\n{}",
                compacted_messages.len(),
                display_lines.join("\n")
            )
        };

        let mut participant_stats = Vec::new();
        for ((name, role), count) in participant_map {
            participant_stats.push(ParticipantSummary {
                name,
                role,
                message_count: count,
            });
        }
        participant_stats.sort_by(|a, b| b.message_count.cmp(&a.message_count));

        // Estimate tokens of the produced snapshot (concise representation)
        let snapshot_chars = summary_text.len()
            + obligations.iter().map(|o| o.description.len()).sum::<usize>()
            + decisions.iter().map(|d| d.decision.len()).sum::<usize>();
        let tokens_after = ((snapshot_chars + 3) / 4).max(1);

        CompactSnapshot {
            summary_text,
            active_obligations: obligations,
            preserved_decisions: decisions,
            participant_stats,
            compacted_message_count: compacted_messages.len(),
            tokens_before,
            tokens_after,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_message(id: &str, sender: &str, role: SenderRole, content: &str) -> CompactableMessage {
        CompactableMessage::new(id, sender.to_lowercase(), sender, role, content)
    }

    #[test]
    fn test_token_estimation_and_threshold_detection() {
        let config = CompactorConfig::new(80.0, 150, 2);
        let compactor = ContextCompactor::new(config);

        let mut messages = Vec::new();
        // Each short message has ~14 tokens (5 * 14 = 70 tokens, under 80% of 150 = 120)
        for i in 0..5 {
            messages.push(create_test_message(
                &format!("msg-{}", i),
                "Hermes",
                SenderRole::Agent,
                "Normal report.",
            ));
        }

        let tokens = ContextCompactor::estimate_total_tokens(&messages);
        assert!(tokens > 0);

        // Under 120 tokens, should not trigger compaction
        assert!(!compactor.should_compact(&messages));

        // Add large messages to cross 80% threshold (120 tokens)
        for i in 5..12 {
            messages.push(create_test_message(
                &format!("msg-{}", i),
                "Opencode",
                SenderRole::Agent,
                "Substantial turn explaining architectural constraints, data schemas, and API contracts in detail.",
            ));
        }

        let saturated_tokens = ContextCompactor::estimate_total_tokens(&messages);
        assert!(saturated_tokens >= 120);
        assert!(compactor.should_compact(&messages));
    }

    #[test]
    fn test_compaction_trigger_and_token_preservation() {
        let config = CompactorConfig::new(80.0, 300, 2);
        let compactor = ContextCompactor::new(config);

        let mut messages = Vec::new();
        // Fill enough to breach 240 tokens (80% of 300) with lengthy messages
        for i in 0..12 {
            messages.push(create_test_message(
                &format!("msg-{}", i),
                "User",
                SenderRole::Human,
                "Detailed query regarding distributed database consensus algorithms, replication lag under network partition, vector clocks, and linearizable Raft log compaction state machines.",
            ));
        }

        let before_tokens = ContextCompactor::estimate_total_tokens(&messages);
        assert!(before_tokens >= 240);

        let result = compactor.compact_history(&messages);
        assert!(result.compacted);
        assert_eq!(result.retained_messages.len(), 2);
        assert_eq!(result.messages_compacted, 10);
        assert!(result.tokens_after < result.tokens_before);
        assert!(result.snapshot.is_some());
    }

    #[test]
    fn test_active_obligations_and_decisions_preservation() {
        let config = CompactorConfig::new(50.0, 100, 1);
        let compactor = ContextCompactor::new(config);

        let messages = vec![
            create_test_message(
                "1",
                "Alice",
                SenderRole::Human,
                "TODO: Implement database connection pooling and failover mechanism.",
            ),
            create_test_message(
                "2",
                "Bob",
                SenderRole::Agent,
                "Decision: We decided to use rusqlite with bundled SQLite for local embedded storage.",
            ),
            create_test_message(
                "3",
                "Charlie",
                SenderRole::Orchestrator,
                "Agreed to adopt M3 Expressive design tokens for Android client.",
            ),
            create_test_message(
                "4",
                "Dave",
                SenderRole::Agent,
                "Task: Dave please verify the TLS handshake timeouts.",
            ),
            create_test_message(
                "5",
                "Eve",
                SenderRole::Human,
                "Latest status check on recent PR.",
            ),
        ];

        let result = compactor.compact_history(&messages);
        assert!(result.compacted);
        let snapshot = result.snapshot.expect("Expected snapshot summary");

        // Verify obligations preserved
        assert_eq!(snapshot.active_obligations.len(), 2);
        assert!(snapshot.active_obligations.iter().any(|o| o.description.contains("TODO: Implement database connection pooling")));
        assert!(snapshot.active_obligations.iter().any(|o| o.description.contains("Dave please verify the TLS handshake")));

        // Verify decisions preserved
        assert_eq!(snapshot.preserved_decisions.len(), 2);
        assert!(snapshot.preserved_decisions.iter().any(|d| d.decision.contains("rusqlite with bundled SQLite")));
        assert!(snapshot.preserved_decisions.iter().any(|d| d.decision.contains("M3 Expressive design tokens")));

        // Verify participant stats
        assert!(!snapshot.participant_stats.is_empty());
        assert_eq!(result.retained_messages.len(), 1);
        assert_eq!(result.retained_messages[0].id, "5");
    }

    #[test]
    fn test_no_compaction_below_threshold() {
        let config = CompactorConfig::new(80.0, 1000, 2);
        let compactor = ContextCompactor::new(config);

        let messages = vec![
            create_test_message("1", "User", SenderRole::Human, "Hello"),
            create_test_message("2", "Agent", SenderRole::Agent, "Hi, how can I help?"),
        ];

        let result = compactor.compact_history(&messages);
        assert!(!result.compacted);
        assert_eq!(result.messages_compacted, 0);
        assert!(result.snapshot.is_none());
        assert_eq!(result.retained_messages.len(), 2);
    }

    #[test]
    fn test_keep_recent_turns_boundary() {
        let config = CompactorConfig::new(50.0, 50, 4);
        let compactor = ContextCompactor::new(config);

        // Only 3 messages (<= keep_recent_turns of 4), even if exceeding threshold
        let messages = vec![
            create_test_message("1", "Hermes", SenderRole::Agent, "A very long message that easily crosses the 25 token threshold for compaction."),
            create_test_message("2", "Opencode", SenderRole::Agent, "Another very long message that adds further token weight."),
            create_test_message("3", "Codex", SenderRole::Agent, "Yet another message with substantial content."),
        ];

        let result = compactor.compact_history(&messages);
        assert!(!result.compacted);
        assert_eq!(result.retained_messages.len(), 3);
        assert_eq!(result.messages_compacted, 0);
    }
}
