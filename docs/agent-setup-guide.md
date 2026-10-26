# TALOS Agent Setup & Systems Engineering Guide

This document is the source-grounded reference for configuring, running, verifying, and troubleshooting **TALOS** in agentic environments. It provides the concrete facts, commands, and architectural patterns required for generating repo-root `AGENTS.md` (cross-agent standard) and `HERMES.md` (Hermes Agent specific).

All factual claims regarding network topology, dependencies, API contracts, and pairing behavior in this document are verified against the TALOS codebase (`crates/talos-core`, `android/app`, `server/main.py`) and official documentation.

---

## 1. Tailscale Setup for Autonomous Agents

TALOS relies on Tailscale WireGuard overlay networking for secure, zero-config peer-to-peer communication between agent nodes and client devices (such as Android phones).

### 1.1 Detection: Check Before Installing
An agent must never blindly invoke an installation script if a working Tailscale setup is already running.

Check for existing binaries and running processes:
```bash
# 1. Binary location check
which tailscale tailscaled 2>/dev/null

# 2. Check running daemon status via systemd
systemctl is-active tailscaled 2>/dev/null

# 3. Check CLI connectivity and status
tailscale status 2>/dev/null
```

Common binary locations on Linux:
- `/usr/bin/tailscale` and `/usr/sbin/tailscaled` (standard Debian/Ubuntu package)
- `/bin/tailscale` and `/sbin/tailscaled`
- `/usr/local/bin/tailscale` (custom/manual installs)
- `/var/run/tailscale/tailscaled.sock` (local daemon UNIX socket)

### 1.2 Safe Installation Without Clobbering
If Tailscale is not present:
- **Avoid running unpinned shell scripts blindly** (`curl | sh`) if native package managers can provide deterministic repository tracking.
- The official script at `https://tailscale.com/install.sh` detects the OS distro, sets up the official Tailscale package repository, imports the signing key, and installs via `apt`, `dnf`, `pacman`, or `zypper`.
- If using `apt` directly on Debian/Ubuntu systems:
  ```bash
  # Ensure prerequisites
  sudo apt-get update && sudo apt-get install -y curl gpg lsb-release

  # Import official Tailscale repository signing key
  sudo mkdir -p /usr/share/keyrings
  curl -fsSL https://pkgs.tailscale.com/stable/$(lsb_release -is | tr '[:upper:]' '[:lower:]')/$(lsb_release -cs).noarmor.gpg | sudo tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null

  # Add repository list
  curl -fsSL https://pkgs.tailscale.com/stable/$(lsb_release -is | tr '[:upper:]' '[:lower:]')/$(lsb_release -cs).tailscale-keyring.list | sudo tee /etc/apt/sources.list.d/tailscale.list

  # Install package
  sudo apt-get update && sudo apt-get install -y tailscale
  ```
- **Rule:** If `which tailscale` succeeds, skip installation entirely. Do not run package updates or overwrites unless explicitly requested.

### 1.3 Running tailscaled as a Service
Tailscale consists of two components: the background daemon (`tailscaled`) and the control CLI (`tailscale`).
- On systemd-managed Linux systems:
  ```bash
  sudo systemctl enable --now tailscaled
  ```
- In unprivileged containers or environments without systemd:
  ```bash
  # Userspace networking mode or background daemon invocation
  sudo tailscaled --tun=userspace-networking --socks5-server=localhost:1055 &
  ```

### 1.4 Authentication Methods
1. **Interactive Login (Human in the loop):**
   ```bash
   sudo tailscale up --qr
   ```
   - Running `tailscale up` prints a control plane URL (e.g., `https://login.tailscale.com/a/...`).
   - The agent surfaces this URL to the human operator. Once approved in the browser, the daemon transitions to `Running`.
2. **Auth Keys (Fully autonomous / headless):**
   ```bash
   sudo tailscale up --auth-key="${TAILSCALE_AUTH_KEY}" --hostname="talos-node-$(hostname)"
   ```
   - **Secret Handling Rule:** Never log, echo, or store `TAILSCALE_AUTH_KEY` in git, transcripts, or unencrypted world-readable files. Prefer loading via environment variable or passing via file path using `tailscale up --auth-key="file:/path/to/key"`. Delete temporary key files immediately after authentication.
   - Use ephemeral or tagged pre-authorized auth keys in automated test runners.

### 1.5 Verification & Runtime Status Inspection
Use `tailscale status --json` for programmatic verification:
```bash
tailscale status --json
```

