# Plugin architecture

One core, one adapter per CLI.

A TALOS plugin answers questions about the host the agent is running against:
which agents exist, what is on the board, what changed, is the host up. That
question does not change when the CLI changes. How the CLI is loaded, how a tool
is registered and what its namespace looks like does.

So the plugin is split at that line.

## The split

`shared/` (`talos-plugin-core`) owns everything that is not about a specific CLI:

- `readConfig()` — `TALOS_URL`, `TALOS_TOKEN`, `TALOS_DISABLED`, `TALOS_TIMEOUT_MS`
- `callHost()` — one bounded request, with a timeout that always fires
- `describeFailure()` — 401 and 404 turned into a sentence, not a status code
- `defineTool()` — takes a path and a renderer, returns a runnable tool
- `renderDiff()`, `renderHealth()`, `renderJson()` — payload to text

Each adapter owns the endpoint list and nothing else:

| Adapter | Transport | Tools |
|---|---|---|
| `opencode-talos-control` | OpenCode 2.x plugin (`Plugin.define`, `ctx.tool.transform`) | agents, processes, diff, mesh, health |
| `claude-talos-control` | MCP server over stdio | agents, board, diff, health |

The lists differ on purpose. Claude Code gets the Kanban board because it is the
CLI used to review work; OpenCode gets mesh and processes because it is the CLI
used to drive a fleet. Neither is a subset of the other, and a shared list would
force one of them to carry tools it cannot use.

## Adding a CLI

1. `plugins/<name>-talos-control/`
2. `src/tools.ts` — a list of `defineTool({ name, description, path, render? })`
3. `src/<entry>.ts` — read config, register the tools with that CLI's API,
   register nothing at all when `TALOS_TOKEN` is unset
4. `test/` — tests that do not need a live host
5. `README.md` — install, configure, and what the agent sees when the host is down

The shared core does not change. If it does, the second adapter is wrong, not
the first.

## Rules every adapter inherits

**Fail as text, never as an exception.** A model that reads "TALOS rejected the
API key (401 from /api/agents/roster)" can act. A model that reads a stack trace
cannot.

**Register nothing when unconfigured.** Five tools that each fail with the same
message are worse than no tools and one line saying why.

**Never invent an endpoint.** Every path in an adapter's list is checked against
`server/main.py` before it is written, and called against a live host before it
is committed.

**No credentials in the repo.** Configuration comes from the environment only.

## Why not one plugin with a `--cli` flag

Because the loaders are different. OpenCode discovers a plugin directory and
calls `setup(ctx)`; Claude Code starts an MCP server over stdio. One package
would have to ship both entrypoints and know which one it was loaded as, which is
more code than the two adapters it replaces.