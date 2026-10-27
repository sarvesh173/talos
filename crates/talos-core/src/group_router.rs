use std::collections::{HashMap, HashSet};
use serde::{Deserialize, Serialize};

/// Target(s) determined by inter-agent dispatch.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DispatchTarget {
    /// Specific agent IDs to route the turn to.
    Specific(Vec<String>),
    /// Broadcast turn to all agents participating in the room.
    BroadcastAll,
    /// No agents should be triggered.
    None,
}

/// Participant info used for mention and addressing resolution.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParticipantInfo {
    pub id: String,
    pub name: String,
}

impl ParticipantInfo {
    pub fn new(id: impl Into<String>, name: impl Into<String>) -> Self {
        Self {
            id: id.into(),
            name: name.into(),
        }
    }
}

/// Parser for message text supporting @mentions, @all broadcast, and natural name addressing.
#[derive(Debug, Default, Clone)]
pub struct MentionParser;

impl MentionParser {
    pub fn new() -> Self {
        Self
    }

    /// Checks if message contains `@all` broadcast mention (case-insensitive).
    /// Matches `@all` when isolated or bounded by non-alphanumeric characters.
    pub fn has_broadcast_mention(text: &str) -> bool {
        let lower = text.to_lowercase();
        let bytes = lower.as_bytes();
        let target = b"@all";

        let mut idx = 0;
        while let Some(pos) = lower[idx..].find("@all") {
            let start = idx + pos;
            let end = start + target.len();

            // Must not be immediately followed by alphanumeric/underscore (e.g. @allow)
            let is_boundary = if end < bytes.len() {
                let next_char = bytes[end];
                !next_char.is_ascii_alphanumeric() && next_char != b'_'
            } else {
                true
            };

            if is_boundary {
                return true;
            }

            idx = end;
        }

        false
    }

    /// Extracts all explicitly @mentioned agent IDs from participant list.
    /// Matches `@agent_id` or `@AgentName` case-insensitively.
    pub fn extract_at_mentions(text: &str, participants: &[ParticipantInfo]) -> Vec<String> {
        let mut matched_ids = Vec::new();
        let mut seen = HashSet::new();

        let lower = text.to_lowercase();
        let bytes = lower.as_bytes();

        for p in participants {
            if seen.contains(&p.id) {
                continue;
            }

            let id_token = format!("@{}", p.id.to_lowercase());
            let name_token = format!("@{}", p.name.to_lowercase());

            let matches_id = Self::contains_isolated_token(&lower, bytes, &id_token);
            let matches_name = Self::contains_isolated_token(&lower, bytes, &name_token);

            if matches_id || matches_name {
                matched_ids.push(p.id.clone());
                seen.insert(p.id.clone());
            }
        }

        matched_ids
    }

    /// Detects natural name addressing in text (e.g. "Hermes, please review..." or "Codex check the code").
    /// Matches when an agent's name appears as a whole word, especially at beginning of sentences,
    /// clauses, or direct conversational address.
    pub fn extract_natural_addressed(text: &str, participants: &[ParticipantInfo]) -> Vec<String> {
        let mut addressed_ids = Vec::new();
        let mut seen = HashSet::new();

        let lower = text.to_lowercase();
        let bytes = lower.as_bytes();

        for p in participants {
            if seen.contains(&p.id) {
                continue;
            }

            let name_lower = p.name.to_lowercase();
            if name_lower.trim().is_empty() {
                continue;
            }

            if Self::contains_natural_addressing(&lower, bytes, &name_lower) {
                addressed_ids.push(p.id.clone());
                seen.insert(p.id.clone());
            }
        }

        addressed_ids
    }

    /// Helper to find token prefixed with '@' bounded by non-alphanumeric chars.
    fn contains_isolated_token(lower: &str, bytes: &[u8], token: &str) -> bool {
        let token_bytes = token.as_bytes();
        let mut idx = 0;

        while let Some(pos) = lower[idx..].find(token) {
            let start = idx + pos;
            let end = start + token_bytes.len();

            // Preceding boundary: start of string or not alphanumeric/underscore
            let left_ok = if start == 0 {
                true
            } else {
                let prev = bytes[start - 1];
                !prev.is_ascii_alphanumeric() && prev != b'_'
            };

            // Following boundary: end of string or not alphanumeric/underscore
            let right_ok = if end >= bytes.len() {
                true
            } else {
                let next = bytes[end];
                !next.is_ascii_alphanumeric() && next != b'_'
            };

            if left_ok && right_ok {
                return true;
            }

            idx = end;
        }

        false
    }

