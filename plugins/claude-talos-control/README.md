# claude-talos-control

Gives Claude Code read access to the TALOS host it is running against, so a
session can answer "what is on the board", "what changed", "is the host up" from
the host's own state instead of guessing from the repository.

The transport, the timeout and the error mapping come from
[`talos-plugin-core`](../shared). The endpoint list is this plugin's own — see
[the architecture note](../PLUGIN_ARCHITECTURE.md) for why the split sits there.

## Tools

| Tool | Endpoint | Answers |
|---|---|---|
| `agents` | `GET /api/agents/roster` | which engines exist, which are installed, where the binary is |
| `board` | `GET /api/kanban/board` | the lanes and the agent activity behind them |
| `diff` | `GET /api/git/diff` | uncommitted changes, grouped by file |
| `health` | `GET /health` | is the host answering |

All four are reads. Nothing here can change the host.

## Install

```bash
cd plugins
npm install
npm run build --workspace claude-talos-control
```

Then point Claude Code at the plugin directory:

```bash
claude plugin add ./plugins/claude-talos-control
```

Or add it to a marketplace. The manifest is `.claude-plugin/plugin.json` and the
server is declared in `.mcp.json` at the plugin root.

## Configure

Environment only. Nothing is read from a file this repo ships.

| Variable | Default | Meaning |
|---|---|---|
| `TALOS_URL` | `http://127.0.0.1:8000` | host base url |
| `TALOS_TOKEN` | — | host API key; **without it no tools register** |
| `TALOS_DISABLED` | — | `1` keeps the plugin loaded but registers nothing |

`TALOS_TOKEN` is passed through `.mcp.json` from the environment Claude Code
already has. Export it before starting Claude Code; the server does not read a
file and does not prompt.

## When the host is down

Every tool returns the failure as text, not an exception:

- `401` — the key is wrong or revoked
- `404` — the host is running an older build and lacks that endpoint
- timeout — named with the url and the limit
- unreachable — named with the url and the socket error

`health` exists so a host problem can be told apart from a bad request before
concluding anything about the repository.

## Develop

```bash
npm test          # builds, then runs against a loopback HTTP server
npm run typecheck
```

The tests do not need a live TALOS host. To check against one:

```bash
TALOS_TOKEN=... TALOS_URL=http://127.0.0.1:8000 node dist/server.js
```

then speak JSON-RPC on stdin — `initialize`, `notifications/initialized`,
`tools/list`.