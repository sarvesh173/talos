# opencode-talos-bridge

An OpenCode plugin that streams tool activity to [TALOS](../..) so the Kanban
board shows what the agent is actually doing instead of a spinner.

```
tool.execute.before  ──►  redact  ──►  POST /api/kanban/events  ──►  TALOS board
tool.execute.after   ──►           (fire-and-forget)
```

## Why

An agent that says "working…" for ninety seconds is indistinguishable from one
that is stuck. TALOS already keeps a tool-call ledger; this plugin is what feeds
it from the OpenCode side. Every call becomes a row with the tool name, a redacted
argument preview, a duration and an outcome.

## Install

```bash
opencode plugin add opencode-talos-bridge
```

From a local checkout:

```bash
opencode plugin add /home/kira/talos/plugins/opencode-talos-bridge
```

Verify it loaded:

```bash
opencode plugin list
```

## Configure

Environment only. Nothing is written to disk by this plugin, and no credential is
stored or logged.

| Variable | Default | Meaning |
|---|---|---|
| `TALOS_URL` | `http://127.0.0.1:8010` | TALOS host daemon |
| `TALOS_TOKEN` | *(none)* | Host API key. **Without this the plugin stays disabled.** |
| `TALOS_ROOM_ID` | `the-council` | Which room the calls land in |
| `TALOS_TIMEOUT_MS` | `4000` | Per-event HTTP timeout |
| `TALOS_DISABLED` | *(unset)* | `1` turns the bridge off even with a token |

Put them in your shell profile or the service unit's `Environment=` lines. Do not
commit them.

```bash
export TALOS_URL="http://100.75.107.79:8010"
export TALOS_TOKEN="talos_sk_..."
```

## Behaviour when TALOS is down

The plugin never blocks or fails an agent turn. A failed POST increments a counter
and is otherwise dropped:

* telemetry is not worth stalling a coding session for;
* the counters are exposed so a health check can say "we are dropping events"
  rather than pretending everything is fine.

Set `TALOS_DISABLED=1` to stop even trying.

## Redaction

Tool arguments are scrubbed **before they leave the process**, and again on the
server. Two layers because a single missed pattern should not be enough to leak a
key into SQLite and out over a websocket to a phone.

Redacted: `sk-*`, `sk-or-v1-*`, `sk-ant-*`, `github_pat_*`, `gh[pousr]_*`,
`xox[baprs]-*`, `AIza*`, `AKIA*`, `[sr]k_live_*`, `hf_*`, JWTs, `Bearer …`,
`Basic …`, and any key literally named `api_key` / `token` / `secret` /
`password` / `authorization`.

Deliberately **not** redacted: file paths, commit SHAs, base64 images, and normal
prose. A rule that redacts every long string makes the board useless and teaches
people to ignore it.

## Tests

```bash
npm test        # 24 tests
npm run typecheck
```

Coverage: credential classes, nested-object redaction, credential-named keys,
cyclic payloads, non-serialisable input, regex-state leakage between calls,
disabled-by-default behaviour, transport failures never throwing, HTTP errors
not echoing the token, and the exact request shape sent to TALOS.

## Hooks used

| Hook | Purpose |
|---|---|
| `ctx.tool.hook("execute.before")` | Emit `tool.start` with redacted arguments |
| `ctx.tool.hook("execute.after")` | Emit `tool.finish` with status, preview, duration |
| `ctx.permission.hook("evaluate")` | Report a pending permission so the phone can answer it |

Built against `@opencode/plugin` 2.x. V1 plugins are not compatible — the entry
point is `Plugin.define({ id, setup })`, not a function returning a hook map.

## Licence

GPL-3.0-or-later
