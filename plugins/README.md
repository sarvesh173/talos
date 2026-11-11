# TALOS plugins

Plugins that connect external CLI agents to TALOS telemetry.

Each plugin lives in its own directory with its own toolchain, tests and README,
so adding one never touches another's dependencies.

| Plugin | Target | Purpose |
|---|---|---|
| [`opencode-talos-bridge`](./opencode-talos-bridge) | OpenCode 2.x | Streams tool calls, results and permission prompts to the Kanban board |
| [`opencode-talos-control`](./opencode-talos-control) | OpenCode 2.x | Read tools so the agent can query the host's agents, processes, diff and mesh |

## Adding a plugin

A plugin directory must contain:

- `README.md` — install, configure, and what it does when the host is down
- `LICENSE`
- tests that run without a live TALOS instance
- no committed credentials, ever

Two rules apply to every plugin here:

1. **Redact before transmitting.** Tool arguments cross a process boundary; a
   key in an argument must not become a row in a database.
2. **Never block the agent.** Telemetry that can stall a coding session is worse
   than no telemetry. Failures are counted and dropped.

## Wiring a new agent

The ingest endpoint is `POST /api/kanban/events` with an
`Authorization: Bearer <host key>` header:

```json
{
  "room_id": "the-council",
  "event": {
    "kind": "tool.start",
    "agentId": "opencode",
    "agentName": "OpenCode",
    "tool": "bash",
    "arguments": { "cmd": "ls" }
  }
}
```

`kind` is `tool.start` or `tool.finish`. A finish closes the most recent open call
for that agent and tool and is acknowledged with `recorded: false` if there is
nothing to close — an orphan finish never creates a fabricated row.
