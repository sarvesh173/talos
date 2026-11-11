# opencode-talos-control

Read tools that let OpenCode see the TALOS host it runs against.

| Tool | Endpoint | Answers |
|---|---|---|
| `talos_health` | `GET /health` | is the host answering |
| `talos_agents` | `GET /api/agents/roster` | which engines exist, which are installed |
| `talos_processes` | `GET /api/processes` | what the host is supervising |
| `talos_diff` | `GET /api/git/diff` | what changed in the host's repo |
| `talos_mesh` | `GET /api/mesh/status` | peers, addresses, online state |

## Install

```bash
opencode plugin add ./plugins/opencode-talos-control
```

Or point it at the checkout:

```jsonc
// ~/.config/opencode/opencode.jsonc
{ "plugins": [ "/home/kira/talos/plugins/opencode-talos-control" ] }
```

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
