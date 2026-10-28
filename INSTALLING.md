# TALOS HOST NODE — AGENT INSTALLATION & BOOTSTRAP PRD

*Version: 2.9.0 | Target: AI Coding Agents (OmniRush, Claude Code, Hermes, OpenCode, Codex) & Human Operators*

---

## 1. Executive Summary & Objective

TALOS is a sovereign, local-first multi-agent orchestration chamber. The mobile Android application communicates exclusively with the desktop/laptop host (`talos-core`) over an encrypted private Tailscale WireGuard overlay (`100.64.0.0/10`).

When an AI agent is instructed to **"set up TALOS"**, **"run TALOS"**, or **"pair TALOS"**, the agent must autonomously:
1. Build and run the `talos-core` Rust service in the background on port `8010`.
2. Discover the host's active Tailscale IPv4 address.
3. Mint an authorized host API key.
4. Output the exact pairing lines to the user so they can paste them into the mobile app.

---

## 2. One-Liner Quick Bootstrap (Automated)

Any AI agent or operator can run the single script below from the repository root:

```bash
bash scripts/agent_setup.sh
```

This script automatically verifies dependencies, compiles the release binary, starts `talos-core` as a detached daemon, resolves Tailscale IPv4, mints the key, and prints the required pairing output.

---

## 3. Mandatory Agent Behavior & Response Contract

When setting up or pairing the TALOS host, the AI agent **MUST ALWAYS** conclude its response with these exact two lines:

```text
User please enter this in your Tailscale IP section: http://<tailscale-ip>:8010
User please enter this in your Host API Key section: talos_sk_<token>
```

### Prohibited Agent Behaviors:
- **NEVER** ask the user to manually compile Rust code if you have shell/terminal tool access. Do it yourself.
- **NEVER** invent or hallucinate fake Tailscale addresses or random tokens in chat. Always query the running daemon.
- **NEVER** recommend opening external desktop Chrome for mobile pairing when the native app handles it directly.

---

## 4. Manual Step-by-Step Execution Guide

If running step-by-step or troubleshooting in an isolated environment:

### Step 1: Preflight Dependency Check
```bash
for dep in cargo rustc tailscale curl; do
    command -v "$dep" >/dev/null 2>&1 || echo "MISSING: $dep"
done
```

### Step 2: Ensure Tailscale is Active
Tailscale WireGuard overlay is mandatory.
```bash
tailscale status --json | grep -oP '(?<="BackendState":")[^"]*'
# Expected output: Running
```
To obtain the host Tailscale IPv4:
```bash
TS_IP=$(tailscale ip -4)
echo "Host Tailscale IP: $TS_IP"
```

### Step 3: Build & Launch talos-core Daemon
```bash
cd crates/talos-core
cargo build --release --quiet

# Launch in background with disown/nohup on port 8010:
PORT=8010 nohup ./target/release/talos-core >/tmp/talos-core.log 2>&1 &

# Verify health check:
curl -s -f http://127.0.0.1:8010/health
# Expected: {"status":"healthy","uptime_seconds":...}
```

### Step 4: Mint Host Pairing Token
```bash
INVITE_JSON=$(curl -s http://127.0.0.1:8010/api/pairing/invite)
TOKEN=$(echo "$INVITE_JSON" | grep -oP '(?<="token":")[^"]*')

echo "Generated Host Key: $TOKEN"
```

---

## 5. System Boot Persistence (Systemd Service)

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

## 6. Verification & Health SLA

- `GET http://127.0.0.1:8010/health` -> HTTP 200 OK
- `GET http://<tailscale-ip>:8010/health` -> HTTP 200 OK from mobile device
- `POST http://<tailscale-ip>:8010/api/auth/pair` with `{"token": "<token>"}` -> HTTP 200 OK (`{"mesh_verified": true, "status": "paired"}`)
- Invalid key -> HTTP 401 Unauthorized (Zero fake tokens generated)
