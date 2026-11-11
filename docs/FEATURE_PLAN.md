# TALOS Feature Plan — researched against open-source AI platforms

Date: 2026-11-04
Basis: feature audit of Open WebUI (BSD-3, 157k★), LibreChat (MIT, 46k★),
LobeChat (Apache-2.0, 84k★), AnythingLLM (MIT, 68k★), Jan (Apache-2.0),
Msty (closed, free tier). Sources listed at the bottom.

The rule for this document: a feature only goes in the build queue if TALOS
does not already have it. Several things that look present are not — that is
recorded in the "claimed but absent" section, because shipping a row that does
nothing is worse than not shipping the row.

---

## 1. Audit — what TALOS actually has

Verified by reading the source, not by trusting a capability list.

| Capability | State | Evidence |
|---|---|---|
| Multi-provider routing | real | `server/provider_service.py`, `/api/providers/*`, live OpenRouter + OpenCode |
| CLI agent control | real | `server/agent_sessions.py`, `server/cli_engines.py`, 5/7 engines detected |
| Tool-call ledger | real | `server/tool_ledger.py`, `/api/kanban/*`, plugin ingest |
| Delegate orchestration | real | `/api/agents/{id}/delegate`, `/api/agents/delegates/*`, watchdog |
| Agent identity / avatars | real | `ui/avatar/*`, 500 expressions, group vs member |
| LaTeX + diagram rendering | in progress | `ui/richtext/*` (delegate batch `deleg_18eb2a61`) |
| Voice in/out | real | TTS providers configured, inbound transcription |
| Kanban of tool activity | real | `ui/kanban/KanbanScreen.kt` |
| Settings-driven service control | real | `ui/settings/DashboardControlScreen.kt` |

## 2. Claimed but absent

These appear in keyword searches and would look done in a screenshot. They are
not. Any plan that assumes them is wrong.

- **RAG / document Q&A** — zero embedding code. `grep -c embedding server/*.py` → 0 real hits.
- **Conversation branching / forking** — 0 `fork` references anywhere.
- **Conversation search** — no full-text index over messages.
- **Prompt library** — no saved-prompt store.
- **Model comparison** — no side-by-side runner, despite multiple providers being live.
- **Citations** — no source-attribution model.
- **Export / share** — nothing that produces a portable transcript.

## 3. Feature queue

Ordered by (user value × how cheap it is against what already exists). Each item
names the platform it comes from so the borrowing is honest.

### P0 — Branching and fork (LibreChat)
Every message gets a `fork` action: it copies the conversation up to that point
into a new thread with a new id, leaving the original untouched. This is the one
feature the three big self-hosted platforms disagree on most, and the one users
miss immediately after using LibreChat.
- Server: `POST /api/rooms/{id}/fork` with `{message_id, title?}` → new room row, copied messages, `parent_room_id` recorded.
- App: long-press a message → Fork. Thread list shows the parent.
- Cost: low. Messages and rooms already exist; this is a copy plus two columns.

### P0 — Conversation search (LibreChat / Open WebUI)
Full-text search across every message with an inline result list that jumps to
the hit.
- Server: SQLite FTS5 virtual table over `messages`, kept in sync on insert; `GET /api/search?q=` returning room, message id, snippet with match offsets.
- App: search field in the thread list, results grouped by room.
- Cost: low. SQLite is already the store.

### P1 — Prompt library (Open WebUI / LobeChat)
Save a message as a reusable prompt, with a name, optional variables
(`{{topic}}`), and a category. Insert from the composer.
- Server: `prompts` table + CRUD, variable substitution at render time, never at save time.
- App: a sheet reachable from the composer, with a search field and a "save current" action.
- Cost: low.

### P1 — Model comparison (Msty)
Send one prompt to 2–4 models at once and read the answers side by side, with
per-answer latency and token counts. TALOS already has the provider clients, so
this is a fan-out plus a two-column layout on tablets and a swipeable stack on
phones.
- Server: `POST /api/compare` with `{prompt, models[], room_id?}` streaming each
  model's deltas tagged by model id.
- App: split view with synchronised scroll; picking a winner promotes that answer
  into the conversation.
- Cost: medium. Streaming fan-out is the only non-trivial part.

### P2 — Document workspace with citations (AnythingLLM)
Attach files to a room, chunk and embed them, and answer with numbered
citations that scroll to the source passage. This is the biggest item and the
one that needs a real dependency decision (see below).
- Requires an embedding model: either a local ONNX embedder (~80 MB, no network)
  or an API provider. Local is the default because the user's phone may be
  offline.
