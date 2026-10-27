use crate::models::{FailoverRouteResponse, ProviderInfo, ProviderModel, ProviderRanking, ProviderStatus};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

pub struct ProviderRegistry;

impl ProviderRegistry {
    pub fn get_default_providers() -> Vec<ProviderInfo> {
        let now = now_ts();
        vec![
            ProviderInfo {
                id: "gemini".to_string(),
                name: "Google Gemini (Flash Tiered)".to_string(),
                vendor: "google".to_string(),
                base_url: "https://generativelanguage.googleapis.com".to_string(),
                default_model: "gemini-3.8-flash".to_string(),
                status: ProviderStatus::Operational,
                latency_ms: Some(142.5),
                context_length: Some(1048576),
                models: vec![
                    ProviderModel {
                        id: "gemini-3.8-flash".to_string(),
                        context_length: Some(1048576),
                        context_source: "live_catalog".to_string(),
                    },
                    ProviderModel {
                        id: "gemini-3.5-pro".to_string(),
                        context_length: Some(2097152),
                        context_source: "live_catalog".to_string(),
                    },
                ],
                models_count: 2,
                checked_at: Some(now),
            },
            ProviderInfo {
                id: "omnirouter".to_string(),
                name: "OmniRouter Gateway".to_string(),
                vendor: "custom".to_string(),
                base_url: "http://localhost:20128/v1".to_string(),
                default_model: "auto/best-coding".to_string(),
                status: ProviderStatus::Operational,
                latency_ms: Some(89.2),
                context_length: Some(2000000),
                models: vec![
                    ProviderModel {
                        id: "auto/best-coding".to_string(),
                        context_length: Some(2000000),
                        context_source: "live_catalog".to_string(),
                    },
                    ProviderModel {
                        id: "gpt-6-sol".to_string(),
                        context_length: Some(1048576),
                        context_source: "hermes_cache".to_string(),
                    },
                ],
                models_count: 2,
                checked_at: Some(now),
            },
            ProviderInfo {
                id: "groq".to_string(),
                name: "Groq LPU Engine".to_string(),
                vendor: "groq".to_string(),
                base_url: "https://api.groq.com/openai/v1".to_string(),
                default_model: "llama-3.3-70b-versatile".to_string(),
                status: ProviderStatus::Operational,
                latency_ms: Some(48.0),
                context_length: Some(131072),
                models: vec![
                    ProviderModel {
                        id: "llama-3.3-70b-versatile".to_string(),
                        context_length: Some(131072),
                        context_source: "live_catalog".to_string(),
                    },
                ],
                models_count: 1,
                checked_at: Some(now),
            },
        ]
    }

    pub fn get_failover_route() -> FailoverRouteResponse {
        let providers = Self::get_default_providers();
        let mut rankings: Vec<ProviderRanking> = providers
            .into_iter()
            .map(|p| ProviderRanking {
                id: p.id.clone(),
                name: p.name,
                latency_ms: p.latency_ms,
                status: "healthy".to_string(),
                tier: if p.id == "groq" {
                    "primary".to_string()
                } else {
                    "secondary".to_string()
                },
            })
            .collect();

        // Sort by latency
        rankings.sort_by(|a, b| {
            a.latency_ms
                .unwrap_or(9999.0)
                .partial_cmp(&b.latency_ms.unwrap_or(9999.0))
                .unwrap()
        });

        let primary = rankings.first().map(|r| r.id.clone()).unwrap_or_else(|| "omnirouter".to_string());
        let chain: Vec<String> = rankings.iter().map(|r| r.id.clone()).collect();

        FailoverRouteResponse {
            primary_provider: primary,
            failover_chain: chain,
            rankings,
            benchmark_timestamp: now_ts(),
        }
    }
}

// ---------------------------------------------------------------------------
// Feature 8 & 11: Universal Custom Provider Management, Vault & Fallback
// ---------------------------------------------------------------------------