Key fields in the status JSON:
- `.BackendState`:
  - `"Running"`: Fully authenticated and connected to the tailnet coordinate server. WireGuard tunnels can be established.
  - `"NeedsLogin"`: Daemon is running but node requires authentication (URL emitted by `tailscale up`).
  - `"Stopped"`: Daemon is running but network access was halted (e.g. `tailscale down`).
  - `"NoState"`: Daemon is initializing or cannot communicate with local socket.
- `.Self.TailscaleIPs`: Array containing IPv4 (`100.x.y.z`) and IPv6 (`fd7a:115c:...`) addresses assigned to this node.
- `.Self.Online`: Boolean indicating connection health to Tailscale infrastructure.
- `.Peer`: Map of remote nodes on the tailnet.

### 1.6 Runtime Overlay Detection (TALOS Core Logic)
TALOS strictly avoids hardcoding IP addresses. The Rust core (`crates/talos-core/src/mesh.rs`) and server implementation verify network health via:
1. Running `tailscale ip -4` to retrieve the current overlay IPv4 address dynamically.
2. Running `tailscale status --json` and parsing `.BackendState == "Running"`.
3. Fallback: Checking the presence of network device interface `/sys/class/net/tailscale0`.
4. Peer counting: Counting reachable nodes from the `.Peer` object in `tailscale status --json`.

**Privacy Invariant in TALOS Core (`crates/talos-core/src/models.rs`):**
The node's host identity (`node_id`, `hostname`, `tailscale_ip`) and individual peer details are marked `#[serde(skip_serializing)]`. When the Android app or external callers query `/api/mesh/status`, TALOS returns only operational metrics (`is_online`, `wireguard_active`, `tailscale_enabled`, `peers_count`, `last_handshake`). The physical machine's identity and raw IPs remain private.

---

## 2. Dependency Preflight Convention

### 2.1 What TALOS Actually Needs
Inspected from repository manifests:
- **`crates/talos-core/Cargo.toml`:**
  - Rust edition 2021
  - Cargo dependencies: `tokio` (1.38, full), `axum` (0.7), `tower`, `tower-http`, `serde`, `serde_json`, `rusqlite` (0.31, bundled SQLite), `uuid`, `sha2`, `hex`, `tracing`, `reqwest`, `chrono`.
  - Native toolchain requirement: `rustc >= 1.75` (edition 2021 support), `cargo`, standard C compiler/linker (`gcc` or `clang` required for compiling bundled `libsqlite3-sys`).
- **`server/main.py` & `server/db.py`:**
  - `python3 >= 3.10`
  - Packages: `fastapi`, `pydantic`, `uvicorn`, `starlette`
  - Python standard library: `sqlite3`, `subprocess`, `asyncio`, `secrets`, `hashlib`.
- **System Utilities:**
  - `git`: Repository operations and status checks.
  - `curl`: Health checks, API verification, downloading toolchains.
  - `sqlite3`: CLI database inspection tool (optional for debugging `talos.db`).
  - `tailscale`: WireGuard overlay daemon and CLI.
  - *Note on `ffprobe`:* Verified across the codebase. `ffprobe` is referenced in optional audio/animation inspection tooling (`.agents/skills`), but is **not** a direct dependency of `talos-core` or `server/main.py`.

### 2.2 The Invariant: Check -> Report -> Install Missing Only -> Re-verify
An autonomous agent must never execute destructive reinstallations or overwrite existing packages:
1. **Check:** Verify presence and executable status of each required tool.
2. **Report:** Output a clear summary of what is detected and what is missing.
3. **Install Missing Only:** If and only if a required component is absent, install that specific dependency using the host's native mechanism (e.g. `apt` or `rustup`).
4. **Re-verify:** Re-run the binary checks to confirm successful installation before proceeding to build or launch.

### 2.3 Copy-Pasteable Preflight Shell Snippet
```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== TALOS Agent Dependency Preflight ==="

MISSING=()

# 1. Check Git
if command -v git >/dev/null 2>&1; then
    echo "[OK] git: $(git --version)"
else
    echo "[MISSING] git"
    MISSING+=("git")
fi

# 2. Check Curl
if command -v curl >/dev/null 2>&1; then
    echo "[OK] curl: $(curl --version | head -n1)"
else
    echo "[MISSING] curl"
    MISSING+=("curl")
fi

# 3. Check Rust toolchain (cargo & rustc)
if command -v cargo >/dev/null 2>&1 && command -v rustc >/dev/null 2>&1; then
    echo "[OK] rustc: $(rustc --version)"
    echo "[OK] cargo: $(cargo --version)"
else
    echo "[MISSING] rust toolchain (cargo/rustc)"
    MISSING+=("rust")
fi

# 4. Check C build tooling (for libsqlite3-sys bundled compilation)
if command -v cc >/dev/null 2>&1 || command -v gcc >/dev/null 2>&1; then
    echo "[OK] C compiler detected"
else
    echo "[MISSING] build-essential / gcc"
    MISSING+=("build-essential")
fi

# 5. Check Python3 (for server scripts/tests)
if command -v python3 >/dev/null 2>&1; then
    echo "[OK] python3: $(python3 --version)"
else
    echo "[MISSING] python3"
    MISSING+=("python3")
fi

# 6. Check Tailscale
if command -v tailscale >/dev/null 2>&1; then
    echo "[OK] tailscale: $(tailscale version | head -n1)"
else
    echo "[MISSING] tailscale"
    MISSING+=("tailscale")
fi

echo "----------------------------------------"
if [ ${#MISSING[@]} -eq 0 ]; then
    echo "Preflight PASS: All core dependencies are available."
    exit 0
else
    echo "Preflight NOTICE: Missing components: ${MISSING[*]}"
    echo "Action required: Install missing components before proceeding."
    exit 1
fi
```

