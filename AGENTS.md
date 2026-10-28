# AGENTS.md — TALOS

> TALOS is an **agent-only** system: an Android client that AI agents drive, plus a
> Rust core (`crates/talos-core`) that runs on the user's machine. A human should
> never have to wire this up manually — **you** (the agent) do it, and you only ask
> the human for the one thing you cannot do for them: **signing in to Tailscale**
> with their account.

Read this whole file before touching the repo. It is short on purpose.

---

## 0. Non-negotiable rules

| Rule | Why |
|---|---|
| **Never hardcode an IP, hostname, device id or token** in code. Everything is discovered at runtime. | The owner's IPs change constantly (hotspot / Wi-Fi / cellular / Tailscale). Hardcoding breaks the product for every other user. |
| **Never leak host system information into the app UI.** Hostname, overlay IP, peer names, OS names, node ids are private data. The core must not serialize them. | Privacy: the phone app is a client surface, not a system monitor. |
| **Check before installing.** Report what is missing, install only what is missing, never overwrite a working install. | The host may already have a working toolchain; clobbering it breaks unrelated work. |
| **Never force-push to `main`/`dev`.** Small commits, push often, PR per phase. | Shared repo. |
| **Never commit secrets** (Tailscale auth keys, tokens, `.env` contents). | Repo is on GitHub. |
| **CI builds the APK.** Do not run `assembleRelease` on the user's laptop; it is a slow, low-RAM machine. Use `./gradlew :app:compileReleaseKotlin` as a local check only. | The user explicitly asked for cloud builds. |

---

## 1. Preflight — dependency check FIRST

Run this before installing anything:

```bash
for dep in git curl cargo rustc python3 pip sqlite3 tailscale; do
  command -v "$dep" >/dev/null 2>&1 || echo "MISSING: $dep"
done
```

What the project actually needs:

| Dependency | Needed for | If missing |
|---|---|---|
| `git` | clone/pull | install |
| `cargo` + `rustc` | building `talos-core` | install rustup (`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`) |
| `curl` | health checks + downloads | install |
| `python3` | helper scripts, tests | install |
| `sqlite3` | dev inspection of `talos.db` (runtime uses `rusqlite` — bundled) | optional |
| `tailscale` | **the private overlay that carries TALOS traffic** | **ask the human to install it** (see §2) |

**Invariant:** `check → report → install only what is missing → re-verify → continue`.
Never run an installer that overwrites an existing working install without saying so first.

---

## 2. Tailscale — the one dependency the human must own

TALOS traffic travels over the Tailscale overlay. Without it there is **no encryption
layer**, so the app refuses to run — this is deliberate, not a bug.

### 2.1 Check

```bash
command -v tailscale && tailscale status --json | head -40
```

`tailscale status --json` → `BackendState` must be `"Running"`.

### 2.2 If Tailscale is missing

Do **not** silently install it and move on. Tell the human exactly this:

> TALOS needs Tailscale on both the phone and this machine — it carries the encrypted
> link between your phone and the agents on this host.
> 1. Download Tailscale on your phone (Google Play / App Store) and on this machine.
> 2. Sign in with the **same account** on both.
> 3. Tell me when you are signed in — I will do the rest.

Then wait. Everything else in this file can be done before they answer.

### 2.3 If Tailscale is installed but not authenticated

```bash
sudo tailscale up --qr     # prints a login URL / QR the human scans
```

Ask the human to open it, then re-check `BackendState`.

### 2.4 Installing Tailscale without clobbering

Only when it is genuinely absent, and only after the human approved:

```bash
# Debian/Ubuntu - official repo, does not touch other packages
curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/tailscale.gpg | sudo tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null
curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/tailscale.list | sudo tee /etc/apt/sources.list.d/tailscale.list
sudo apt-get update && sudo apt-get install -y tailscale
sudo systemctl enable --now tailscaled
```

Never run an installer that removes or replaces an existing Tailscale package.

---

## 3. Build and run the core

```bash
cd /home/kira/talos
CARGO_BUILD_JOBS=2 cargo build --manifest-path crates/talos-core/Cargo.toml
```

Run it as a **long-lived background service** (it must survive your session):

```bash
# foreground test
PORT=8010 crates/talos-core/target/debug/talos-core
# background service (preferred)
sudo systemctl --user enable --now talos-core.service   # see scripts/talos-core.service
# or, without systemd
setsid env PORT=8010 crates/talos-core/target/debug/talos-core \
  > /tmp/talos-core.log 2>&1 < /dev/null &
```

### Verify (all three must pass)

```bash
curl -fsSL http://127.0.0.1:8010/health
# {"status":"operational","engine":"talos-core-rust",...}

curl -fsSL http://127.0.0.1:8010/api/mesh/status
# {"is_online":true,"wireguard_active":true,"tailscale_enabled":true,"peers_count":N,...}

# zero-config discovery beacon (the phone uses this)
python3 - <<'PY'
import socket, json
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(3)
s.sendto(b"TALOS_DISCOVER", ("127.0.0.1", 8089))
print(json.loads(s.recvfrom(1024)[0]))
PY
# {"service":"talos-core","port":8010,"version":"0.2.0"}
```