#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CustomProvider {
    pub id: String,
    pub name: String,
    pub base_url: String,
    pub api_key: String,
    pub enabled: bool,
    pub target_agents: Vec<String>,
    pub created_at: DateTime<Utc>,
}

#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ModelCatalogEntry {
    pub model_id: String,
    pub provider_id: String,
    pub display_name: String,
    pub hidden: bool,
    pub is_universal: bool,
}

#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
pub struct FallbackCascade {
    pub fallback_chain: Vec<String>,
}

#[allow(dead_code)]
impl FallbackCascade {
    pub fn new(chain: Vec<String>) -> Self {
        Self {
            fallback_chain: chain,
        }
    }

    pub fn primary(&self) -> Option<&str> {
        self.fallback_chain.first().map(|s| s.as_str())
    }

    pub fn next_target(&self, current_target_id: &str) -> Option<&str> {
        if let Some(pos) = self.fallback_chain.iter().position(|id| id == current_target_id) {
            self.fallback_chain.get(pos + 1).map(|s| s.as_str())
        } else {
            self.fallback_chain.first().map(|s| s.as_str())
        }
    }

    pub fn should_failover(status_code: u16) -> bool {
        status_code == 429 || (500..=599).contains(&status_code)
    }

    pub fn auto_advance(&self, current_target_id: &str, status_code: u16) -> Option<&str> {
        if Self::should_failover(status_code) {
            self.next_target(current_target_id)
        } else {
            None
        }
    }
}

#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct VaultData {
    pub providers: Vec<CustomProvider>,
    #[serde(default)]
    pub models: Vec<ModelCatalogEntry>,
    #[serde(default)]
    pub fallbacks: HashMap<String, FallbackCascade>,
}

#[allow(dead_code)]
#[derive(Debug, Clone, Deserialize)]
struct OpenAIModelItem {
    id: String,
}

#[allow(dead_code)]
#[derive(Debug, Clone, Deserialize)]
struct OpenAIModelsResponse {
    #[serde(default)]
    data: Vec<OpenAIModelItem>,
}

#[allow(dead_code)]
#[derive(Debug, Clone)]
pub struct ProviderVault {
    storage_path: PathBuf,
}

#[allow(dead_code)]
impl ProviderVault {
    pub fn new() -> Self {
        let storage_path = Self::default_vault_path();
        Self { storage_path }
    }

    pub fn with_path<P: AsRef<Path>>(path: P) -> Self {
        Self {
            storage_path: path.as_ref().to_path_buf(),
        }
    }

    pub fn default_vault_path() -> PathBuf {
        let home = std::env::var("HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("/tmp"));
        home.join(".talos").join("vault").join("providers.json")
    }

    pub fn storage_path(&self) -> &Path {
        &self.storage_path
    }

    fn ensure_file_permissions(path: &Path) -> Result<(), String> {
        #[cfg(unix)]
        {
            let perms = fs::Permissions::from_mode(0o600);
            fs::set_permissions(path, perms)
                .map_err(|e| format!("Failed to set permissions 0600 on {}: {}", path.display(), e))?;
        }
        let _ = path;
        Ok(())
    }

    pub fn load(&self) -> Result<VaultData, String> {
        if !self.storage_path.exists() {
            return Ok(VaultData::default());
        }

        let raw = fs::read_to_string(&self.storage_path)
            .map_err(|e| format!("Failed to read vault file {}: {}", self.storage_path.display(), e))?;

        if raw.trim().is_empty() {
            return Ok(VaultData::default());
        }

        serde_json::from_str::<VaultData>(&raw)
            .map_err(|e| format!("Failed to parse vault file {}: {}", self.storage_path.display(), e))
    }

    pub fn save(&self, data: &VaultData) -> Result<(), String> {
        if let Some(parent) = self.storage_path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed to create vault directory {}: {}", parent.display(), e))?;
        }

        let serialized = serde_json::to_string_pretty(data)
            .map_err(|e| format!("Failed to serialize vault data: {}", e))?;

        let mut file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(&self.storage_path)
            .map_err(|e| format!("Failed to open vault file {}: {}", self.storage_path.display(), e))?;

        file.write_all(serialized.as_bytes())
            .map_err(|e| format!("Failed to write to vault file {}: {}", self.storage_path.display(), e))?;

        file.flush()
            .map_err(|e| format!("Failed to flush vault file {}: {}", self.storage_path.display(), e))?;

        Self::ensure_file_permissions(&self.storage_path)?;

        Ok(())
    }

