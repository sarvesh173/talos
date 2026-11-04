# SYSTEM.md — TALOS Interactive Surface Protocol

> **Read this before generating any reply that is not plain prose.**
>
> TALOS renders *rich* messages on an Android client: charts, selection lists,
> checkmarks, mini-games, emoji grids. None of that happens by accident. The
> client draws it because the model emits a **fenced directive block** and the
> harness parses it out. If you write prose that *describes* a chart, the user
> sees prose. If you emit the block below, the user sees a chart.

This file is *not* a persona prompt. It is the wire contract between the model
and the renderer. It applies to the main agent and to every delegate.

---

## 0. The one rule

**Never describe a UI element you could render.** No "I would show you a graph",
no ASCII tables as a substitute, no "here's a chart: [imagine bars]". Either
emit the block or answer in prose. A described chart is a broken chart.

---

## 1. Envelope

One reply may contain at most **three** directive blocks, each on its own, each
fenced with `:::talos` … `:::`. Prose goes between them. Order is preserved.

```
:::talos
{"type": "...", ...}
:::
```

The body must be **valid JSON, one object, one line is fine but multi-line is
fine too.** No comments, no trailing commas, no markdown inside the JSON.

If the JSON is malformed the client drops the block silently. The user then
sees a blank gap and thinks the agent is broken. Validate before emitting:
quotes closed, no `True`/`None` (use `true`/`null`), no unescaped newline
inside a string.

**Never** emit a `:::` fence unless the body parses. If you are unsure the
payload is valid, fall back to prose.

---

## 2. Available blocks

### 2.1 `chart` — bar / line / area

```json
{"type":"chart","kind":"bar","title":"Delegate tokens per second",
 "x":["crouton","deepseek-pro","GLM-5.3","Kimi-K3"],
 "series":[{"name":"tok/s","values":[0.8,29.7,59.8,10.4]}],
 "unit":"tok/s"}
```

- `kind`: `bar` | `line` | `area`
- `series`: 1–4 entries; each `values` must be the **same length as `x`**
- Numbers only in `values`. `null` renders a gap in `line`/`area`.
- Do not exceed ~12 x-labels; aggregate the rest into "other".

Use a chart when the user asks to compare quantities, see a trend, or read a
distribution. Do not chart two data points — write a sentence.

### 2.2 `select` — single/multi choice with ticks

```json
{"type":"select","multi":true,"title":"Which targets should I refactor?",
 "options":[
   {"id":"orb","label":"Thinking orb tempo","hint":"~90 LOC, low risk","checked":true},
   {"id":"avatar","label":"Agent avatar states","hint":"needs bus wiring"},
   {"id":"glass","label":"Liquid glass optics","hint":"visual only"}
 ],
 "submit_label":"Apply"}
```

- `multi: false` ⇒ radio behaviour; `multi: true` ⇒ checkboxes
- `checked` pre-selects; omit for unchecked
- `hint` is one short line, not a paragraph
- The client sends the chosen `id`s back in the next user turn as
  `selected: ["orb","glass"]`

Use this instead of asking "which ones?" in prose whenever there are 2–8
discrete options. Above 8, ask a narrower question first.

### 2.3 `confirm` — yes / no with a deadline

```json
{"type":"confirm","title":"Delete the stale APK artifacts?",
 "body":"3 builds, 240 MB total, in the release folder.",
 "confirm_label":"Delete","deny_label":"Keep",
 "deadline_seconds":120,"default_on_timeout":"deny"}
```

- Always set `default_on_timeout`. `deny` is the safe default; use `confirm`
  only when the action is trivially reversible.
- `deadline_seconds` between 15 and 600.
- The client shows a countdown ring. On expiry it applies the default and
  tells you which one fired.

### 2.4 `permission` — tool/command approval

```json
{"type":"permission","tool":"shell","command":"rm -rf build/",
 "reason":"Clears stale Kotlin intermediates",
 "scopes":["once","session","full"]}
```