If `tailscale_enabled` is `false`, the overlay is down — fix Tailscale (§2), do not
work around it in code.

---

## 4. Pairing the Android app

## 4. Host-Authorized Pairing & API Keys

The laptop/computer host is the **sole authority** that mints, registers, and validates API keys:

1. **User Action**: The human activates Tailscale on both their phone and host.
2. **Host Minting**: The host machine generates the official device API key (`talos_sk_...`) using `scripts/pairing_link.sh`, the core API `POST /api/tokens`, or the built-in browser pairing endpoint `http://<host>:8010/pair`.
3. **App Input**: The mobile app never fabricates or mints tokens locally. In the setup wizard (Step 3), the user either:
   - **Taps the One-Tap Pairing Link**: Opens `talos://pair?url=...&token=...` or `http://<host>:8010/pair` in Chrome on the phone, which auto-configures and pairs in 1 tap.
   - **Pastes the Host Key**: Manually pastes the `talos_sk_...` key generated by their host machine into the "Host API Key" field.
4. **Resolution**: The app communicates over Tailscale WireGuard (`100.64.0.0/10` CGNAT range) with zero hardcoded IPs.

```bash
# Generate a fresh one-time pairing link + API key on the host:
scripts/pairing_link.sh                      # prints talos://pair?url=...&token=...
# Or open in Chrome on phone:
# http://<tailscale-host-ip>:8010/pair
```

---

## 5. Android build & ship

```bash
cd /home/kira/talos/android
GRADLE_OPTS="-Xmx1024m -Dorg.gradle.workers.max=2" ./gradlew :app:compileReleaseKotlin
```

Toolchain is pinned (do not "upgrade" casually):

- AGP `8.9.1`, Gradle `8.11.1`, `compileSdk 36`, `minSdk 26`
- `material3 1.5.0-alpha12` (explicit pin — the BOM alone resolves 1.3.2)
- `graphics-shapes 1.1.0` for the M3E shape-morph engine

Ship by pushing a version tag; GitHub Actions builds and publishes:

```bash
git checkout dev && git merge --ff-only feature/<branch>
git push origin dev
git tag -a v2.7.0 -m "..." && git push origin v2.7.0
```

Verify the release at `https://github.com/sarvesh173/talos/releases` — the rolling
release **TALOS Latest Build** must expose `talos-latest.apk`.

---

## 6. Repo map

| Path | What it is |
|---|---|
| `crates/talos-core/` | Rust core: axum HTTP API, SQLite, WebSocket bus, theme API, UDP discovery, Tailscale detection |
| `android/app/src/main/java/com/sarvesh173/talos/ui/` | Compose UI: `Theme.kt`, `ExpressiveComponents.kt`, `DynamicTheme.kt`, `ServiceGate.kt`, `Adaptive.kt` |
| `android/app/src/main/java/com/sarvesh173/talos/network/` | `TalosClient.kt` (HTTP/WS + endpoint resolution), `MeshDiscovery.kt` (UDP beacon) |
| `server/main.py` | Legacy Python core — kept for reference/compat; the Rust core is the product |
| `docs/` | Design specs, ADRs, split-screen and agent-setup research |
| `scripts/` | systemd unit + helper scripts for running the core as a service |
| `PLAN.md` | Phase plan and real completion percentages — keep it honest |

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| App shows "Tailscale required" but Tailscale is on | Core is too old / not reporting `tailscale_enabled`, or the app can't reach the core | Rebuild + restart the core; verify `curl /api/mesh/status` shows `tailscale_enabled: true` |
| App shows "Node unreachable" | Node moved ports or discovery is blocked | The app retries stored endpoint → same host on standard ports → beacon. Confirm the core is listening and the beacon answers |
| Wizard stuck on "Scanning private mesh..." | A probe hung (old client had no read timeout) | Client now has an 8s probe timeout and a 12s resolver ceiling — rebuild the app |
| `cargo build` fails on `libsqlite3` | missing C toolchain | `sudo apt-get install -y build-essential pkg-config` |
| CI fails on Gradle | lockfile/package.json drift | `npm ci` in CI requires `package-lock.json` to match `package.json` |
| Android 16 letterboxes the app | `maxAspectRatio` / `minAspectRatio` set, or `resizeableActivity` missing | Keep `android:resizeableActivity="true"` and **omit** aspect-ratio attributes |

---

## 8. Reporting

When you finish a phase, report: what changed, the real command output that proves it,
what is still open. Use the repo's `PLAN.md` percentages honestly — a plausible claim
without evidence is worse than an unfinished task.