    pub fn add_provider(&self, provider: CustomProvider) -> Result<(), String> {
        let mut data = self.load()?;
        if let Some(pos) = data.providers.iter().position(|p| p.id == provider.id) {
            data.providers[pos] = provider;
        } else {
            data.providers.push(provider);
        }
        self.save(&data)
    }

    pub fn get_provider(&self, id: &str) -> Result<Option<CustomProvider>, String> {
        let data = self.load()?;
        Ok(data.providers.into_iter().find(|p| p.id == id))
    }

    pub fn list_providers(&self) -> Result<Vec<CustomProvider>, String> {
        let data = self.load()?;
        Ok(data.providers)
    }

    pub fn set_provider_status(&self, id: &str, enabled: bool) -> Result<bool, String> {
        let mut data = self.load()?;
        if let Some(p) = data.providers.iter_mut().find(|p| p.id == id) {
            p.enabled = enabled;
            self.save(&data)?;
            Ok(true)
        } else {
            Ok(false)
        }
    }

    pub fn delete_provider(&self, id: &str) -> Result<bool, String> {
        let mut data = self.load()?;
        let orig_len = data.providers.len();
        data.providers.retain(|p| p.id != id);
        let removed = data.providers.len() < orig_len;

        data.models.retain(|m| m.provider_id != id);
        data.fallbacks.remove(id);

        if removed {
            self.save(&data)?;
        }
        Ok(removed)
    }

    pub fn add_model(&self, model: ModelCatalogEntry) -> Result<(), String> {
        let mut data = self.load()?;
        if let Some(pos) = data
            .models
            .iter()
            .position(|m| m.model_id == model.model_id && m.provider_id == model.provider_id)
        {
            data.models[pos] = model;
        } else {
            data.models.push(model);
        }
        self.save(&data)
    }

    pub fn list_models(&self) -> Result<Vec<ModelCatalogEntry>, String> {
        let data = self.load()?;
        Ok(data.models)
    }

    pub fn list_visible_models(&self) -> Result<Vec<ModelCatalogEntry>, String> {
        let data = self.load()?;
        Ok(data.models.into_iter().filter(|m| !m.hidden).collect())
    }

    pub fn set_model_hidden(&self, model_id: &str, hidden: bool) -> Result<bool, String> {
        let mut data = self.load()?;
        let mut matched = false;
        for m in data.models.iter_mut() {
            if m.model_id == model_id {
                m.hidden = hidden;
                matched = true;
            }
        }
        if matched {
            self.save(&data)?;
        }
        Ok(matched)
    }

    pub fn delete_model(&self, model_id: &str) -> Result<bool, String> {
        let mut data = self.load()?;
        let orig_len = data.models.len();
        data.models.retain(|m| m.model_id != model_id);
        let removed = data.models.len() < orig_len;
        if removed {
            self.save(&data)?;
        }
        Ok(removed)
    }

    pub fn set_fallback_cascade(&self, provider_id: &str, cascade: FallbackCascade) -> Result<(), String> {
        let mut data = self.load()?;
        data.fallbacks.insert(provider_id.to_string(), cascade);
        self.save(&data)
    }

    pub fn get_fallback_cascade(&self, provider_id: &str) -> Result<Option<FallbackCascade>, String> {
        let data = self.load()?;
        Ok(data.fallbacks.get(provider_id).cloned())
    }