- `scopes` is what the user is allowed to grant. Omit `full` for anything
  destructive — the operator can still escalate manually, but you must not
  offer blanket approval for `rm -rf`, `git push --force`, credential access,
  or anything touching `.git` history.
- The reply comes back as `granted: "once" | "session" | "full" | "denied"`.

### 2.5 `game` — playable mini-game

```json
{"type":"game","game":"rock-paper-scissors","title":"Best of three?",
 "state":{"round":1,"player":0,"agent":1},
 "prompt":"Pick one"}
```

- `game`: `rock-paper-scissors` | `tic-tac-toe` | `guess-number`
- Games are turn-based. The client returns
  `move: "rock"` / `move: 4` (cell or number) and you reply with the next
  `state`, not a fresh game, until it ends.
- Announce the result in prose after the final move. Do not let a game block
  be the last thing in a conversation.

### 2.6 `emoji` — reaction grid

```json
{"type":"emoji","title":"How did that land?","options":["🔥","😐","💀","👀"]}
```

Four to eight emoji maximum. The client returns `emoji: "🔥"`. Use sparingly —
this is a reaction, not decoration.

### 2.7 `cards` — rich result tiles

```json
{"type":"cards","title":"Discovered CLI engines","items":[
  {"title":"OpenCode","subtitle":"/home/kira/.local/bin/opencode","badge":"installed","accent":"blue"},
  {"title":"Codex CLI","subtitle":"not on PATH","badge":"missing","accent":"gray"}
]}
```

- 2–6 cards
- `badge` is one word; `accent` is `blue|green|orange|red|purple|gray`
- Use for scan results, file lists, agent rosters

---

## 3. Questions the model must ask as blocks

Three situations **must** be a block, never prose:

| Situation | Block |
|---|---|
| Choosing between named options | `select` |
| Destructive or irreversible action | `confirm` (with `deny` default) |
| Needs a tool/command the user has not granted | `permission` |

If you ask these in prose, the operator has to type the answer as text, the
harness cannot parse it, and the turn stalls. That is a bug in your reply.

Everything else — clarification, opinion, "should I?", open-ended — stays
prose. Do not turn a simple question into a `select` with two options; that is
slower to answer than typing "yes".

---

## 4. When a delegate asks you something

Delegates emit the same blocks. When a delegate's block needs a human
decision, forward it to the user **unchanged** and say who asked:

> `Kotlin Auditor` needs a decision:
> :::talos {...} :::

Do not answer on the user's behalf. Do not collapse four delegate questions
into one summary question — each keeps its own deadline and its own scope.

If a delegate's question expires, report which delegate and what default
fired. Silence about an expired approval is how agents end up doing work
nobody sanctioned.

---

## 5. Limits

- Max 3 directive blocks per reply.
- Max 1 `permission` block per reply (two simultaneous grants is confusing).
- Never emit a block inside another block's string.
- Never emit a block as the entire reply with no prose at all — the user needs
  one sentence of context above it.
- Streaming: emit the block **whole** at the end of the streamed prose. Do not
  stream a fence open and then think for ten seconds; the client shows an
  empty box.

---

## 6. Failure modes to avoid

| Wrong | Why | Right |
|---|---|---|
| `` ```json {...} ``` `` | Rendered as code text | Use `:::talos` fence |
| `"values": [1, 2]` with 4 `x` labels | Client throws, block dropped | Match lengths |
| `{"type":"confirm"}` with no `default_on_timeout` | Timer expires into nothing | Always specify |
| Chart with 1 bar | Absurd | Write a sentence |
| `select` with options `["yes","no"]` | It is a confirm | Use `confirm` |
| Permission offered with `full` scope for `rm -rf` | Blanket destructive grant | Omit `full` |
| Game block after the game ended | Client shows dead board | Prose result |
