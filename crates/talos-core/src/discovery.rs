//! UDP discovery responder - zero-config pairing beacon.
//!
//! Listens on 0.0.0.0:8089 for "TALOS_DISCOVER" probes from the Android
//! client and replies with the service endpoint JSON. This is how the app
//! auto-detects the core on a LAN without any IP being typed or displayed.

use tokio::net::UdpSocket;
use tracing::{info, warn};

const PROBE_PORT: u16 = 8089;
const PROBE_PHRASE: &str = "TALOS_DISCOVER";

pub async fn run_discovery_responder() {
    let socket = match UdpSocket::bind(("0.0.0.0", PROBE_PORT)).await {
        Ok(s) => s,
        Err(e) => {
            // Another instance may hold the port; discovery is optional.
            warn!("discovery responder unavailable on {PROBE_PORT}: {e}");
            return;
        }
    };

    info!("discovery responder listening on udp/{PROBE_PORT}");

    let mut buf = vec![0u8; 128];
    loop {
        match socket.recv_from(&mut buf).await {
            Ok((len, peer)) => {
                let msg = String::from_utf8_lossy(&buf[..len]);
                if msg.trim().contains(PROBE_PHRASE) {
                    let reply = serde_json::json!({
                        "service": "talos-core",
                        "node": hostname(),
                        "port": effective_port(),
                        "version": env!("CARGO_PKG_VERSION"),
                    });
                    let payload = reply.to_string();
                    if let Err(e) = socket.send_to(payload.as_bytes(), peer).await {
                        warn!("discovery reply to {peer} failed: {e}");
                    }
                }
            }
            Err(e) => {
                warn!("discovery recv error: {e}");
                tokio::time::sleep(std::time::Duration::from_millis(500)).await;
            }
        }
    }
}

fn hostname() -> String {
    std::env::var("TALOS_NODE_NAME")
        .ok()
        .filter(|v| !v.is_empty())
        .or_else(|| {
            std::fs::read_to_string("/etc/hostname")
                .ok()
                .map(|h| h.trim().to_string())
                .filter(|h| !h.is_empty())
        })
        .unwrap_or_else(|| "talos-node".to_string())
}

fn effective_port() -> u16 {
    std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8000)
}