    pub async fn fetch_models_for_provider(&self, provider_id: &str) -> Result<Vec<ModelCatalogEntry>, String> {
        let provider = self
            .get_provider(provider_id)?
            .ok_or_else(|| format!("Provider not found: {}", provider_id))?;

        let models = Self::auto_fetch_models(&provider.base_url, &provider.api_key, &provider.id).await?;

        let mut data = self.load()?;
        for m in &models {
            if let Some(pos) = data
                .models
                .iter()
                .position(|existing| existing.model_id == m.model_id && existing.provider_id == m.provider_id)
            {
                data.models[pos].display_name = m.display_name.clone();
            } else {
                data.models.push(m.clone());
            }
        }
        self.save(&data)?;

        Ok(models)
    }

    pub async fn auto_fetch_models(
        base_url: &str,
        api_key: &str,
        provider_id: &str,
    ) -> Result<Vec<ModelCatalogEntry>, String> {
        let trimmed_url = base_url.trim_end_matches('/');
        let url = if trimmed_url.ends_with("/models") {
            trimmed_url.to_string()
        } else {
            format!("{}/models", trimmed_url)
        };

        let client = reqwest::Client::new();
        let mut req = client.get(&url);
        if !api_key.trim().is_empty() {
            req = req.bearer_auth(api_key);
        }

        let resp = req
            .send()
            .await
            .map_err(|e| format!("HTTP request to {} failed: {}", url, e))?;

        let status = resp.status();
        if !status.is_success() {
            return Err(format!("GET {} returned status {}", url, status));
        }

        let payload: OpenAIModelsResponse = resp
            .json()
            .await
            .map_err(|e| format!("Failed to parse JSON response from {}: {}", url, e))?;

        let entries: Vec<ModelCatalogEntry> = payload
            .data
            .into_iter()
            .map(|item| ModelCatalogEntry {
                display_name: item.id.clone(),
                model_id: item.id,
                provider_id: provider_id.to_string(),
                hidden: false,
                is_universal: false,
            })
            .collect();

        Ok(entries)
    }
}