    /// Helper to match natural names as distinct words/clauses without @ symbol.
    fn contains_natural_addressing(lower: &str, bytes: &[u8], name_lower: &str) -> bool {
        let name_bytes = name_lower.as_bytes();
        let mut idx = 0;

        while let Some(pos) = lower[idx..].find(name_lower) {
            let start = idx + pos;
            let end = start + name_bytes.len();

            // Must NOT be preceded by '@' (which is handled by extract_at_mentions)
            let left_ok = if start == 0 {
                true
            } else {
                let prev = bytes[start - 1];
                !prev.is_ascii_alphanumeric() && prev != b'_' && prev != b'@'
            };

            let right_ok = if end >= bytes.len() {
                true
            } else {
                let next = bytes[end];
                !next.is_ascii_alphanumeric() && next != b'_'
            };

            if left_ok && right_ok {
                return true;
            }

            idx = end;
        }

        false
    }
}

/// Configuration options for the inter-agent router.
#[derive(Debug, Clone)]
pub struct RouterConfig {
    /// Maximum allowed dispatch hops / turn depth before dropping to prevent infinite ping-pong loops.
    pub max_dispatch_depth: usize,
    /// Default fallback when no specific mentions are found.
    pub broadcast_on_empty_mentions: bool,
}

impl Default for RouterConfig {
    fn default() -> Self {
        Self {
            max_dispatch_depth: 5,
            broadcast_on_empty_mentions: false,
        }
    }
}

/// Sender role representation for dispatch resolution.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SenderRole {
    Human,
    Agent,
    System,
    Orchestrator,
}

/// Context representing an incoming message for routing.
#[derive(Debug, Clone)]
pub struct MessageContext {
    pub room_id: String,
    pub sender_id: String,
    pub sender_role: SenderRole,
    pub content: String,
    /// The hop / turn depth chain for this deliberation tree.
    pub current_depth: usize,
}

/// Resolution outcome containing target and loop status.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RouteDecision {
    pub target: DispatchTarget,
    pub current_depth: usize,
    pub loop_prevented: bool,
    pub reason: String,
}

/// Core InterAgentRouter resolving agent triggers and enforcing loop prevention.
#[derive(Debug, Clone)]
pub struct InterAgentRouter {
    config: RouterConfig,
    /// Tracks active chain depths per room/thread key: (room_id, chain_key) -> depth
    chain_depths: HashMap<String, usize>,
}

impl InterAgentRouter {
    pub fn new(config: RouterConfig) -> Self {
        Self {
            config,
            chain_depths: HashMap::new(),
        }
    }

    pub fn with_max_depth(max_depth: usize) -> Self {
        Self {
            config: RouterConfig {
                max_dispatch_depth: max_depth,
                ..Default::default()
            },
            chain_depths: HashMap::new(),
        }
    }

