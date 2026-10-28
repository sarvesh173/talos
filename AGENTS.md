# AGENTS.md — TALOS CENTRAL AGENT DIRECTORY & MASTER REPO MAP

> **TALOS is an agent-orchestrated system**: an Android companion client communicating with a high-performance Rust core (`crates/talos-core`) over an encrypted private Tailscale WireGuard overlay.
> 
> **Master Rule for AI Agents**: This repository is designed to be completely navigable by AI agents (OmniRush, Claude Code, Hermes, OpenCode, Codex). **Never guess procedures.** Use the specialized guides linked below to execute specific setup, networking, or development tasks.

---

## 🧭 Master Knowledge Index & Linked Setup Guides

| Guide | File / Link | When to Check & What it Covers |
|---|---|---|
| **Host Setup & Pairing PRD** | [`INSTALLING.md`](./INSTALLING.md) | **MANDATORY FOR SETUP.** Complete PRD for booting the host daemon, managing the single persistent 16-digit API key, Tailscale IP output format, and user pairing lines. |
| **Feature Catalog & Spec** | [`FEATURE.md`](./FEATURE.md) | Spec-first intake directory, active architecture layers, M3 Expressive tokens, native browser integration, and dynamic IP updating. |
| **Tailscale & Network Architecture** | [`docs/MULTI_AGENT_MESH_ARCHITECTURE.md`](./docs/MULTI_AGENT_MESH_ARCHITECTURE.md) | Deep dive into WireGuard overlay topology, Tailscale daemon lifecycle, port resolution (`8010`/`8089`), and zero-trust auth. |
| **Comprehensive Agent Onboarding** | [`docs/agent-setup-guide.md`](./docs/agent-setup-guide.md) | Step-by-step handbook for configuring subagents, multi-agent dispatch pipes, and prompt injection hardening. |
| **Hermes Gateway & Tool Sync** | [`HERMES.md`](./HERMES.md) | Instructions for bridging Hermes Agent CLI, tool schemas, and local MCP nodes into TALOS. |
| **Material 3 Expressive System** | [`docs/DESIGN_SYSTEM_M3.md`](./docs/DESIGN_SYSTEM_M3.md) | Strict guidelines for metallic styling, M3E container shapes, surface roles, and anti-slop mobile UX. |
| **Motion & Animation Physics** | [`docs/MOTION_SPEC.md`](./docs/MOTION_SPEC.md) | Spring specs (`dampingRatio = 0.82f`, `stiffness = 380f`), gesture transitions, and drawer physics. |
| **Split-Screen & Tablet Support** | [`docs/split-screen-android.md`](./docs/split-screen-android.md) | Android multi-window, OEM letterboxing fixes, and foldables adaptive geometry. |
| **Rust Architectural Decisions** | [`docs/ADR-001-RUST-STACK-AND-M3E-SYSTEM.md`](./docs/ADR-001-RUST-STACK-AND-M3E-SYSTEM.md) | ADR outlining why Axum + SQLite + Tokio replaced the legacy Python engine for low latency. |

---

## 0. Non-Negotiable Operational Rules

| Rule | Why |
|---|---|
| **Never hardcode an IP, hostname, device id or token** in code. Everything is discovered at runtime. | The owner's IPs change constantly (hotspot / Wi-Fi / cellular / Tailscale). Hardcoding breaks the product for every other user. |
| **Single Host API Key Rule**: Only ONE persistent key per host. | Preserves credentials in `~/.config/talos/credentials.json` so mobile app uninstalls/reinstalls reuse the exact same key without minting duplicates. |
| **Never leak host system information into the app UI.** Hostname, overlay IP, peer names, OS names, node ids are private data. The core must not serialize them. | Privacy: the phone app is a client surface, not a system monitor. |
| **Check before installing.** Report what is missing, install only what is missing, never overwrite a working install. | The host may already have a working toolchain; clobbering it breaks unrelated work. |
| **Never force-push to `main`/`dev`.** Small commits, push often, PR per phase via SSH. | Shared repo safety. |
| **Never commit secrets** (Tailscale auth keys, tokens, `.env` contents). | Public GitHub security. |
| **CI builds the APK.** Do not run `assembleRelease` on the user's laptop; it is a slow, low-RAM machine. Use `./gradlew :app:compileReleaseKotlin` as a local check only. | Preserves host battery and RAM. |

---

## 1. Quick Setup: How to Boot TALOS & Pair Mobile App

