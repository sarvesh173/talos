use crate::models::{FailoverRouteResponse, ProviderInfo, ProviderModel, ProviderRanking, ProviderStatus};
use std::time::{SystemTime, UNIX_EPOCH};

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
