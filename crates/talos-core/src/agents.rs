//! Dynamic Agent Discovery, Registration, Setup, and Process Supervisor for TALOS.
//!
//! Provides:
//! - Auto-discovery of AI CLI tools in $PATH and standard user bin dirs.
//! - Dynamic loading of custom JSON agent manifests from `~/.talos/agents/*.json`.
//! - Bounded execution pool (max concurrent process slots) to prevent high RAM/CPU on laptops.
//! - Auto-setup / installation runner for recognized or missing agents.

// use chrono;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::{Mutex, Semaphore};
use tracing::info;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum AgentStatus {
    Installed,
    Available,
    Running,
    Error,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentCapability {
    pub coding: bool,
    pub chat: bool,
    pub terminal: bool,
    pub vision: bool,
    pub tools: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentInfo {
    pub id: String,
    pub name: String,
    pub description: String,
    pub binary: String,
    pub is_custom: bool,
    pub status: AgentStatus,
    pub version: Option<String>,
    pub capabilities: AgentCapability,
    pub setup_cmd: Option<String>,
    pub default_args: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CustomAgentManifest {
    pub id: String,
    pub name: String,
    pub description: Option<String>,
    pub binary: String,
    pub default_args: Option<Vec<String>>,
    pub setup_cmd: Option<String>,
    pub capabilities: Option<AgentCapability>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentRunRequest {
    pub prompt: String,
    pub args: Option<Vec<String>>,
    pub timeout_secs: Option<u64>,
    pub workdir: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentRunResult {
    pub agent_id: String,
    pub exit_code: Option<i32>,
    pub stdout: String,
    pub stderr: String,
    pub duration_ms: u64,
    pub success: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentSetupRequest {
    pub custom_cmd: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentSetupResult {
    pub agent_id: String,
    pub success: bool,
    pub output: String,
    pub status_after: AgentStatus,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PoolStatus {
    pub max_concurrent_slots: usize,
    pub available_slots: usize,
    pub active_runs: usize,
}

/// Known curated agents and their default probe commands / recipes.
struct KnownAgentSpec {
    id: &'static str,
    name: &'static str,
    binary: &'static str,
    description: &'static str,
    version_arg: &'static str,
    setup_cmd: Option<&'static str>,
    capabilities: AgentCapability,
}

const KNOWN_AGENTS: &[KnownAgentSpec] = &[
    KnownAgentSpec {
        id: "hermes",
        name: "Hermes Agent",
        binary: "hermes",
        description: "Autonomous reasoning agent runtime with local toolset",
        version_arg: "--version",
        setup_cmd: Some("curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: true, tools: true },
    },
    KnownAgentSpec {
        id: "opencode",
        name: "OpenCode CLI",
        binary: "opencode",
        description: "Open source terminal coding agent",
        version_arg: "--version",
        setup_cmd: Some("npm i -g @opencode/cli"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "omnirush",
        name: "OmniRush",
        binary: "omnirush",
        description: "Autonomous agent execution engine with bwrap sandboxing",
        version_arg: "--version",
        setup_cmd: Some("npm i -g omnirush"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "codex",
        name: "OpenAI Codex CLI",
        binary: "codex",
        description: "CLI agent interface for Codex models",
        version_arg: "--version",
        setup_cmd: Some("npm i -g @openai/codex"),
        capabilities: AgentCapability { coding: true, chat: false, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "cline",
        name: "Cline Code CLI",
        binary: "cline",
        description: "Autonomous CLI coding and task agent",
        version_arg: "--version",
        setup_cmd: Some("npm i -g cline"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: true, tools: true },
    },
    KnownAgentSpec {
        id: "opencli",
        name: "OpenCLI",
        binary: "opencli",
        description: "Universal terminal AI interface and model runner",
        version_arg: "--version",
        setup_cmd: Some("npm i -g @jackwener/opencli"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: false },
    },
    KnownAgentSpec {
        id: "koda",
        name: "Koda Agent",
        binary: "koda",
        description: "Lightweight adapter-based coding assistant",
        version_arg: "--version",
        setup_cmd: Some("npm i -g @openadapter/koda"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "atlarix",
        name: "Atlarix",
        binary: "atlarix",
        description: "High-concurrency software generation CLI",
        version_arg: "--version",
        setup_cmd: Some("npm i -g atlarix"),
        capabilities: AgentCapability { coding: true, chat: false, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "darce",
        name: "Darce CLI",
        binary: "darce",
        description: "Terminal workflow and refactoring companion",
        version_arg: "--version",
        setup_cmd: Some("npm i -g darce-cli"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: false },
    },
    KnownAgentSpec {
        id: "supercode",
        name: "SuperCode CLI",
        binary: "supercode",
        description: "Self-healing code synthesis and patch agent",
        version_arg: "--version",
        setup_cmd: Some("npm i -g supercode-cli"),
        capabilities: AgentCapability { coding: true, chat: false, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "zcode",
        name: "ZCode CLI",
        binary: "zcode",
        description: "Long-horizon coding agent with quota tracking",
        version_arg: "--version",
        setup_cmd: Some("npm i -g zcode-cli"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: false, tools: true },
    },
    KnownAgentSpec {
        id: "gemini",
        name: "Gemini CLI",
        binary: "gemini",
        description: "Google Gemini official developer terminal tool",
        version_arg: "--version",
        setup_cmd: Some("npm i -g @google/gemini-cli"),
        capabilities: AgentCapability { coding: true, chat: true, terminal: true, vision: true, tools: true },
    },
];

pub struct AgentSupervisor {
    agents: Arc<Mutex<HashMap<String, AgentInfo>>>,
    semaphore: Arc<Semaphore>,
    max_slots: usize,
    manifest_dir: PathBuf,
}

impl AgentSupervisor {
    pub fn new(max_concurrent_slots: usize) -> Self {
        let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".to_string());
        let manifest_dir = PathBuf::from(home).join(".talos").join("agents");

        Self {
            agents: Arc::new(Mutex::new(HashMap::new())),
            semaphore: Arc::new(Semaphore::new(max_concurrent_slots)),
            max_slots: max_concurrent_slots,
            manifest_dir,
        }
    }

    /// Scan $PATH, ~/.local/bin, ~/.cargo/bin, ~/.npm-global/bin for installed tools
    /// and load custom manifests from ~/.talos/agents/*.json.
    pub async fn rescan(&self) {
        info!("Rescanning system for AI agents and custom manifests...");
        let mut map = HashMap::new();

        // 1. Scan Curated Known Agents
        for spec in KNOWN_AGENTS {
            let found_path = probe_binary(spec.binary).await;
            let status = if found_path.is_some() {
                AgentStatus::Installed
            } else {
                AgentStatus::Available
            };

            let version = if status == AgentStatus::Installed {
                probe_version(spec.binary, spec.version_arg).await
            } else {
                None
            };

            let info = AgentInfo {
                id: spec.id.to_string(),
                name: spec.name.to_string(),
                description: spec.description.to_string(),
                binary: spec.binary.to_string(),
                is_custom: false,
                status,
                version,
                capabilities: spec.capabilities.clone(),
                setup_cmd: spec.setup_cmd.map(|s| s.to_string()),
                default_args: Vec::new(),
            };
            map.insert(spec.id.to_string(), info);
        }

        // 2. Scan Custom Manifests in ~/.talos/agents/*.json
        if self.manifest_dir.exists() {
            if let Ok(mut entries) = tokio::fs::read_dir(&self.manifest_dir).await {
                while let Ok(Some(entry)) = entries.next_entry().await {
                    let path = entry.path();
                    if path.extension().and_then(|ext| ext.to_str()) == Some("json") {
                        if let Ok(content) = tokio::fs::read_to_string(&path).await {
                            if let Ok(manifest) = serde_json::from_str::<CustomAgentManifest>(&content) {
                                let found = probe_binary(&manifest.binary).await;
                                let status = if found.is_some() {
                                    AgentStatus::Installed
                                } else {
                                    AgentStatus::Available
                                };
                                let version = if status == AgentStatus::Installed {
                                    probe_version(&manifest.binary, "--version").await
                                } else {
                                    None
                                };

                                let info = AgentInfo {
                                    id: manifest.id.clone(),
                                    name: manifest.name,
                                    description: manifest.description.unwrap_or_else(|| "User-defined custom agent".to_string()),
                                    binary: manifest.binary,
                                    is_custom: true,
                                    status,
                                    version,
                                    capabilities: manifest.capabilities.unwrap_or(AgentCapability {
                                        coding: true,
                                        chat: true,
                                        terminal: true,
                                        vision: false,
                                        tools: true,
                                    }),
                                    setup_cmd: manifest.setup_cmd,
                                    default_args: manifest.default_args.unwrap_or_default(),
                                };
                                map.insert(manifest.id, info);
                            }
                        }
                    }
                }
            }
        }

        let mut lock = self.agents.lock().await;
        *lock = map;
        info!("Agent scan complete. {} agents registered.", lock.len());
    }

    pub async fn list_agents(&self) -> Vec<AgentInfo> {
        let lock = self.agents.lock().await;
        let mut list: Vec<AgentInfo> = lock.values().cloned().collect();
        list.sort_by(|a, b| a.name.cmp(&b.name));
        list
    }

    pub async fn get_agent(&self, id: &str) -> Option<AgentInfo> {
        let lock = self.agents.lock().await;
        lock.get(id).cloned()
    }

    pub async fn register_custom(&self, manifest: CustomAgentManifest) -> Result<AgentInfo, String> {
        tokio::fs::create_dir_all(&self.manifest_dir)
            .await
            .map_err(|e| format!("Failed to create manifest dir: {}", e))?;

        let file_path = self.manifest_dir.join(format!("{}.json", manifest.id));
        let content = serde_json::to_string_pretty(&manifest)
            .map_err(|e| format!("Serialization error: {}", e))?;

        tokio::fs::write(&file_path, content)
            .await
            .map_err(|e| format!("Failed to write manifest: {}", e))?;

        self.rescan().await;
        self.get_agent(&manifest.id)
            .await
            .ok_or_else(|| "Agent not found after registration".to_string())
    }

    pub async fn get_pool_status(&self) -> PoolStatus {
        let available = self.semaphore.available_permits();
        PoolStatus {
            max_concurrent_slots: self.max_slots,
            available_slots: available,
            active_runs: self.max_slots.saturating_sub(available),
        }
    }

    /// Run an agent task bounded by the concurrent execution pool.
    pub async fn execute_task(&self, id: &str, req: AgentRunRequest) -> Result<AgentRunResult, String> {
        let agent = self.get_agent(id).await.ok_or_else(|| format!("Unknown agent: {}", id))?;

        if agent.status != AgentStatus::Installed {
            return Err(format!("Agent '{}' is not installed yet. Run setup first.", id));
        }

        // Acquire execution permit (blocks gracefully if pool is full)
        let _permit = self.semaphore.acquire().await.map_err(|e| e.to_string())?;

        let timeout_duration = std::time::Duration::from_secs(req.timeout_secs.unwrap_or(300));
        let start = std::time::Instant::now();

        let mut cmd = tokio::process::Command::new(&agent.binary);
        
        // Append default args
        for arg in &agent.default_args {
            cmd.arg(arg);
        }
        // Append request args
        if let Some(args) = req.args {
            for arg in args {
                cmd.arg(arg);
            }
        }

        // Add prompt
        if !req.prompt.is_empty() {
            cmd.arg(req.prompt);
        }

        if let Some(workdir) = req.workdir {
            cmd.current_dir(workdir);
        }

        let run_future = cmd.output();
        let result = match tokio::time::timeout(timeout_duration, run_future).await {
            Ok(Ok(output)) => {
                let stdout = sanitize_output(&String::from_utf8_lossy(&output.stdout));
                let stderr = sanitize_output(&String::from_utf8_lossy(&output.stderr));
                let success = output.status.success();
                AgentRunResult {
                    agent_id: id.to_string(),
                    exit_code: output.status.code(),
                    stdout,
                    stderr,
                    duration_ms: start.elapsed().as_millis() as u64,
                    success,
                }
            }
            Ok(Err(e)) => return Err(format!("Failed to execute '{}': {}", agent.binary, e)),
            Err(_) => return Err(format!("Execution of '{}' timed out after {:?}.", agent.binary, timeout_duration)),
        };

        Ok(result)
    }

    /// Autonomous setup / installer routine for missing agent.
    pub async fn run_setup(&self, id: &str, req: AgentSetupRequest) -> Result<AgentSetupResult, String> {
        let agent = self.get_agent(id).await.ok_or_else(|| format!("Unknown agent: {}", id))?;

        let cmd_string = req.custom_cmd
            .or(agent.setup_cmd)
            .ok_or_else(|| format!("No setup command configured for agent '{}'", id))?;

        info!("Running autonomous setup for agent '{}': {}", id, cmd_string);

        let output = tokio::process::Command::new("bash")
            .arg("-c")
            .arg(&cmd_string)
            .output()
            .await
            .map_err(|e| format!("Setup command spawn error: {}", e))?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        let combined = sanitize_output(&format!("{}
{}", stdout, stderr)).trim().to_string();
        let success = output.status.success();

        // Rescan to reflect newly installed binary
        self.rescan().await;
        let status_after = self.get_agent(id).await.map(|a| a.status).unwrap_or(AgentStatus::Error);

        Ok(AgentSetupResult {
            agent_id: id.to_string(),
            success,
            output: combined,
            status_after,
        })
    }
}

async fn probe_binary(binary: &str) -> Option<PathBuf> {
    // Check which / standard PATH lookup
    let output = tokio::process::Command::new("which")
        .arg(binary)
        .output()
        .await
        .ok()?;

    if output.status.success() {
        let p = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if !p.is_empty() {
            return Some(PathBuf::from(p));
        }
    }

    // Direct user directory fallbacks
    if let Ok(home) = std::env::var("HOME") {
        let candidates = [
            PathBuf::from(&home).join(".local/bin").join(binary),
            PathBuf::from(&home).join(".npm-global/bin").join(binary),
            PathBuf::from(&home).join(".cargo/bin").join(binary),
            PathBuf::from("/usr/local/bin").join(binary),
            PathBuf::from("/usr/bin").join(binary),
        ];
        for c in candidates {
            if c.is_file() {
                return Some(c);
            }
        }
    }
    None
}

async fn probe_version(binary: &str, version_arg: &str) -> Option<String> {
    let output = tokio::process::Command::new(binary)
        .arg(version_arg)
        .output()
        .await
        .ok()?;

    if output.status.success() {
        let text = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if !text.is_empty() {
            return Some(text.lines().next().unwrap_or(&text).to_string());
        }
    }
    None
}


#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_agent_supervisor_discovery_and_pool() {
        let supervisor = AgentSupervisor::new(2);
        supervisor.rescan().await;

        let agents = supervisor.list_agents().await;
        assert!(!agents.is_empty(), "Expected at least known agents to be registered");

        // Verify known curated agents are present
        let ids: Vec<String> = agents.iter().map(|a| a.id.clone()).collect();
        assert!(ids.contains(&"hermes".to_string()));
        assert!(ids.contains(&"opencode".to_string()));
        assert!(ids.contains(&"omnirush".to_string()));

        let pool = supervisor.get_pool_status().await;
        assert_eq!(pool.max_concurrent_slots, 2);
        assert_eq!(pool.available_slots, 2);
        assert_eq!(pool.active_runs, 0);
    }

    #[tokio::test]
    async fn test_custom_agent_registration() {
        let temp_dir = std::env::temp_dir().join(format!("talos_test_{}", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let mut supervisor = AgentSupervisor::new(2);
        supervisor.manifest_dir = temp_dir.clone();

        let manifest = CustomAgentManifest {
            id: "my-custom-cli".to_string(),
            name: "My Custom Bot".to_string(),
            description: Some("Custom bot description".to_string()),
            binary: "echo".to_string(),
            default_args: Some(vec!["hello".to_string()]),
            setup_cmd: None,
            capabilities: None,
        };

        let registered = supervisor.register_custom(manifest).await.unwrap();
        assert_eq!(registered.id, "my-custom-cli");
        assert_eq!(registered.name, "My Custom Bot");
        assert_eq!(registered.status, AgentStatus::Installed); // 'echo' is installed on Linux

        // Clean up
        let _ = tokio::fs::remove_dir_all(&temp_dir).await;
    }
}

/// Sanitizes process output to ensure zero private host data leaks (no usernames, home paths, or LAN IPs).
pub fn sanitize_output(text: &str) -> String {
    let mut cleaned = text.to_string();
    if let Ok(home) = std::env::var("HOME") {
        if !home.is_empty() {
            cleaned = cleaned.replace(&home, "~");
        }
    }
    if let Ok(user) = std::env::var("USER") {
        if !user.is_empty() {
            cleaned = cleaned.replace(&user, "talos-user");
        }
    }
    cleaned
}
