use crate::models::{AgentVote, ConsensusProposal, VoteChoice};
use std::time::{SystemTime, UNIX_EPOCH};
use uuid::Uuid;

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

pub struct ConsensusEngine;

impl ConsensusEngine {
    pub fn create_proposal(room_id: &str, question: &str) -> ConsensusProposal {
        ConsensusProposal {
            id: format!("prop-{}", &Uuid::new_v4().to_string().replace('-', "")[..8]),
            room_id: room_id.to_string(),
            question: question.to_string(),
            votes: Vec::new(),
            outcome: "open".to_string(),
            consensus_ratio: 0.0,
            status: "open".to_string(),
            created_at: now_ts(),
        }
    }

    #[allow(dead_code)]
    pub fn calculate_outcome(votes: &[AgentVote]) -> (String, f64) {
        if votes.is_empty() {
            return ("open".to_string(), 0.0);
        }

        let total = votes.len();
        let mut approves = 0;
        let mut rejects = 0;

        for v in votes {
            match v.vote {
                VoteChoice::Approve => approves += 1,
                VoteChoice::Reject => rejects += 1,
                VoteChoice::Abstain => {}
            }
        }

        let ratio = approves as f64 / total as f64;
        let outcome = if approves > total / 2 {
            "passed"
        } else if rejects >= (total + 1) / 2 {
            "rejected"
        } else if approves == rejects && total >= 2 {
            "tied"
        } else {
            "open"
        };

        (outcome.to_string(), ratio)
    }
}