---

## 3. Repo-Root AGENTS.md Specifications

The repo-root `AGENTS.md` is loaded by generic CLI coding agents (Claude Code, OpenAI Codex, OpenCode, Hermes) when operating in the workspace. It must contain concise, unambiguous instructions without conversational fluff.

### 3.1 Required Sections
1. **Project Mission & Architecture Summary:**
   - TALOS is an AI-native multi-agent collaboration hub with a Rust-first core (`crates/talos-core`, Axum HTTP/WebSocket API on port 8000) and an Android M3 Expressive client.
2. **Preflight & Environment Verification:**
   - Execute the preflight checks before running builds or tests.
3. **Build & Service Management:**
   - Build core: `cargo build --manifest-path crates/talos-core/Cargo.toml`
   - Release build: `cargo build --release --manifest-path crates/talos-core/Cargo.toml`
   - Start daemon in background: `crates/talos-core/target/debug/talos-core`
   - Stop daemon: `pkill -f talos-core`
   - Port override: `PORT=8000 TALOS_DB_PATH=talos.db`
4. **Health Check & Verification:**
   - `curl -fsSL http://127.0.0.1:8000/health` -> HTTP 200 `{"status":"operational","engine":"talos-core-rust"}`
   - `curl -fsSL http://127.0.0.1:8000/api/mesh/status` -> HTTP 200 `{"is_online":true,"tailscale_enabled":true}`
5. **Pairing Flow for Android Client:**
   - `POST /api/auth/pair` with `{}` -> returns `{"status":"paired","token":"talos_sk_...","role":"user"}`
   - UDP discovery responder runs on port `8089` (responds to `TALOS_DISCOVER` probes).
6. **Troubleshooting Guide:**
   - Table of errors (e.g. port 8000 in use, UDP 8089 collision, missing Tailscale socket).
7. **Explicit Invariants & DO-NOT Rules:**
   - **DO NOT** hardcode hostnames, overlay IPs, or port numbers in client/server code.
   - **DO NOT** overwrite or clobber an existing Tailscale or Rust installation.
   - **DO NOT** commit secrets, `.env` files, or API tokens (`talos_sk_*`) to git.
   - **DO NOT** force-push to `main` or skip verification tests.

---

## 4. Repo-Root HERMES.md Specifications

`HERMES.md` contains Hermes Agent specific conventions, taking advantage of Hermes's built-in platform features (skills, background processes, delegation models, and session management).

### 4.1 Hermes Skill Conventions
- Skills live under `$HERMES_HOME/skills/<category>/<skill-name>/` or profile skill directories.
- Structure: `SKILL.md` with strict YAML frontmatter (`name`, `description`).
  - The first 57 characters of `description` must form a complete self-contained trigger: `"Use when <trigger>. <one-line behavior>."`
- Load convention: Always load relevant skills using `skill_view(name='...')` before modifying code or diagnosing issues.
- Maintain procedural clarity: Skills encode rules, proven workflows, and command sequences—not historical narrations or diary logs.

### 4.2 Background Service Execution in Hermes
Hermes CLI agents operate via tool-call loops (`terminal`). To keep the TALOS core service running persistently:
- Use the `terminal` tool with `background=true`:
  ```python
  terminal(command="/home/kira/talos/crates/talos-core/target/debug/talos-core", background=True)
  ```
- Alternatively, launch via process detachment (`setsid` / `nohup`):
  ```bash
  setsid /home/kira/talos/crates/talos-core/target/debug/talos-core > talos.log 2>&1 &
  ```
- Always verify health immediately via `curl http://127.0.0.1:8000/health` in a follow-up step.
- Database location: Defaults to `talos.db` in working directory unless overridden with `TALOS_DB_PATH`.

