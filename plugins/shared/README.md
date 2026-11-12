# talos-plugin-core

The CLI-independent half of every TALOS plugin: reading the host's configuration,
calling it with a timeout, turning failures into text a model can act on, and the
shape a tool takes.

Adapters depend on this; this depends on nothing. See
[the architecture note](../PLUGIN_ARCHITECTURE.md).

## Exports

| Export | Purpose |
|---|---|
| `readConfig(env?)` | `HostConfig` from `TALOS_URL`, `TALOS_TOKEN`, `TALOS_DISABLED`, `TALOS_TIMEOUT_MS` |
| `callHost(config, path, init?)` | one bounded request; never throws, never hangs |
| `describeFailure(path, status, error?)` | 401 and 404 as sentences |
| `defineTool(spec)` | `{ name, description, path, render? }` to a runnable tool |
| `renderJson`, `renderDiff`, `renderHealth` | payload to text |
| `Tool`, `ToolSpec`, `ToolResult`, `Render` | types |

## The two rules

**`callHost` returns a result, it does not throw.** A model that reads "TALOS
rejected the API key (401 from /api/agents/roster)" can act on it. An exception
becomes a stack trace it cannot use.

**Without a token, `readConfig` reports `enabled: false`.** Every call would be
the same 401, so the adapter registers nothing and says why once.

## Develop

```bash
npm test          # builds, then runs against a loopback HTTP server
npm run typecheck
```