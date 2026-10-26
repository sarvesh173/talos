# HERMES.md — TALOS for Hermes Agent

> Hermes-specific instructions for driving TALOS. The generic, agent-agnostic
> contract lives in [`AGENTS.md`](./AGENTS.md) — read that first, this file only
> covers what is different when **you are Hermes**.

---

## 1. What TALOS is

TALOS = Rust core (`crates/talos-core`) + Android client, joined over a Tailscale
overlay. The Android app is the human's control surface; the core is what agents
actually talk to over HTTP/WebSocket.

Ports and services on the host:

| Service | Port | Notes |
|---|---|---|
| `talos-core` HTTP/WS API | `8000` default, `8010` in this deployment | `PORT` env var |
| UDP discovery beacon | `8089` | answers `TALOS_DISCOVER` with `{service, port, version}` |
| Tailscale overlay | — | required; carries the encrypted transport |

---

## 2. Hermes skill conventions (how to add TALOS knowledge to Hermes)

- A skill is a directory with `SKILL.md` whose frontmatter has **`name`** and
  **`description`**. The description is the trigger — write it as
  `Use when <trigger>. <one-line behavior>.`
- Skills live in the **active profile's** skills directory
  (`~/.hermes/skills/<category>/<skill-name>/SKILL.md` for the default profile;
  other profiles live under `~/.hermes/profiles/<name>/skills/`).
- **Load a skill with `skill_view(name=...)` before acting on anything it covers.**
  Do not guess its contents from memory.
- Skills hold *procedure*, not narration: imperative rule + why, no incident logs,
  no dates, no PR numbers.
- Supporting files go under `references/`, `templates/`, `scripts/`, `assets/`
  inside the skill directory and are loaded with `skill_view(name=..., file_path=...)`.
- The `hermes-agent` skill is the authoritative reference for Hermes configuration.

---

## 3. Running the core as a long-lived background service

The core must outlive a single turn. Two supported ways:

### 3.1 systemd user unit (preferred — survives reboot)

`scripts/talos-core.service` ships with the repo:

```bash
sudo cp scripts/talos-core.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now talos-core.service
systemctl --user status talos-core.service
journalctl --user -u talos-core.service -n 50 --no-pager
```

The unit waits for `tailscaled` to be running before starting the core, so the
overlay is always up before the app can pair.

### 3.2 Plain background process

```bash
cd /home/kira/talos/crates/talos-core
setsid env PORT=8010 ./target/debug/talos-core > /tmp/talos-core-8010.log 2>&1 < /dev/null &
disown
```

Use `setsid` + `disown` (not a bare `&`) so the process survives the gateway
session ending. In Hermes, `terminal(background=true)` is also fine for
long-lived servers — it tracks the process — but a systemd unit is better when
the machine reboots.

**Health-check after any start:**

```bash
curl -fsSL http://127.0.0.1:8010/health
curl -fsSL http://127.0.0.1:8010/api/mesh/status
```

---

## 4. Dependency preflight (Hermes wording)

Before installing anything, report a table of `present / missing` for
`git, curl, cargo, rustc, python3, pip, sqlite3, tailscale`, then install only
the missing ones. Never overwrite a working install. If **Tailscale** is missing,
stop and ask the human to install + sign in on the phone and the host — everything
else can proceed without them.

---

## 5. Delegation model policy for this repo

Hermes delegates background work through `~/.hermes/config.yaml`:

```yaml
delegation:
  provider: "custom:omnirouter"
  model: "agy/gemini-3.8-flash-tiered"
  reasoning_effort: "low"
```

Set it with the CLI (**never** hand-edit `config.yaml` — the runtime blocks it):

```bash
hermes config set delegation.provider custom:omnirouter
hermes config set delegation.model agy/gemini-3.8-flash-tiered
hermes config set delegation.reasoning_effort low
```

Before pinning a model, verify it returns a populated `tool_calls` on a trivial
request — a 200 with empty content is not a working delegation model. If the
provider reports *"all credentials are cooling down"*, that is a **provider-side
quota state, not a config error**: wait and retry, or reduce the batch into
smaller briefs rather than re-pinning models.

---

## 6. Agent-first pairing flow (no human typing)

1. Ensure the overlay is up: `tailscale status --json | grep BackendState`.
2. Start the core (§3) and verify `/health` and `/api/mesh/status`.
3. The app auto-discovers the core over UDP `8089` and pairs itself with
   `POST /api/auth/pair`. **The human never types an IP.**
4. If discovery cannot reach the host (e.g. the phone is on cellular data and the
   LAN beacon does not route), the app falls back to the endpoint it already
   stored, then to the same host on the standard TALOS ports. Never hardcode an
   address into the app to work around this — fix reachability.

---

## 7. Messaging the user from Hermes

- This project is agent-only: prefer one-line status updates over setup
  tutorials. The one thing the human must do is sign in to Tailscale.
- Report real command output, not claims. Include the release URL once CI
  publishes it.
- Keep the repo's `PLAN.md` percentages honest.

---

## 8. Quick reference

```bash
# core lifecycle
systemctl --user restart talos-core.service
curl -fsSL http://127.0.0.1:8010/health

# android compile check (never a local APK build - CI does that)
cd /home/kira/talos/android && GRADLE_OPTS="-Xmx1024m -Dorg.gradle.workers.max=2" \
  ./gradlew :app:compileReleaseKotlin

# ship
git tag -a v2.7.0 -m "..." && git push origin v2.7.0
# release: https://github.com/sarvesh173/talos/releases
```