- Server: chunker, vector store (SQLite + float arrays is enough at this scale),
  retrieval, citation anchors.
- App: source chips under the answer; tapping opens the passage.
- Cost: high. Split into chunker+store, then retrieval, then UI.

### P2 — Scheduled tasks (AnythingLLM)
A room can run a saved prompt on a cron schedule and drop the result into the
thread. Hermes already runs cron on the host, so the app-side job is to own the
schedule record and show the run history, not to reimplement a scheduler.
- Server: `scheduled_tasks` table, a tick that shells out to the existing cron
  mechanism, and a run log.
- App: a schedule row per room, next-run time, last result link.
- Cost: medium.

### P3 — Export and share (Open WebUI / LibreChat)
Export a thread as Markdown or JSON, with attachments and citations intact.
Share as a read-only link served by the host.
- Cost: low for export, medium for share links (needs an unguessable token and
  a scope check).

## 3a. Agent-initiated fake call — reference only, do not copy

**The idea.** The agent can put a call through to the phone. No SIM, no number,
no carrier, no cost, unlimited. The screen looks exactly like a real incoming
call — ring, answer, decline, keypad, speaker — and everything stays inside the
phone.

**Why it belongs in TALOS.** The point of the app is talking to the agent. If the
agent hits something it needs you for, a real incoming-call screen is the one
notification you cannot miss; a banner is the one you can.

**How the platform does it.** Android owns the incoming-call UI. The supported
route is to register a phone account with `TelecomManager`, then call
`addNewIncomingCall(accountHandle, extras)` with the caller name and number in
the incoming-call extras. The system draws the rest. The app supplies a
`ConnectionService` that answers with a `Connection` whose audio it controls.
`CAPABILITY_CALL_PROVIDER` plus `SCHEME_TEL` are the capabilities involved.

**Reference: <https://github.com/DDOneApps/Phony>** — an open-source app that
simulates incoming calls in Material 3.

> **Warning — read this before touching the code.**
>
> This reference is here so the mechanism is understandable, not so it can be
> copied. TALOS and Phony share nothing: different app, different package, no
> code was taken from it and none should be. Do not copy its files, its class
> names, its strings or its resources. Do not vendor the repository. Do not
> depend on it.
>
> It is GPL-3.0. TALOS is now GPL-3.0 too, so the licenses would not clash, but
> that is not the reason to look: the reason is that reading how a telecom
> connection is driven teaches the shape of the problem, and writing the small
> amount TALOS needs from that shape is the work.
>
> Use it to understand the system. Write TALOS' own implementation.

**State:** not started. Not code, not a screen, not a plugin.

**The part that is not obvious.** The first run has to register the phone account
and the user has to enable it in system settings; without that the call will not
appear. That step is part of the feature, not an edge case.

## 4. Deliberately not doing

- **Multi-user auth, RBAC, SSO** (LibreChat/Open WebUI). TALOS is a personal
  control plane on a tailnet with one operator. Adding login screens would be
  ceremony. The existing bearer token is the correct scope.
- **Plugin marketplace** (LobeChat). The plugin surface is one bridge plugin by
  design; a marketplace needs curation that does not exist here.
- **Built-in model runtime** (Jan / AnythingLLM's llama.cpp). TALOS drives
  external engines. Bundling a runtime would multiply the APK and duplicate work
  the CLIs already do.

## 5. What "done" means for each item

No feature ships on the strength of a screen existing. Each of the P0/P1 items
lands with: a server endpoint, a test proving the endpoint's contract, a Kotlin
screen reachable from a route, and an entry in the integration verifier so it
cannot silently become dead code.

---

## Sources

- Open WebUI vs LibreChat vs LobeChat (2026) — evalaitools.com
- LibreChat vs OpenWebUI vs Lobe Chat, self-host comparison — blog.elest.io
- Self-Hosted AI Chat UI Comparison 2026 — amurg.ai
- Open WebUI Alternatives: 7 Tools Compared — ultranos.de
- Open WebUI, LibreChat, AnythingLLM & Co. Compared 2026 — wz-it.com
- Jan Alternatives / Local LLM GUI rankings 2026 — scored.tools, hussain-nazary.github.io
- AnythingLLM product overview — github.com/Mintplex-Labs/anything-llm
- Best open-source chat UIs (Nov 2026), 21 ranked — archestack.com