### 4.3 Subagent Delegation Models
When orchestrating sub-tasks in Hermes:
- Use `delegate_task(goal="...", context="...")` for focused sub-actions (e.g. auditing M3 tokens, checking tests).
- Subagent models:
  - Fast analysis: `agy/gemini-2.5-flash` or `openrouter/google/gemini-2.5-flash`
  - Complex systems code / Rust refactoring: High-tier reasoning models (`anthropic/claude-3-7-sonnet`, `deepseek/deepseek-r1`).
- Concurrency limit: Configured in `~/.hermes/config.yaml` (`delegation.max_concurrent_children: 3`).

### 4.4 Agent-First Pairing Flow
TALOS is designed for zero human manual network configuration:
1. Agent starts `talos-core`.
2. Core initializes the HTTP server on port 8000 and the UDP discovery beacon on port 8089.
3. The Android app (`MeshDiscovery.kt`) broadcasts `TALOS_DISCOVER` on port 8089 upon startup.
4. Core replies with `{"service":"talos-core","port":8000,"version":"0.2.0"}`.
5. Android client receives the beacon, calls `POST /api/auth/pair`, receives a minted `talos_sk_*` token, and immediately establishes WebSocket connection to `/ws/the-council/android-client?token=...`.
6. Fallback pairing: The agent can mint a pairing token directly via `curl -X POST http://127.0.0.1:8000/api/auth/pair` and provide it to the client.

---

## 5. Golden Path: End-to-End Autonomous Sequence

This is the exact sequence a fresh agent executes on a Linux machine to go from clean state to verified operational core:

```bash
# 1. Clone repository (if not already present)
git status || git clone <repo-url> /home/kira/talos
cd /home/kira/talos

# 2. Preflight dependency check
which git curl cargo rustc python3 tailscale || echo "Check missing dependencies above"

# 3. Ensure Tailscale is running (login via QR/URL if unauthenticated)
tailscale status || sudo tailscale up --qr

# 4. Verify Tailscale mesh state
tailscale status --json | grep -q '"BackendState": "Running"' && echo "Tailscale mesh operational"

# 5. Build TALOS Rust Core
cargo build --manifest-path crates/talos-core/Cargo.toml

# 6. Start TALOS Core in the background
crates/talos-core/target/debug/talos-core &
CORE_PID=$!
sleep 1

# 7. Verify Health endpoint
curl -fsSL http://127.0.0.1:8000/health
# Expected: {"design_system":"Material 3 Expressive (M3E)","engine":"talos-core-rust","status":"operational",...}

# 8. Verify Mesh & Overlay status
curl -fsSL http://127.0.0.1:8000/api/mesh/status
# Expected: {"is_online":true,"tailscale_enabled":true,"wireguard_active":true,...}

# 9. Verify Auth & Pairing endpoint
curl -fsSL -X POST http://127.0.0.1:8000/api/auth/pair -H "Content-Type: application/json" -d '{}'
# Expected: {"masked_token":"talos_sk_...","mesh_verified":true,"role":"user","status":"paired","token":"talos_sk_..."}

# 10. Clean up test process (if running as one-shot check)
kill $CORE_PID 2>/dev/null || true
```

---

## 6. Verification Status Matrix

| Component / Claim | Verification Source | Status |
|---|---|---|
| Rust Core Binary & Axum Endpoints | `crates/talos-core/src/main.rs` & Cargo build test | **VERIFIED** |
| `/health` response schema | Real execution test against compiled binary | **VERIFIED** |
| `/api/mesh/status` response schema | Real execution test against compiled binary | **VERIFIED** |
| `/api/auth/pair` response schema | Real execution test against compiled binary | **VERIFIED** |
| Tailscale runtime detection logic | `crates/talos-core/src/mesh.rs` & CLI testing | **VERIFIED** |
| Tailscale privacy invariant (`skip_serializing`) | `crates/talos-core/src/models.rs` | **VERIFIED** |
| Zero-config UDP beacon (port 8089) | `crates/talos-core/src/discovery.rs` & `MeshDiscovery.kt` | **VERIFIED** |
| Android pairing & auth flow | `android/app/.../network/TalosClient.kt` | **VERIFIED** |
| Hermes Agent skill & context rules | `hermes-agent` skill reference & docs | **VERIFIED** |
| FFprobe dependency in core engine | Searched in `talos-core` and `server/` (absent) | **VERIFIED (Not a core dependency)** |
| Python 3 requirements in core engine | Rust core has zero Python dependencies; Python only in `server/` legacy tests | **VERIFIED** |