    /// Resolves target agents to dispatch based on message content, participants, and depth.
    pub fn resolve(
        &mut self,
        ctx: &MessageContext,
        participants: &[ParticipantInfo],
    ) -> RouteDecision {
        let depth = ctx.current_depth;

        // Loop Prevention Check:
        // If depth reached or exceeded max_dispatch_depth, halt propagation.
        if depth >= self.config.max_dispatch_depth {
            return RouteDecision {
                target: DispatchTarget::None,
                current_depth: depth,
                loop_prevented: true,
                reason: format!(
                    "Dispatch loop prevented: depth {} >= max {}",
                    depth, self.config.max_dispatch_depth
                ),
            };
        }

        // 1. Check for @all broadcast
        if MentionParser::has_broadcast_mention(&ctx.content) {
            // Filter out sender itself from receiving its own broadcast if agent
            return RouteDecision {
                target: DispatchTarget::BroadcastAll,
                current_depth: depth + 1,
                loop_prevented: false,
                reason: "Triggered via @all broadcast mention".to_string(),
            };
        }

        // 2. Check for explicit @mentions
        let mut targets = MentionParser::extract_at_mentions(&ctx.content, participants);

        // 3. If no @mentions, check for natural name addressing
        if targets.is_empty() {
            targets = MentionParser::extract_natural_addressed(&ctx.content, participants);
        }

        // Don't route to the sender agent itself (prevent immediate self-loop)
        targets.retain(|id| id != &ctx.sender_id);

        if !targets.is_empty() {
            RouteDecision {
                target: DispatchTarget::Specific(targets),
                current_depth: depth + 1,
                loop_prevented: false,
                reason: "Triggered via specific agent mention or direct addressing".to_string(),
            }
        } else if self.config.broadcast_on_empty_mentions {
            RouteDecision {
                target: DispatchTarget::BroadcastAll,
                current_depth: depth + 1,
                loop_prevented: false,
                reason: "Fallback to broadcast due to empty mentions".to_string(),
            }
        } else {
            RouteDecision {
                target: DispatchTarget::None,
                current_depth: depth,
                loop_prevented: false,
                reason: "No mentioned agents found".to_string(),
            }
        }
    }

    /// Convenience helper to record and advance a room's conversation depth.
    pub fn record_and_check_chain(&mut self, room_id: &str, sender_role: SenderRole) -> (usize, bool) {
        match sender_role {
            SenderRole::Human => {
                // Human message resets or initializes depth back to 0
                self.chain_depths.insert(room_id.to_string(), 0);
                (0, false)
            }
            SenderRole::Agent | SenderRole::System | SenderRole::Orchestrator => {
                let current = self.chain_depths.entry(room_id.to_string()).or_insert(0);
                if *current >= self.config.max_dispatch_depth {
                    (*current, true)
                } else {
                    *current += 1;
                    (*current, false)
                }
            }
        }
    }

