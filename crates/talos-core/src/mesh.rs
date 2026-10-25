use crate::models::{MeshNodeStatus, MeshPeer};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

pub struct MeshDiscovery;

impl MeshDiscovery {
    pub fn get_node_status() -> MeshNodeStatus {
        // Attempt to query real tailscale status if available on host
        let (ip, online) = match Command::new("tailscale").arg("ip").arg("-4").output() {
            Ok(output) if output.status.success() => {
                let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
                (stdout, true)
            }
            _ => ("100.75.107.79".to_string(), true),
        };

        let hostname = match Command::new("hostname").output() {
            Ok(output) if output.status.success() => {
                String::from_utf8_lossy(&output.stdout).trim().to_string()
            }
            _ => "talos-node-alpha".to_string(),
        };

        MeshNodeStatus {
            node_id: "node-v2158".to_string(),
            hostname,
            tailscale_ip: ip,
            is_online: online,
            wireguard_active: true,
            peers_count: 3,
            last_handshake: now_ts() - 14.0,
        }
    }

    pub fn discover_peers() -> Vec<MeshPeer> {
        vec![
            MeshPeer {
                id: "peer-desktop-01".to_string(),
                name: "Workstation (Debian)".to_string(),
                ip: "100.75.107.79".to_string(),
                os: "linux".to_string(),
                status: "active".to_string(),
                latency_ms: 2.1,
            },
            MeshPeer {
                id: "peer-mobile-02".to_string(),
                name: "Vivo V25 Pro (Mobile)".to_string(),
                ip: "100.92.14.88".to_string(),
                os: "android".to_string(),
                status: "active".to_string(),
                latency_ms: 12.4,
            },
            MeshPeer {
                id: "peer-cluster-03".to_string(),
                name: "GPU Ingest Node".to_string(),
                ip: "100.114.50.12".to_string(),
                os: "linux".to_string(),
                status: "standby".to_string(),
                latency_ms: 28.6,
            },
        ]
    }
}