Whenever the user asks to **"set up the app"**, **"run the host"**, or **"pair the phone"**, execute the universal host manager:

```bash
# Cross-platform one-liner (Linux, macOS, WSL, Windows):
bash scripts/agent_setup.sh
# or:
python3 scripts/agent_setup.py
```

### What this command guarantees:
1. Checks preflight requirements (`cargo`, `tailscale`, `curl`, `python3`).
2. Compiles and runs `talos-core` as a detached daemon on port `8010`.
3. Resolves the active host **Tailscale IPv4 address** (`100.x.y.z`).
4. Reuses the existing single key from `~/.config/talos/credentials.json` (or generates a random 16-digit key if fresh).
5. **Mandatory Final Output**: The agent must finish with these exact two lines:
   ```text
   User please enter this in your Tailscale IP section: http://<tailscale-ip>:8010
   User please enter this in your Host API Key section: talos_sk_<token>
   ```

*(For full details on key rotation `--rotate` or custom key input `--key`, refer to [`INSTALLING.md`](./INSTALLING.md)).*

---

## 2. How to Set Up Tailscale (The Mandatory Mesh Overlay)

TALOS traffic travels over the Tailscale overlay. Without it there is **no encryption layer**, so the app refuses to run.

### 2.1 Verify Tailscale State
```bash
tailscale status --json | grep -oP '(?<="BackendState":")[^"]*'
# Must return: Running
```

### 2.2 If Tailscale is Missing on Host
Do **not** silently install it and move on. Tell the human:
> TALOS needs Tailscale on both the phone and this machine — it carries the encrypted link between your phone and the agents on this host.
> 1. Download Tailscale on your phone and on this machine.
> 2. Sign in with the **same account** on both.
> 3. Tell me when you are signed in — I will do the rest.

### 2.3 If Tailscale Needs Authentication
```bash
sudo tailscale up --qr     # prints a login URL / QR for the user
```

*(For deep network troubleshooting and routing topology, refer to [`docs/MULTI_AGENT_MESH_ARCHITECTURE.md`](./docs/MULTI_AGENT_MESH_ARCHITECTURE.md)).*

---

## 3. How to Build & Verify the Core (`crates/talos-core`)

```bash
cd crates/talos-core
cargo build --release --quiet

# Verify running endpoints (all three must return HTTP 200 OK):
curl -fsSL http://127.0.0.1:8010/health
curl -fsSL http://127.0.0.1:8010/api/mesh/status
curl -fsSL -X POST http://127.0.0.1:8010/api/auth/pair -H "Content-Type: application/json" -d '{"token":"<16_DIGIT_KEY>"}'
```

---

## 4. How to Verify Android Code Changes

Before pushing any commit to GitHub:
```bash
cd android
GRADLE_OPTS="-Xmx1024m -Dorg.gradle.workers.max=2" ./gradlew :app:compileReleaseKotlin --no-daemon
```

Push all verified changes to `feature/m3-expressive-rust-first`, merge into `dev` and `main` via fast-forward, tag a release (e.g. `v2.8.x`), and GitHub Actions will automatically compile the release APK and publish to releases.

---

## 5. Repository File Directory

```text
talos/
├── AGENTS.md                  <-- Central directory and cross-linking index (Start Here)
├── INSTALLING.md              <-- Host setup, 16-digit API keys & pairing PRD
├── FEATURE.md                 <-- Live feature tracker, M3 tokens & spec intake
├── HERMES.md                  <-- Hermes CLI integration & tool gateway
├── docs/
│   ├── MULTI_AGENT_MESH_ARCHITECTURE.md  <-- Tailscale overlay & network protocol
│   ├── DESIGN_SYSTEM_M3.md               <-- Metallic styling & container specs
│   ├── MOTION_SPEC.md                    <-- Animation curves, springs & physics
│   ├── agent-setup-guide.md              <-- Agent dispatch & multi-agent handbook
│   ├── split-screen-android.md           <-- Multi-window & adaptive layouts
│   └── ADR-001-RUST-STACK-AND-M3E-SYSTEM.md <-- Rust core architecture decision
├── crates/
│   └── talos-core/            <-- Rust Axum service, SQLite DB & auth engine
├── android/                   <-- Jetpack Compose Android companion application
└── scripts/
    ├── agent_setup.sh         <-- Universal POSIX shell setup wrapper
    └── agent_setup.py         <-- Universal cross-platform setup & key manager
```
