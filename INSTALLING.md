# TALOS HOST NODE — AGENT INSTALLATION & BOOTSTRAP PRD

*Version: 2.10.0 | Target: AI Coding Agents (OmniRush, Claude Code, Hermes, OpenCode, Codex) & Human Operators*

---

## 1. Executive Summary & Objective

TALOS is a sovereign, local-first multi-agent orchestration chamber. The mobile Android application communicates exclusively with the desktop/laptop host (`talos-core`) over an encrypted private Tailscale WireGuard overlay (`100.64.0.0/10`).

When an AI agent is instructed to **"set up TALOS"**, **"run TALOS"**, or **"pair TALOS"**, the agent must autonomously:
1. Build and run the `talos-core` Rust service in the background on port `8010`.
2. Discover the host's active Tailscale IPv4 address (`100.x.y.z`).
3. Enforce the **Single Host API Key Policy** by managing a persistent key stored safely at `~/.config/talos/credentials.json`.
4. Output the exact pairing lines to the user so they can paste them into the mobile app.

---

## 2. Universal Cross-Platform Host Setup (Linux, macOS, WSL, Windows)

A universal bootstrap script is provided in `scripts/`:

```bash
# On Linux, macOS, or WSL (auto-generates or reuses single 16-digit API key):
bash scripts/agent_setup.sh

# Pass a custom 16-digit API key:
python3 scripts/agent_setup.py --key <16_DIGIT_KEY>

# Rotate / regenerate key deliberately:
python3 scripts/agent_setup.py --rotate
```

### What the Universal Script Does:
1. **OS & Environment Auto-Detection**: Detects platform (Linux, Darwin/macOS, Windows).
2. **Daemon Verification**: Ensures `talos-core` is built and running as a detached background daemon on port `8010`.
3. **Tailscale IPv4 Resolution**: Automatically resolves the active `100.64.0.0/10` Tailscale address via CLI, status JSON, or socket interfaces.
4. **Single API Key Management & App Reinstall Safety**: Checks `~/.config/talos/credentials.json`. If a key already exists and validates against the host, it is **preserved and reused**. If the user uninstalls and reinstalls the mobile app, they can use this SAME key to log back in without having to generate a new key.
5. **16-Digit Random Key Generation**: If no key exists or `--rotate` is specified, generates a clean 16-digit random API key and registers it with `talos-core`.
6. **Key Rotation at Will**: The host allows the user to rotate or input a new key whenever desired (`--rotate` or `--key <16_DIGIT_KEY>`), while otherwise keeping a single stable key.
7. **Safe Storage**: Stores credentials at `~/.config/talos/credentials.json` with strict `chmod 600` permissions (owner-only). Never stores secrets in `/tmp` or repository files.

---

## 3. The Single Host API Key Policy

To prevent token desynchronization, token duplication, and database bloat:

- **Single Key Rule**: The host machine must maintain **only ONE primary authorized API key** for companion pairing.
- **Persistent Location**: The single key is stored at:
  - Linux / macOS / WSL: `~/.config/talos/credentials.json`
  - Windows: `%APPDATA%\talos\credentials.json`
- **File Permissions**: Must be locked to `chmod 600` (read/write by owner only).
- **Format**:
```json
{
  "tailscale_ip": "<tailscale_ip>",
  "port": 8010,
  "endpoint": "http://<tailscale_ip>:8010",
  "api_key": "talos_sk_...",
  "created_at": "2026-10-28 19:35:00 UTC",
  "environment": "Linux"
}
```
- **Idempotency**: If `credentials.json` already contains a valid key, agents and scripts **MUST NOT** mint a new token. They must reuse the existing verified key.

---

## 4. Mandatory Agent Behavior & Response Contract

When setting up or pairing the TALOS host, the AI agent **MUST ALWAYS** conclude its response with these exact two lines:

```text
User please enter this in your Tailscale IP section: http://<tailscale-ip>:8010
User please enter this in your Host API Key section: talos_sk_<token>
```

### Prohibited Agent Behaviors:
- **NEVER** mint duplicate keys on every run. Always reuse the single key in `~/.config/talos/credentials.json`.
- **NEVER** write credentials to temporary directories like `/tmp` or repo root. Use `~/.config/talos/`.
- **NEVER** ask the user to manually compile Rust code if you have terminal/tool access.
- **NEVER** hallucinate fake Tailscale addresses or random tokens in chat. Always verify against the daemon.

---

## 5. Manual Step-by-Step Execution Guide

If executing step-by-step or troubleshooting manually:

### Step 1: Preflight Dependency Check
```bash
for dep in cargo rustc tailscale curl python3; do
    command -v "$dep" >/dev/null 2>&1 || echo "MISSING: $dep"
done
```

### Step 2: Ensure Tailscale is Active
```bash
tailscale status --json | grep -oP '(?<="BackendState":")[^"]*'
# Expected output: Running

TS_IP=$(tailscale ip -4)
echo "Host Tailscale IP: $TS_IP"
```

### Step 3: Build & Launch talos-core Daemon
```bash
cd crates/talos-core
cargo build --release --quiet

# Launch in background with disown/nohup on port 8010:
PORT=8010 nohup ./target/release/talos-core >~/.config/talos/daemon.log 2>&1 &

# Verify health check:
curl -s -f http://127.0.0.1:8010/health
# Expected: {"status":"healthy","uptime_seconds":...}
```

### Step 4: Mint or Fetch Single Key
```bash
# Run the universal credential manager:
python3 scripts/agent_setup.py
```

---

## 6. System Boot Persistence (Systemd Service)

To ensure `talos-core` automatically starts when the laptop/desktop boots:

1. Create user service file at `~/.config/systemd/user/talos-core.service`:
```ini
[Unit]
Description=TALOS Autonomous Agent Core Service
After=network-online.target tailscaled.service

[Service]
Type=simple
Environment=PORT=8010
Environment=RUST_LOG=info
WorkingDirectory=%h/talos/crates/talos-core
ExecStart=%h/talos/crates/talos-core/target/release/talos-core
Restart=always
RestartSec=3

[Install]
WantedBy=default.target
```

2. Enable and start:
```bash
systemctl --user daemon-reload
systemctl --user enable --now talos-core.service
```

---

## 7. Verification & Health SLA

- `GET http://127.0.0.1:8010/health` -> HTTP 200 OK
- `GET http://<tailscale-ip>:8010/health` -> HTTP 200 OK from mobile device
- `POST http://<tailscale-ip>:8010/api/auth/pair` with `{"token": "<token>"}` -> HTTP 200 OK (`{"mesh_verified": true, "status": "paired"}`)
- Invalid key -> HTTP 401 Unauthorized
- Credentials file permission -> `0600` at `~/.config/talos/credentials.json`
