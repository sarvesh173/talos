# opencode-talos-control

Tools that let OpenCode see the TALOS host it runs against, and hold a
conversation in one of its sessions.

**Host state** — the machine it is running on:

| Tool | Endpoint | Answers |
|---|---|---|
| `talos_health` | `GET /health` | is the host answering |
| `talos_agents` | `GET /api/agents/roster` | which engines exist, which are installed |
| `talos_processes` | `GET /api/processes` | what the host is supervising |
| `talos_diff` | `GET /api/git/diff` | what changed in the host's repo |
| `talos_mesh` | `GET /api/mesh/status` | peers, addresses, online state |

**Sessions** — the conversation, through the OpenCode server itself:

| Tool | Arguments | Answers |
|---|---|---|
| `talos_sessions` | — | the sessions on this server, newest first, with id and title |
| `talos_prompt` | `session_id`, `text` | sends a message and returns the agent's reply |
| `talos_messages` | `session_id`, `limit` | the recent messages of one session |

The session tools use `OPENCODE_SERVER_PASSWORD`, which `opencode serve`
already has; nothing extra needs provisioning. A prompt is accepted at once and
answered later, so the tool polls until the assistant turn completes or the
provider returns an error — a quota refusal surfaces as text, not a hang.

## Install

```bash
cd plugins/opencode-talos-control
npm install
npm run build     # tsc for the tests, then bun bundles dist/plugin.js
opencode plugin add .
```

Or point it at the checkout:

```jsonc
// ~/.config/opencode/opencode.jsonc
{ "plugins": [ "/home/kira/talos/plugins/opencode-talos-control" ] }
```

The bundle step is not optional. OpenCode's plugin loader does not see
`node_modules` (see below), so a workspace import like `talos-plugin-core` cannot
resolve from source — it has to already be inlined.

## Configure

Environment only. Nothing is read from a file this repo ships.

| Variable | Default | Meaning |
|---|---|---|
| `TALOS_URL` | `http://127.0.0.1:8000` | host base url |
| `TALOS_TOKEN` | — | host API key; **without it no tools register** |
| `TALOS_DISABLED` | — | `1` keeps the plugin loaded but registers nothing |
| `TALOS_TIMEOUT_MS` | `8000` | per-call ceiling |

## When the host is down

Every tool returns the failure as text, not an exception:

- `401` → the key is wrong or revoked
- `404` → the host is running an older build and lacks that endpoint
- timeout → named with the url and the limit
- unreachable → named with the url and the socket error

`talos_health` exists so the model can tell a host problem from a bad request
before concluding anything about the repository.

## How OpenCode finds the entrypoint

Worth knowing before editing the layout, because the failure is silent:

- A **local directory** is resolved at the package root. With a `package.json`
  that has both `name` and `exports`, those are honoured like a published
  package; without them OpenCode looks for `index.ts`/`index.js` at the root.
  That is why the root `index.ts` re-exports `src/index.ts` — a directory whose
  entry lives only in a subdirectory loads nothing and logs nothing
  (opencode#52300, #61262).
- Only the **plugin directory** goes in config or the discovery path, never a
  file inside it.
- Listing the same directory in both `opencode.jsonc` and
  `.opencode/plugins/` loads it twice and reports both as failed.
- **The loader does not resolve `node_modules`.** A plugin that imports a sibling
  workspace package by name fails with `Cannot find package '<name>'`, even when
  `node_modules/<name>` exists both in the package and one directory up, and even
  when plain `node` resolves it fine from the same file. Symlinking it into the
  plugin's own `node_modules` does not help. This is why `dist/plugin.js` is a
  bundle: the shared code is inlined at build time, so there is nothing left to
  resolve except `@opencode/plugin`, which the host provides.
- `@opencode/plugin` belongs in `dependencies`, not `devDependencies`: the
  published package imports it at runtime, so a dev-only entry installs cleanly
  and then fails to import on the user's machine. Every published plugin checked
  does this.

## Develop

```bash
npm install
npm test        # builds, then runs against a loopback HTTP server
npm run build
```