    /// Resets chain depth for a room.
    pub fn reset_chain(&mut self, room_id: &str) {
        self.chain_depths.remove(room_id);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_participants() -> Vec<ParticipantInfo> {
        vec![
            ParticipantInfo::new("hermes", "Hermes"),
            ParticipantInfo::new("codex", "OpenAI Codex"),
            ParticipantInfo::new("opencode", "OpenCode"),
            ParticipantInfo::new("agent-reviewer", "Reviewer"),
        ]
    }

    #[test]
    fn test_broadcast_mention_detection() {
        assert!(MentionParser::has_broadcast_mention("@all please review this"));
        assert!(MentionParser::has_broadcast_mention("Attention @ALL!"));
        assert!(MentionParser::has_broadcast_mention("hello @all, status?"));
        // Boundary check: @allow shouldn't trigger @all
        assert!(!MentionParser::has_broadcast_mention("We @allow this operation"));
        assert!(!MentionParser::has_broadcast_mention("all agents report"));
    }

    #[test]
    fn test_at_mention_extraction_by_id_and_name() {
        let participants = test_participants();

        // Mention by ID
        let targets = MentionParser::extract_at_mentions("@hermes check the tests", &participants);
        assert_eq!(targets, vec!["hermes"]);

        // Mention by Name (case-insensitive)
        let targets = MentionParser::extract_at_mentions("Ask @OpenCode to build", &participants);
        assert_eq!(targets, vec!["opencode"]);

        // Multiple mentions
        let targets = MentionParser::extract_at_mentions(
            "@hermes and @codex, please coordinate",
            &participants,
        );
        assert_eq!(targets.len(), 2);
        assert!(targets.contains(&"hermes".to_string()));
        assert!(targets.contains(&"codex".to_string()));

        // Deduplication
        let targets = MentionParser::extract_at_mentions(
            "@hermes @Hermes check this out",
            &participants,
        );
        assert_eq!(targets, vec!["hermes"]);
    }

    #[test]
    fn test_natural_name_addressing() {
        let participants = test_participants();

        // Natural name addressing at beginning
        let targets = MentionParser::extract_natural_addressed(
            "Hermes, please review the latest commit",
            &participants,
        );
        assert_eq!(targets, vec!["hermes"]);

        // Natural name in middle sentence
        let targets = MentionParser::extract_natural_addressed(
            "I think OpenCode can write the patch",
            &participants,
        );
        assert_eq!(targets, vec!["opencode"]);

        // Partial word should not match (e.g. Hermesify)
        let targets = MentionParser::extract_natural_addressed(
            "This is Hermesify codebase",
            &participants,
        );
        assert!(targets.is_empty());
    }

    #[test]
    fn test_inter_agent_router_specific_dispatch() {
        let mut router = InterAgentRouter::new(RouterConfig::default());
        let participants = test_participants();

        let ctx = MessageContext {
            room_id: "room-1".to_string(),
            sender_id: "human-01".to_string(),
            sender_role: SenderRole::Human,
            content: "@hermes run the compiler checks".to_string(),
            current_depth: 0,
        };

        let decision = router.resolve(&ctx, &participants);
        assert_eq!(decision.target, DispatchTarget::Specific(vec!["hermes".to_string()]));
        assert_eq!(decision.current_depth, 1);
        assert!(!decision.loop_prevented);
    }

    #[test]
    fn test_inter_agent_router_self_mention_filter() {
        let mut router = InterAgentRouter::new(RouterConfig::default());
        let participants = test_participants();

        // Agent mentions itself: should be filtered out
        let ctx = MessageContext {
            room_id: "room-1".to_string(),
            sender_id: "hermes".to_string(),
            sender_role: SenderRole::Agent,
            content: "@hermes status update".to_string(),
            current_depth: 0,
        };

        let decision = router.resolve(&ctx, &participants);
        assert_eq!(decision.target, DispatchTarget::None);
    }

    #[test]
    fn test_inter_agent_router_broadcast_all() {
        let mut router = InterAgentRouter::new(RouterConfig::default());
        let participants = test_participants();

        let ctx = MessageContext {
            room_id: "room-1".to_string(),
            sender_id: "human-01".to_string(),
            sender_role: SenderRole::Human,
            content: "@all convene a sync".to_string(),
            current_depth: 0,
        };

        let decision = router.resolve(&ctx, &participants);
        assert_eq!(decision.target, DispatchTarget::BroadcastAll);
    }

    #[test]
    fn test_loop_prevention_max_depth() {
        let max_depth = 4;
        let mut router = InterAgentRouter::with_max_depth(max_depth);
        let participants = test_participants();

        // Safe turn before max depth
        let safe_ctx = MessageContext {
            room_id: "room-1".to_string(),
            sender_id: "hermes".to_string(),
            sender_role: SenderRole::Agent,
            content: "@codex what do you think?".to_string(),
            current_depth: 3,
        };
        let safe_decision = router.resolve(&safe_ctx, &participants);
        assert_eq!(safe_decision.target, DispatchTarget::Specific(vec!["codex".to_string()]));
        assert_eq!(safe_decision.current_depth, 4);
        assert!(!safe_decision.loop_prevented);

        // Turn at max depth: loop prevention kicks in
        let loop_ctx = MessageContext {
            room_id: "room-1".to_string(),
            sender_id: "codex".to_string(),
            sender_role: SenderRole::Agent,
            content: "@hermes I agree, verify this".to_string(),
            current_depth: 4,
        };
        let loop_decision = router.resolve(&loop_ctx, &participants);
        assert_eq!(loop_decision.target, DispatchTarget::None);
        assert!(loop_decision.loop_prevented);
    }

    #[test]
    fn test_chain_depth_tracker() {
        let mut router = InterAgentRouter::with_max_depth(3);

        // Human turn resets
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Human);
        assert_eq!(depth, 0);
        assert!(!loop_prevented);

        // Agent hop 1
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Agent);
        assert_eq!(depth, 1);
        assert!(!loop_prevented);

        // Agent hop 2
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Agent);
        assert_eq!(depth, 2);
        assert!(!loop_prevented);

        // Agent hop 3
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Agent);
        assert_eq!(depth, 3);
        assert!(!loop_prevented);

        // Agent hop 4 -> Exceeds max depth 3
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Agent);
        assert_eq!(depth, 3);
        assert!(loop_prevented);

        // Human intervention resets counter
        let (depth, loop_prevented) = router.record_and_check_chain("room-a", SenderRole::Human);
        assert_eq!(depth, 0);
        assert!(!loop_prevented);
    }
}
