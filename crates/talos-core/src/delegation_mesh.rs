//! Multi-Agent Hierarchical Delegation Mesh & Quota Controller for TALOS.
//!
//! Provides:
//! - Multi-level Agent Hierarchy: Engines -> Delegate Leads -> Child Subagents.
//! - Per-delegate Custom Command Turn Limits (10..100) and Reasoning Effort calibration.
//! - Subagent lifecycle management (Spawning, Running, Verifying, Paused, Completed, Terminated).
//! - Active execution guardrails to prevent runaway token spend and infinite recursion loops.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::info;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum SubagentState {
    Spawning,
    Running,
    Verifying,
    Paused,
    Completed,
    Failed,
    Terminated,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ReasoningEffort {
    Low,
    Medium,
    High,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubagentChildNode {
    pub id: String,
    pub name: String,
    pub parent_delegate_id: String,
    pub task_goal: String,
    pub state: SubagentState,
    pub turns_used: usize,
    pub created_at: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DelegateLeadNode {
    pub id: String,
    pub name: String,
    pub engine_id: String,
    pub assigned_model: String,
    pub reasoning_effort: ReasoningEffort,
    pub command_turn_limit: usize,
    pub turns_consumed: usize,
    pub is_paused: bool,
    pub children: Vec<SubagentChildNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CliEngineNode {
    pub id: String,
    pub name: String,
    pub command_binary: String,
    pub is_available: bool,
    pub delegates: Vec<DelegateLeadNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DelegationHierarchySnapshot {
    pub total_engines: usize,
    pub total_delegates: usize,
    pub total_active_children: usize,
    pub engines: Vec<CliEngineNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateQuotaRequest {
    pub command_turn_limit: Option<usize>,
    pub reasoning_effort: Option<ReasoningEffort>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SpawnSubagentRequest {
    pub name: String,
    pub task_goal: String,
}

/// In-memory Delegation Mesh Coordinator.
#[derive(Debug, Clone)]
pub struct DelegationMesh {
    engines: Arc<RwLock<HashMap<String, CliEngineNode>>>,
}

impl DelegationMesh {
    pub fn new() -> Self {
        let mut map = HashMap::new();

        // 1. OmniRush CLI Engine
        map.insert(
            "omnirush".to_string(),
            CliEngineNode {
                id: "omnirush".to_string(),
                name: "OmniRush Engine".to_string(),
                command_binary: "omnirush".to_string(),
                is_available: true,
                delegates: vec![
                    DelegateLeadNode {
                        id: "del_omni_ui".to_string(),
                        name: "UI Refactoring Lead".to_string(),
                        engine_id: "omnirush".to_string(),
                        assigned_model: "agy/gemini-3.8-flash-tiered".to_string(),
                        reasoning_effort: ReasoningEffort::Medium,
                        command_turn_limit: 50,
                        turns_consumed: 24,
                        is_paused: false,
                        children: vec![
                            SubagentChildNode {
                                id: "sub_phys".to_string(),
                                name: "Motion & Physics Validator".to_string(),
                                parent_delegate_id: "del_omni_ui".to_string(),
                                task_goal: "Verify M3 spring tokens & frame budget".to_string(),
                                state: SubagentState::Running,
                                turns_used: 14,
                                created_at: 1730270000.0,
                            },
                            SubagentChildNode {
                                id: "sub_contrast".to_string(),
                                name: "WCAG Contrast Gate".to_string(),
                                parent_delegate_id: "del_omni_ui".to_string(),
                                task_goal: "Verify 4.5:1 text contrast on Molten Obsidian".to_string(),
                                state: SubagentState::Completed,
                                turns_used: 10,
                                created_at: 1730269000.0,
                            },
                        ],
                    },
                    DelegateLeadNode {
                        id: "del_omni_hw".to_string(),
                        name: "Hardware Bridge Lead".to_string(),
                        engine_id: "omnirush".to_string(),
                        assigned_model: "agy/gemini-3.8-flash-tiered".to_string(),
                        reasoning_effort: ReasoningEffort::Medium,
                        command_turn_limit: 40,
                        turns_consumed: 16,
                        is_paused: false,
                        children: vec![SubagentChildNode {
                            id: "sub_cam".to_string(),
                            name: "Camera & Sensor Introspector".to_string(),
                            parent_delegate_id: "del_omni_hw".to_string(),
                            task_goal: "Audit runtime permissions & intent handlers".to_string(),
                            state: SubagentState::Completed,
                            turns_used: 8,
                            created_at: 1730268000.0,
                        }],
                    },
                ],
            },
        );

        // 2. OpenCode Zen Engine
        map.insert(
            "opencode".to_string(),
            CliEngineNode {
                id: "opencode".to_string(),
                name: "OpenCode Zen Bridge".to_string(),
                command_binary: "opencode".to_string(),
                is_available: true,
                delegates: vec![DelegateLeadNode {
                    id: "del_open_rust".to_string(),
                    name: "Rust Core Maintainer".to_string(),
                    engine_id: "opencode".to_string(),
                    assigned_model: "space-bunny-free".to_string(),
                    reasoning_effort: ReasoningEffort::High,
                    command_turn_limit: 60,
                    turns_consumed: 32,
                    is_paused: false,
                    children: vec![
                        SubagentChildNode {
                            id: "sub_tokio".to_string(),
                            name: "Async Threading Supervisor".to_string(),
                            parent_delegate_id: "del_open_rust".to_string(),
                            task_goal: "Monitor Tokio runtime thread pools".to_string(),
                            state: SubagentState::Running,
                            turns_used: 20,
                            created_at: 1730271000.0,
                        },
                        SubagentChildNode {
                            id: "sub_asan".to_string(),
                            name: "Sanitizer Watchdog".to_string(),
                            parent_delegate_id: "del_open_rust".to_string(),
                            task_goal: "Check memory safety & zero dangling pointers".to_string(),
                            state: SubagentState::Completed,
                            turns_used: 12,
                            created_at: 1730267000.0,
                        },
                    ],
                }],
            },
        );

        // 3. Claude Code CLI Engine
        map.insert(
            "claude_code".to_string(),
            CliEngineNode {
                id: "claude_code".to_string(),
                name: "Claude Code CLI".to_string(),
                command_binary: "claude".to_string(),
                is_available: false,
                delegates: vec![DelegateLeadNode {
                    id: "del_claude_arch".to_string(),
                    name: "Architecture Reviewer".to_string(),
                    engine_id: "claude_code".to_string(),
                    assigned_model: "claude-opus-5.5".to_string(),
                    reasoning_effort: ReasoningEffort::High,
                    command_turn_limit: 30,
                    turns_consumed: 5,
                    is_paused: false,
                    children: vec![],
                }],
            },
        );

        Self {
            engines: Arc::new(RwLock::new(map)),
        }
    }

    /// Snapshot full hierarchy for client telemetry.
    pub async fn snapshot_hierarchy(&self) -> DelegationHierarchySnapshot {
        let guard = self.engines.read().await;
        let mut total_delegates = 0;
        let mut total_active_children = 0;

        let mut engines_vec: Vec<CliEngineNode> = guard.values().cloned().collect();
        engines_vec.sort_by(|a, b| a.id.cmp(&b.id));

        for eng in &engines_vec {
            total_delegates += eng.delegates.len();
            for del in &eng.delegates {
                total_active_children += del
                    .children
                    .iter()
                    .filter(|c| c.state == SubagentState::Running || c.state == SubagentState::Verifying)
                    .count();
            }
        }

        DelegationHierarchySnapshot {
            total_engines: engines_vec.len(),
            total_delegates,
            total_active_children,
            engines: engines_vec,
        }
    }

    /// Update delegate command turn limit or reasoning effort.
    pub async fn update_delegate_quota(
        &self,
        delegate_id: &str,
        req: UpdateQuotaRequest,
    ) -> Result<DelegateLeadNode, String> {
        let mut guard = self.engines.write().await;

        for eng in guard.values_mut() {
            for del in &mut eng.delegates {
                if del.id == delegate_id {
                    if let Some(new_limit) = req.command_turn_limit {
                        let bounded_limit = new_limit.clamp(5, 200);
                        info!(
                            "Updating delegate {} quota from {} to {}",
                            del.id, del.command_turn_limit, bounded_limit
                        );
                        del.command_turn_limit = bounded_limit;
                    }
                    if let Some(new_effort) = req.reasoning_effort {
                        del.reasoning_effort = new_effort;
                    }
                    return Ok(del.clone());
                }
            }
        }

        Err(format!("Delegate '{}' not found", delegate_id))
    }

    /// Toggle pause / resume on a delegate.
    pub async fn toggle_delegate_pause(
        &self,
        delegate_id: &str,
        paused: bool,
    ) -> Result<bool, String> {
        let mut guard = self.engines.write().await;

        for eng in guard.values_mut() {
            for del in &mut eng.delegates {
                if del.id == delegate_id {
                    del.is_paused = paused;
                    info!("Delegate {} paused state set to {}", del.id, paused);
                    return Ok(del.is_paused);
                }
            }
        }

        Err(format!("Delegate '{}' not found", delegate_id))
    }

    /// Spawn a new child subagent under a delegate lead.
    pub async fn spawn_child(
        &self,
        delegate_id: &str,
        req: SpawnSubagentRequest,
    ) -> Result<SubagentChildNode, String> {
        let mut guard = self.engines.write().await;

        for eng in guard.values_mut() {
            for del in &mut eng.delegates {
                if del.id == delegate_id {
                    if del.is_paused {
                        return Err(format!("Cannot spawn child: delegate '{}' is paused", delegate_id));
                    }
                    if del.turns_consumed >= del.command_turn_limit {
                        return Err(format!(
                            "Command limit exceeded ({} >= {}). Please increase quota before spawning new subagents.",
                            del.turns_consumed, del.command_turn_limit
                        ));
                    }

                    let child_id = format!("sub_{}", uuid::Uuid::new_v4().to_string()[..8].to_string());
                    let child = SubagentChildNode {
                        id: child_id,
                        name: req.name,
                        parent_delegate_id: delegate_id.to_string(),
                        task_goal: req.task_goal,
                        state: SubagentState::Running,
                        turns_used: 1,
                        created_at: std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_secs_f64(),
                    };

                    del.turns_consumed += 1;
                    del.children.push(child.clone());
                    info!("Spawned child subagent {} under delegate {}", child.id, delegate_id);
                    return Ok(child);
                }
            }
        }

        Err(format!("Delegate '{}' not found", delegate_id))
    }

    /// Toggle pause / resume on an individual child subagent.
    pub async fn toggle_child_pause(
        &self,
        child_id: &str,
        paused: bool,
    ) -> Result<SubagentState, String> {
        let mut guard = self.engines.write().await;

        for eng in guard.values_mut() {
            for del in &mut eng.delegates {
                for child in &mut del.children {
                    if child.id == child_id {
                        child.state = if paused {
                            SubagentState::Paused
                        } else {
                            SubagentState::Running
                        };
                        info!("Child subagent {} state set to {:?}", child.id, child.state);
                        return Ok(child.state.clone());
                    }
                }
            }
        }

        Err(format!("Child subagent '{}' not found", child_id))
    }

    /// Terminate and remove a child subagent.
    pub async fn terminate_child(&self, child_id: &str) -> Result<bool, String> {
        let mut guard = self.engines.write().await;

        for eng in guard.values_mut() {
            for del in &mut eng.delegates {
                if let Some(pos) = del.children.iter().position(|c| c.id == child_id) {
                    let removed = del.children.remove(pos);
                    info!("Terminated and removed child subagent {}", removed.id);
                    return Ok(true);
                }
            }
        }

        Err(format!("Child subagent '{}' not found", child_id))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_delegation_hierarchy_snapshot() {
        let mesh = DelegationMesh::new();
        let snap = mesh.snapshot_hierarchy().await;

        assert_eq!(snap.total_engines, 3);
        assert!(snap.total_delegates >= 3);
        assert!(snap.total_active_children >= 2);
    }

    #[tokio::test]
    async fn test_update_quota_and_clamp() {
        let mesh = DelegationMesh::new();
        let res = mesh
            .update_delegate_quota(
                "del_omni_ui",
                UpdateQuotaRequest {
                    command_turn_limit: Some(85),
                    reasoning_effort: Some(ReasoningEffort::High),
                },
            )
            .await
            .expect("should update");

        assert_eq!(res.command_turn_limit, 85);
        assert_eq!(res.reasoning_effort, ReasoningEffort::High);

        // Clamping test: negative or huge
        let clamped = mesh
            .update_delegate_quota(
                "del_omni_ui",
                UpdateQuotaRequest {
                    command_turn_limit: Some(500),
                    reasoning_effort: None,
                },
            )
            .await
            .expect("should update");
        assert_eq!(clamped.command_turn_limit, 200);
    }

    #[tokio::test]
    async fn test_spawn_child_under_limit() {
        let mesh = DelegationMesh::new();
        let child = mesh
            .spawn_child(
                "del_omni_ui",
                SpawnSubagentRequest {
                    name: "Live CSS Inspector".to_string(),
                    task_goal: "Verify flexbox and grid layouts".to_string(),
                },
            )
            .await
            .expect("spawn should succeed");

        assert_eq!(child.state, SubagentState::Running);
        assert_eq!(child.parent_delegate_id, "del_omni_ui");
    }

    #[tokio::test]
    async fn test_pause_prevents_spawning() {
        let mesh = DelegationMesh::new();
        mesh.toggle_delegate_pause("del_omni_ui", true)
            .await
            .expect("pause should succeed");

        let err = mesh
            .spawn_child(
                "del_omni_ui",
                SpawnSubagentRequest {
                    name: "Should Fail".to_string(),
                    task_goal: "None".to_string(),
                },
            )
            .await;

        assert!(err.is_err());
    }

    #[tokio::test]
    async fn test_toggle_and_terminate_child() {
        let mesh = DelegationMesh::new();
        let child = mesh
            .spawn_child(
                "del_omni_ui",
                SpawnSubagentRequest {
                    name: "Test Runner".to_string(),
                    task_goal: "Run test suite".to_string(),
                },
            )
            .await
            .expect("spawn should succeed");

        let paused_state = mesh
            .toggle_child_pause(&child.id, true)
            .await
            .expect("pause should succeed");
        assert_eq!(paused_state, SubagentState::Paused);

        let term_res = mesh.terminate_child(&child.id).await.expect("terminate should succeed");
        assert!(term_res);
    }
}
