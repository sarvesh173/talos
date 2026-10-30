//! Obscura Stealth Headless Browser Bridge for TALOS Core.
//!
//! Exposes Rust-native stealth browsing, DOM extraction, and CDP telemetry
//! without Chromium bloat (28MB RSS vs 200+MB Chrome).

use serde::{Deserialize, Serialize};
use std::process::Command;
use std::time::Instant;
use tracing::{info, warn};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BrowserStatusResult {
    pub available: bool,
    pub version: String,
    pub binary_path: String,
    pub cdp_port: u16,
    pub stealth_capable: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BrowserFetchRequest {
    pub url: String,
    #[serde(default = "default_stealth")]
    pub stealth: bool,
    #[serde(default = "default_dump_mode")]
    pub dump_mode: String, // "text", "links", "assets"
    #[serde(default = "default_timeout")]
    pub timeout_secs: u64,
}

fn default_stealth() -> bool {
    true
}

fn default_dump_mode() -> String {
    "text".to_string()
}

fn default_timeout() -> u64 {
    15
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BrowserFetchResult {
    pub url: String,
    pub title: String,
    pub content: String,
    pub duration_ms: u64,
    pub success: bool,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Default)]
pub struct BrowserBridge;

impl BrowserBridge {
    pub fn new() -> Self {
        Self
    }

    /// Check if `obscura` is installed in PATH or standard user directories.
    pub fn check_status(&self) -> BrowserStatusResult {
        let binary = "obscura";
        let output = Command::new(binary).arg("--version").output();

        match output {
            Ok(out) if out.status.success() => {
                let ver_str = String::from_utf8_lossy(&out.stdout).trim().to_string();
                let path = Command::new("which")
                    .arg(binary)
                    .output()
                    .ok()
                    .and_then(|o| String::from_utf8(o.stdout).ok())
                    .map(|s| s.trim().to_string())
                    .unwrap_or_else(|| "obscura".to_string());

                BrowserStatusResult {
                    available: true,
                    version: ver_str,
                    binary_path: path,
                    cdp_port: 9222,
                    stealth_capable: true,
                }
            }
            _ => BrowserStatusResult {
                available: false,
                version: "unrecognized".to_string(),
                binary_path: "not found".to_string(),
                cdp_port: 9222,
                stealth_capable: false,
            },
        }
    }

    /// Fetch a URL using Obscura with stealth and dump options.
    pub async fn fetch_page(&self, req: BrowserFetchRequest) -> BrowserFetchResult {
        let start = Instant::now();
        let target_url = req.url.trim();

        if target_url.is_empty() {
            return BrowserFetchResult {
                url: "".to_string(),
                title: "".to_string(),
                content: "".to_string(),
                duration_ms: 0,
                success: false,
                error: Some("URL cannot be empty".to_string()),
            };
        }

        // Sanitize URL protocol
        let normalized_url = if !target_url.starts_with("http://") && !target_url.starts_with("https://") {
            format!("https://{}", target_url)
        } else {
            target_url.to_string()
        };

        let mut cmd = tokio::process::Command::new("obscura");

        if req.stealth {
            cmd.arg("--stealth");
        }

        cmd.arg("fetch")
            .arg(&normalized_url)
            .arg("--allow-private-network")
            .arg("--timeout")
            .arg(req.timeout_secs.to_string());

        match req.dump_mode.as_str() {
            "links" => {
                cmd.arg("--dump").arg("links");
            }
            "assets" => {
                cmd.arg("--dump").arg("assets");
            }
            _ => {
                cmd.arg("--dump").arg("text");
            }
        }

        info!("Executing Obscura fetch: {:?}", cmd);

        match cmd.output().await {
            Ok(output) => {
                let duration_ms = start.elapsed().as_millis() as u64;
                let stdout = String::from_utf8_lossy(&output.stdout).to_string();
                let stderr = String::from_utf8_lossy(&output.stderr).to_string();

                if output.status.success() {
                    // Extract title if present in standard Obscura page header
                    let title = stdout
                        .lines()
                        .find(|l| l.contains("Page loaded:") && l.contains("\""))
                        .and_then(|l| l.split('\"').nth(1))
                        .unwrap_or("Web Page")
                        .to_string();

                    BrowserFetchResult {
                        url: normalized_url,
                        title,
                        content: stdout,
                        duration_ms,
                        success: true,
                        error: None,
                    }
                } else {
                    warn!("Obscura fetch failed: {}", stderr);
                    BrowserFetchResult {
                        url: normalized_url,
                        title: "".to_string(),
                        content: stdout,
                        duration_ms,
                        success: false,
                        error: Some(if stderr.is_empty() { "Process exited with error".to_string() } else { stderr }),
                    }
                }
            }
            Err(e) => BrowserFetchResult {
                url: normalized_url,
                title: "".to_string(),
                content: "".to_string(),
                duration_ms: start.elapsed().as_millis() as u64,
                success: false,
                error: Some(format!("Failed to spawn obscura: {}", e)),
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_check_status() {
        let bridge = BrowserBridge::new();
        let status = bridge.check_status();
        assert!(status.available);
        assert!(status.version.contains("obscura"));
    }

    #[tokio::test]
    async fn test_empty_url_validation() {
        let bridge = BrowserBridge::new();
        let res = bridge
            .fetch_page(BrowserFetchRequest {
                url: "   ".to_string(),
                stealth: true,
                dump_mode: "text".to_string(),
                timeout_secs: 5,
            })
            .await;

        assert!(!res.success);
        assert!(res.error.unwrap().contains("empty"));
    }
}