// ---------------------------------------------------------------------------
// Unit Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_test_vault(test_name: &str) -> (ProviderVault, PathBuf) {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let path = std::env::temp_dir().join(format!("talos_vault_test_{}_{}.json", test_name, nanos));
        let vault = ProviderVault::with_path(&path);
        (vault, path)
    }

    #[test]
    fn test_vault_creation_and_permissions() {
        let (vault, path) = temp_test_vault("creation");

        let initial = vault.load().expect("load empty");
        assert!(initial.providers.is_empty());
        assert!(initial.models.is_empty());

        let provider = CustomProvider {
            id: "groq-custom".to_string(),
            name: "Groq Custom".to_string(),
            base_url: "https://api.groq.com/openai/v1".to_string(),
            api_key: "gsk_secret_12345".to_string(),
            enabled: true,
            target_agents: vec!["hermes".to_string(), "opencode".to_string()],
            created_at: Utc::now(),
        };

        vault.add_provider(provider.clone()).expect("add provider");

        assert!(path.exists());
        #[cfg(unix)]
        {
            let metadata = fs::metadata(&path).expect("metadata");
            let mode = metadata.permissions().mode() & 0o777;
            assert_eq!(mode, 0o600, "File permissions must be 0600 on Unix");
        }

        let stored = vault.get_provider("groq-custom").expect("get provider");
        assert_eq!(stored, Some(provider));

        let _ = fs::remove_file(path);
    }

    #[test]
    fn test_model_addition_hide_unhide_and_deletion() {
        let (vault, path) = temp_test_vault("models");

        let m1 = ModelCatalogEntry {
            model_id: "llama-3.3-70b".to_string(),
            provider_id: "groq-custom".to_string(),
            display_name: "Llama 3.3 70B Versatile".to_string(),
            hidden: false,
            is_universal: true,
        };
        let m2 = ModelCatalogEntry {
            model_id: "deepseek-r1-distill".to_string(),
            provider_id: "groq-custom".to_string(),
            display_name: "DeepSeek R1 Distill 70B".to_string(),
            hidden: false,
            is_universal: false,
        };

        vault.add_model(m1.clone()).expect("add m1");
        vault.add_model(m2.clone()).expect("add m2");

        let all = vault.list_models().expect("list all");
        assert_eq!(all.len(), 2);

        // Hide m2
        let changed = vault.set_model_hidden("deepseek-r1-distill", true).expect("hide");
        assert!(changed);

        let visible = vault.list_visible_models().expect("list visible");
        assert_eq!(visible.len(), 1);
        assert_eq!(visible[0].model_id, "llama-3.3-70b");

        // Unhide m2
        vault.set_model_hidden("deepseek-r1-distill", false).expect("unhide");
        let visible_again = vault.list_visible_models().expect("list visible again");
        assert_eq!(visible_again.len(), 2);

        // Delete m1
        let deleted = vault.delete_model("llama-3.3-70b").expect("delete model");
        assert!(deleted);
        let remaining = vault.list_models().expect("list remaining");
        assert_eq!(remaining.len(), 1);
        assert_eq!(remaining[0].model_id, "deepseek-r1-distill");

        let _ = fs::remove_file(path);
    }

    #[test]
    fn test_fallback_ordering_and_auto_advance() {
        let (vault, path) = temp_test_vault("fallback");

        let chain = vec![
            "groq-primary".to_string(),
            "deepseek-backup-1".to_string(),
            "openrouter-backup-2".to_string(),
        ];
        let cascade = FallbackCascade::new(chain.clone());

        assert_eq!(cascade.primary(), Some("groq-primary"));
        assert_eq!(cascade.next_target("groq-primary"), Some("deepseek-backup-1"));
        assert_eq!(cascade.next_target("deepseek-backup-1"), Some("openrouter-backup-2"));
        assert_eq!(cascade.next_target("openrouter-backup-2"), None);

        // Failover logic on 429 and 5xx
        assert_eq!(cascade.auto_advance("groq-primary", 429), Some("deepseek-backup-1"));
        assert_eq!(cascade.auto_advance("deepseek-backup-1", 503), Some("openrouter-backup-2"));
        assert_eq!(cascade.auto_advance("groq-primary", 200), None);
        assert_eq!(cascade.auto_advance("groq-primary", 401), None);

        // Persist cascade in vault
        vault.set_fallback_cascade("groq-primary", cascade.clone()).expect("save cascade");
        let retrieved = vault.get_fallback_cascade("groq-primary").expect("get cascade");
        assert_eq!(retrieved, Some(cascade));

        let _ = fs::remove_file(path);
    }

    #[test]
    fn test_provider_status_toggle_and_purge() {
        let (vault, path) = temp_test_vault("status_purge");

        let provider = CustomProvider {
            id: "ollama-local".to_string(),
            name: "Ollama Local".to_string(),
            base_url: "http://localhost:11434/v1".to_string(),
            api_key: "".to_string(),
            enabled: true,
            target_agents: vec!["all".to_string()],
            created_at: Utc::now(),
        };

        vault.add_provider(provider).expect("add provider");

        // Toggle disable
        vault.set_provider_status("ollama-local", false).expect("disable");
        let p = vault.get_provider("ollama-local").expect("get").unwrap();
        assert!(!p.enabled);

        // Add a model and cascade for this provider
        vault.add_model(ModelCatalogEntry {
            model_id: "qwen2.5-coder".to_string(),
            provider_id: "ollama-local".to_string(),
            display_name: "Qwen 2.5 Coder".to_string(),
            hidden: false,
            is_universal: false,
        }).expect("add model");
        vault.set_fallback_cascade("ollama-local", FallbackCascade::new(vec!["ollama-local".to_string()])).expect("set cascade");

        // Delete / purge provider
        let deleted = vault.delete_provider("ollama-local").expect("delete provider");
        assert!(deleted);
        assert_eq!(vault.get_provider("ollama-local").expect("get"), None);

        // Associated models and cascade should also be wiped
        let models = vault.list_models().expect("list models");
        assert!(models.is_empty());
        let cascade = vault.get_fallback_cascade("ollama-local").expect("cascade");
        assert_eq!(cascade, None);

        let _ = fs::remove_file(path);
    }
}
