# TALOS Feature Catalog & Roadmap (FEATURE.md)

This document tracks all backend capabilities, API contracts, and pending graphical interface controls. Features implemented in the backend are cataloged here so they can be turned into user interface buttons, toggles, and panels without missing requirements.

---

## 1. Dynamic Agent Registry & Discovery

Backend detects and manages AI CLI tools installed on the Linux host or defined in custom manifests.

- [x] **Host Toolchain Scanner**: Automatically probes `$PATH`, `~/.local/bin`, `~/.npm-global/bin`, and `~/.cargo/bin` for recognized agent CLIs (`opencode`, `hermes`, `omnirush`, `codex`, `cline`, `opencli`, `koda`, `darce`, `asaltcodecli`, `atlarix`, `supercode`, `zcode`, etc.).
- [x] **Custom Manifest Loader**: Loads custom agent manifests from `~/.talos/agents/*.json` (name, binary, args, default environment, icon emblem).
- [x] **Agent State Tracking**:
  - `installed`: Binary exists and is runnable.
  - `available`: Configured or recognized, but needs setup/installation.
  - `running`: Active execution slot in progress.
  - `idle`: Ready for task execution.
- [ ] **UI Mapping**:
  - *Fleet Hub Grid*: Dynamic agent cards with status pills, version badge, and execution status.
  - *Add Custom Agent Modal*: Form to register a custom command or script.

---

## 2. Agent Process Supervisor & Execution Pool

Runs CLI coding tools headlessly with resource discipline.

- [x] **Bounded Execution Pool**: Hard limit of concurrent agent processes (default 2 active slots) to prevent high CPU and RAM consumption on laptops.
- [x] **Job Queue**: Excess execution requests queue gracefully rather than spawning uncontrolled background processes.
- [x] **Stdout/Stderr Streaming**: Real-time process output piped directly into TALOS WebSocket room event bus.
- [ ] **UI Mapping**:
  - *Active Process Indicator*: Live status bar pill showing running tasks and execution duration.
  - *Abort/Kill Button*: One-tap process termination for runaway agent runs.

---

## 3. Autonomous Agent Setup & Installer

Enables the backend to self-install or repair missing agent dependencies.

- [x] **Setup Action Dispatcher**: Executes installer recipes (`npm install -g`, `pip install`, script fetch) in a tracked execution slot.
- [x] **Setup Log Streaming**: Captures setup progress and reports success/failure.
- [ ] **UI Mapping**:
  - *Install Button*: Appears on agents marked as `available` (not yet installed).
  - *Setup Console Drawer*: Displays live installation terminal output.

---

## 4. Google Drive Cloud Backup & Vault Bridge

Offloads storage and chat archives from mobile device to cloud storage via backend token link.

- [ ] **OAuth2 Flow Endpoint**: `/api/auth/gdrive` web authentication flow.
- [ ] **Automated Snapshot Sync**: Periodic sync of SQLite databases, room message archives, and agent artifacts to Google Drive `TALOS_Backups/` folder.
- [ ] **One-Tap Restore**: Restore complete workspace state onto fresh devices.
- [ ] **UI Mapping**:
  - *Cloud Sync Toggle*: Settings capsule to connect Google Drive and view sync status.

---

## 5. Mobile & Android Native Enhancements

Hardware and OS level permissions configured in `AndroidManifest.xml`:

- [x] **Microphone & Voice Input**: `RECORD_AUDIO` enabled for future voice notes and STT integration.
- [x] **Camera Integration**: `CAMERA` permission enabled for visual artifact and QR code capture.
- [x] **Battery Optimization Bypass**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for persistent background operation on aggressive OEM Android builds (Vivo FuntouchOS, Samsung OneUI).
- [x] **Background Connectivity**: `WAKE_LOCK` and `FOREGROUND_SERVICE` to maintain mesh connection when screen is locked.
- [x] **Split-Screen Optimization**: `android:supportsPictureInPicture="true"` and `<layout android:minWidth="200dp" android:minHeight="200dp" />` for OEM split screen compatibility.
- [ ] **UI Mapping**:
  - *Audio Recording Capsule*: Floating recording button with waveform animation.
  - *Camera Attachment Action*: Quick attach button in message compose dock.
  - *Battery Optimization Prompt Dialog*: Guided one-time prompt to request battery unrestricted mode.

---

## 6. Multi-Image Inbound Batching & Turn Queuing

Prevents fragmented multiple-image analysis by holding incoming images in a debounce queue and dispatching a single unified turn.

- [x] **Debounce Window**: Aggregates images sent within a 3.5-second burst window (albums or rapid sequential sends) into a single batch.
- [x] **Turn-Lock Queuing**: If an agent is already actively executing a turn, incoming images wait in the queue until the active turn finishes instead of creating competing sub-turns.
- [x] **Unified Multi-Image Prompt**: Dispatches all collected image paths together to the vision model in one single request.
- [x] **Backend Implementation**:
  - `MediaBatchQueue` in `crates/talos-core/src/room_bus.rs` for room messages with attachments.
  - Hermes Telegram adapter configured (`HERMES_TELEGRAM_MEDIA_BATCH_DELAY_SECONDS=3.5`).
- [ ] **UI Mapping**:
  - *Multi-Image Preview Grid*: Horizontal thumbnail strip above the input box showing queued images before sending.
  - *Queued Turn Badge*: Visual indicator showing pending media count when a turn is currently in flight.

---

## 7. Universal CLI Agent Dynamic Model Switching

Allows switching the underlying AI model for any registered CLI coding agent dynamically from the backend and future client interface.

### Specifications & Behavior:
1. **Dynamic Model Inspection**:
   - Backend tracks `supported_models` and `current_model` per agent.
   - Models can be queried per agent to inspect what choices exist (e.g. `claude-3-7-sonnet`, `gpt-6-astra:max`, `gpt-4o`, `deepseek-r1`, `gemini-2.5-flash`).
2. **Dynamic Model Switching**:
   - Switching an agent's model updates the agent's runtime state immediately without restarting the TALOS backend.
   - Future task executions (`/api/agents/:id/run`) automatically inject the selected model using the agent's specific CLI convention.
3. **Execution Injection Mechanisms**:
   - **Flag Injection**: Injects `--model <name>` or `-m <name>` for agents like OmniRush, OpenCode, Cline, OpenCLI, Codex, etc.
   - **Environment Variable Injection**: Injects `HERMES_MODEL=<name>`, `OPENAI_MODEL=<name>`, etc., for environment-driven agents.
   - **Custom Config Hooks**: For tools that save configuration files, runs an automated config set command.
4. **Backend REST Endpoints**:
   - `GET /api/agents/:id/models` -> Returns `{ agent_id, current_model, supported_models }`.
   - `POST /api/agents/:id/model` -> Payload `{ "model": "model-name" }`, switches active model and returns updated `AgentInfo`.
5. **UI Mapping (Pending Frontend)**:
   - *Model Selector Dropdown / Chips*: Tapping an agent card opens available model options.
   - *Active Model Badge*: Displays the currently selected model name directly beneath the agent's name in Fleet Hub.
   - *Model Switch Feedback Toast*: Transient confirmation when a new model is selected.

---

## 8. Universal vs Dedicated Model Tiering across Agent Fleet

Categorizes agents by model flexibility: Universal Agents receive the entire OmniRoute & custom model catalog, while Dedicated Agents retain their proprietary native models.

### Specifications & Behavior:
1. **Universal Agents Tier** (`tier: "universal"`):
   - Powerful general-purpose coding agents (e.g. `opencode`, `omnirush`, `hermes`, `codex`, `cline`, `cloudcode`).
   - Automatically inherit the **Universal Model Pool**: any model added to OmniRoute or TALOS gateway instantly routes into their available model picker without per-agent manual configuration.
2. **Dedicated Agents Tier** (`tier: "dedicated"`):
   - Specialized or proprietary CLI tools that only support their native models or fixed single endpoints (e.g. Gemini CLI, standalone reverse-engineered agent binaries from the 50-tool fleet).
   - Displayed with a locked/native badge in the UI so users know external models cannot be arbitrarily injected.
3. **Global Universal Model Sync**:
   - TALOS backend maintains a synchronized catalog of all OmniRoute endpoints and custom models (`GET /api/models/universal`).
   - Universal agents query this centralized registry to populate their supported model lists dynamically.
4. **Backend REST Endpoints**:
   - `GET /api/models/universal` -> Returns full list of universal models across all providers.
   - `GET /api/agents` -> Returns `tier: "universal" | "dedicated"` for each registered agent.
5. **UI Mapping (Pending Frontend)**:
   - *Universal Tier Pill*: Accent-colored pill on compatible agent cards indicating full model freedom.
   - *Dedicated Tier Lock*: Neutral locked icon on fixed-model agents.
   - *OmniRoute Model Browser Drawer*: Categorized drawer (Frontier, Coding, Fast, Free) when selecting models for Universal agents.

---

## 9. Dynamic Reasoning Effort & Ultra Code Mode

Provides granular control over the depth of reasoning/thinking tokens and multi-agent expansion across modern AI models and CLI agents.

### Complete Hierarchy Scale (8 Tiers — from `none` to `ultra`):
Modern frontier reasoning engines (OpenAI Codex, Claude Code Ultra, DeepSeek R1, Grok 3 Think) follow an 8-tier progression:

1. `none`: Zero reasoning overhead. Bypasses chain-of-thought entirely for latency-critical tasks (voice, simple lookups, classification).
2. `minimal`: Ultra-light sanity pass with near-zero latency impact.
3. `low`: Efficient reasoning for structured tasks, tool calling, and fast drafting.
4. `medium`: Default balanced tier for complex reasoning, planning, and multi-step decisions.
5. `high`: Deep deliberate planning, complex debugging, and agentic workflows.
6. `xhigh`: Extended deep research, long asynchronous verification loops, and heavy multi-file refactors.
7. `max`: Highest single-model test-time compute. Uncapped internal chain-of-thought where one context explores and verifies all edge cases.
8. **`ultra` (Ultra Code Mode)**: The ultimate tier beyond `max`. Combines **Maximum single-turn reasoning (`max`) with Proactive Dynamic Multi-Agent Orchestration**.
   - Rather than just thinking longer in a single thread, `ultra` automatically decomposes the goal and spawns parallel sub-agents (e.g. 3–4 parallel investigation streams + verification judge).
   - In Codex CLI: `ultra` maps to inference effort `max` + `multiAgentMode: "proactive"`.
   - In Claude Code: Activates `Ultra Code` mode for full-repository refactoring, parallel verification, and automated consensus synthesis.

### Specifications & Behavior:
1. **Normalized Scale Mapping**:
   - Backend exposes all 8 tiers (`none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`, `ultra`).
   - Translates per model/provider to supported subsets (e.g. models rejecting `none` gracefully clamp to `low`; single-agent CLIs receiving `ultra` run at `max`).
2. **Provider & CLI Translation Engine**:
   - **Codex CLI**: `ultra` translates to `--reasoning max --multi-agent proactive`.
   - **Claude Code**: Activates `Ultra Code` mode with maximum thinking budget + sub-agent fan-out.
   - **OpenAI API**: `reasoning_effort: "none" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max"`.
   - **Anthropic API**: `thinking: { type: "enabled", budget_tokens: <1024..128000> }`.
   - **OmniRush**: Translates `ultra` to `:max` model suffix with background worker slots.
3. **Backend REST Endpoints**:
   - `GET /api/agents/:id/reasoning` -> Returns `{ agent_id, current_effort, supported_efforts }`.
   - `POST /api/agents/:id/reasoning` -> Payload `{ "effort": "none" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max" | "ultra" }`.
4. **UI Mapping (Pending Frontend)**:
   - *Reasoning Effort Selector*: 8-step tactile scrub (`None` | `Min` | `Low` | `Med` | `High` | `xHigh` | `Max` | `Ultra`).
   - *Ultra Code Mode Shimmer Pill*: Special gold/violet gradient badge when `Ultra` is engaged to indicate autonomous multi-agent fanout.

---

## 10. Hierarchical Agent Tree Inspection, Live Sub-Agent Steering & Granular Control

Provides full visibility and interactive control over parent-child agent delegation trees, allowing users to inspect, chat with, steer, or modify the model/reasoning of any individual sub-agent independently.

### Specifications & Behavior:
1. **Hierarchical DAG Tracking**:
   - When a primary agent summons sub-agents (e.g. delegating 3–4 parallel investigation or coding tasks), the backend tracks the full dependency graph: `Root Agent -> Child Sub-Agents -> Tasks`.
   - Every child sub-agent retains a unique `subagent_id`, `parent_id`, `assigned_goal`, current `status` (`running`, `paused`, `completed`, `failed`), and real-time execution log.
2. **Direct Sub-Agent Interactive Chat & Live Steering**:
   - Users can directly interact with ANY child agent in real-time, exactly like chatting with the main root agent.
   - Mid-flight course correction: user can send directive messages directly to a sub-agent to steer its work without aborting or restarting the parent's loop.
3. **Independent Per-Child Model & Reasoning Customization**:
   - The user has total freedom to override the underlying model of any specific child agent independently (e.g., Parent is running on `claude-3-7-sonnet`, but Child 1 can be switched to `deepseek-r1` for math, and Child 2 to `gpt-6-astra:max` for code refactoring).
   - Each child sub-agent's reasoning effort (`none` through `ultra`) can be tuned individually.
4. **Granular Life-Cycle Management**:
   - Ability to pause, resume, or kill any specific sub-agent without killing sibling sub-agents or tearing down the parent agent session.
5. **Backend REST & WebSocket Endpoints**:
   - `GET /api/agents/tree` -> Returns active hierarchy of root agents and all active child sub-agents.
   - `GET /api/agents/subagents/:subagent_id` -> Returns full state, live logs, model, and reasoning depth of a specific sub-agent.
   - `POST /api/agents/subagents/:subagent_id/chat` -> Injects a direct message or steering prompt to a specific sub-agent (`{ "message": "..." }`).
   - `POST /api/agents/subagents/:subagent_id/model` -> Overrides the child agent's model (`{ "model": "..." }`).
   - `POST /api/agents/subagents/:subagent_id/reasoning` -> Overrides the child agent's reasoning level (`{ "effort": "..." }`).
   - `POST /api/agents/subagents/:subagent_id/stop` -> Terminates the specific child sub-agent immediately.
6. **UI Mapping (Pending Frontend)**:
   - *Agent Fleet Tree View / DAG Visualizer*: Collapsible tree node graph showing root delegator and child branches with live status pulses.
   - *Sub-Agent Focus Drawer*: Tapping any child node opens a dedicated conversation pane with individual Model/Reasoning chips and live stdout streaming.

---

## 11. Universal Custom Provider Management, Secret Vaulting, Auto-Discovery & Priority Fallback Cascades

Gives the user total authority over AI providers, custom endpoints, auto-discovery of models, granular visibility curation, and ordered fallback key chains.

### Specifications & Behavior:
1. **Dynamic Custom Provider Onboarding**:
   - User inputs Base URL + API Key + Provider Name (e.g. OpenRouter, Groq, DeepSeek, custom vLLM / Ollama server).
   - Backend auto-probes `${base_url}/models` (OpenAI format) to pull available model IDs automatically into the catalog.
   - Allows assigning the new provider to specific CLI agents (`opencode`, `omnirush`, `hermes`, `cline`, etc.).
2. **Encrypted Secret Vaulting (SG Warden Compliant)**:
   - API keys are vaulted under `~/.talos/vault/providers.json` with strict `0600` Linux permissions.
   - Keys are NEVER exposed in plain text logs, error traces, or Git commits (auto-masked as `[REDACTED]`).
3. **Full Provider Lifecycle Management**:
   - **Hire / Pause (Enable / Disable)**: Toggle switch to pause a provider without deleting credentials.
   - **Purge / Delete**: Deletes provider entirely, wiping vaulted API keys and unbinding associated models.
4. **Model Visibility & Catalog Curation**:
   - **Hide / Unhide**: Eye icon toggle to hide unwanted/bloated models from the picker so only curated favorites appear.
   - **Hard Delete**: Delete specific model IDs from the local catalog permanently.
5. **Ordered Multi-Tier Fallback Cascades (Priority Chains)**:
   - Users can configure multiple backup keys/endpoints with an explicit priority order:
     `Primary (Priority 1) -> Fallback 1 (Priority 2) -> Fallback 2 (Priority 3) -> Fallback N`.
   - Re-order priority numerically or via drag-and-drop.
   - **Auto-Failover**: When Primary hits HTTP 429 (quota exhausted) or 5xx, the supervisor seamlessly shifts the in-flight request to Fallback 1 within milliseconds, preserving task progress.
6. **Backend REST Endpoints**:
   - `GET /api/providers` -> List all configured providers, status, and model counts.
   - `POST /api/providers` -> Register a custom provider (`{ "name": "...", "base_url": "...", "api_key": "...", "target_agents": [...] }`).
   - `POST /api/providers/:id/fetch-models` -> Force auto-fetch from `${base_url}/models`.
   - `PATCH /api/providers/:id/status` -> Enable/Disable provider (`{ "enabled": true | false }`).
   - `DELETE /api/providers/:id` -> Delete provider and purge keys.
   - `PATCH /api/models/:model_id/visibility` -> Toggle visibility (`{ "hidden": true | false }`).
   - `DELETE /api/models/:model_id` -> Remove model from catalog.
   - `GET /api/providers/:id/fallbacks` -> Get ordered fallback cascade list.
   - `PUT /api/providers/:id/fallbacks` -> Update fallback order (`{ "ordered_keys": ["key_id_1", "key_id_2"] }`).
7. **UI Mapping (Pending Frontend)**:
   - *Provider Vault Dashboard*: Card grid with live provider status toggles, latency meters, and model counts.
   - *Add Provider Modal*: Simple Base URL + API Key form with "Auto-Fetch Models" test button.
   - *Model Curation Drawer*: Table with eye toggle icons (Hide/Unhide) and delete action per model.
   - *Fallback Priority Stack*: Drag-and-drop ordered list (1, 2, 3...) for automatic 429 failover.

---

## 12. Direct Raw Terminal & Upstream Log Mirroring (Zero Artificial Filtering)

Pipes unvarnished, exact process terminal output and upstream provider error payloads directly to the application UI—exactly as if running the command in a Linux terminal, without artificial filters or synthetic parsing.

### Specifications & Behavior:
1. **Verbatim 1:1 Pass-Through**:
   - Zero hardcoded error message rewriting. Whatever stdout, stderr, or HTTP body the CLI tool or upstream API outputs (`Quota exhausted`, `Your access is denied`, `Model deprecated`, python traceback, or raw JSON error) is mirrored 1:1.
   - Eliminates the risk of synthetic translators hiding real unexpected edge-case errors.
2. **Gateway-Style Log Pipeline**:
   - Just like Hermes gateway writes raw diagnostic lines directly to its gateway log stream, TALOS Core captures stderr and upstream failure bodies and emits them immediately over the room event bus.
   - When an agent job fails or gets rate-limited, the exact line from the process appears directly in the conversation thread and the log stream.
3. **No-PC Remote Live Terminal Feed**:
   - Allows the user to inspect the exact terminal state directly from their phone over Tailscale mesh without needing to get up, open SSH, or sit in front of the PC.
4. **Backend REST & WebSocket Endpoints**:
   - `WS /ws/logs` -> Real-time raw log stream forwarding exact process stdout/stderr lines.
   - `GET /api/logs/latest` -> Returns raw unedited terminal output of the last executed command or agent run.
   - `GET /api/agents/:id/logs` -> Returns verbatim stdout and stderr logs for a specific agent.
5. **UI Mapping (Pending Frontend)**:
   - *Live Terminal Log Sheet / Drawer*: Expandable dark-mode terminal window right inside the app displaying exact scrolling CLI output.
   - *Verbatim Error Callout*: When a command fails, displays the exact stderr string in a monospace console bubble directly under the message.

---

## 13. Context Window Telemetry & On-Device Predictive Token Interpolation

Monitors context window saturation and token consumption with zero CPU load on the host machine by combining periodic server sync pulses with on-device client velocity prediction.

### Specifications & Behavior:
1. **Dual Telemetry Modes (Configurable in Settings)**:
   - **Real-Time Mode (`sync_delay: 0s`)**: High-frequency streaming pulses for users who want exact tick-by-tick token counts.
   - **Optimized Delay Mode (`sync_delay: 1s–10s`, default `5s`)**: The host computer only emits a lightweight token snapshot once every 5 seconds, preventing CPU/RAM overload when running multiple concurrent background agents.
2. **On-Device Client-Side Velocity Prediction Engine**:
   - Runs locally on the Android device (phone-local script).
   - Calculates recent token velocity (tokens generated per second, e.g. ~100 tok/sec).
   - In between the 5-second server sync pulses, the phone predicts and smoothly animates the token counter ticking upward in real-time.
   - **Jitter-Free Reconciliation**: When the authoritative 5s snapshot arrives from the server, the client smoothly reconciles without abrupt jumping. If actual token output was lower or higher than expected, it smoothly catches up.
3. **Context Window Saturation Tracking**:
   - Tracks current token consumption vs maximum model limit (`prompt_tokens`, `completion_tokens`, `total_tokens`, `context_window_limit`, `percentage_used`).
   - Emits visual threshold warnings when context exceeds 80% (amber) and 90% (red), suggesting context compaction or model handover.
4. **"Learn More" Architecture Transparency Sheet**:
   - Tapping "Learn More" next to the counter opens an on-device modal explaining:
     *"To keep your PC host cool and preserve hardware resources, server telemetry syncs in 5-second intervals. The live ticking you see is predicted on-device based on generation velocity and reconciled against server snapshots."*
5. **Backend REST & WebSocket Endpoints**:
   - `GET /api/agents/:id/context` -> Returns `{ agent_id, current_tokens, context_window, usage_pct, generation_rate_per_sec }`.
   - `WS /ws/telemetry/tokens` -> Periodic token usage pulse emitted at the user's configured delay interval (`interval_secs: 0..10`).
6. **UI Mapping (Pending Frontend)**:
   - *Live Context Ring & Token Pill*: Monospace pill displaying live ticking tokens and circular percentage ring in the app header/composer.
   - *Telemetry Sync Rate Slider*: Setting slider in Preferences (`0s: Real-Time` to `10s: Ultra-Eco`, default `5s`).
   - *Context Explainer Dialog*: Tapping "Learn More" pops up the architecture explanation modal.

---

## 14. Live Compose Prompt Token Estimator & Interactive Corner Context Ring (Double-Tap Inspect)

Calculates live prompt token counts while typing in the composer and displays an interactive circular context saturation ring in the screen corner with double-tap deep diagnostics.

### Specifications & Behavior:
1. **Live In-Composer Prompt Token Estimator**:
   - As the user types in the message input box, a lightweight on-device tokenizer calculates the prompt token count in real-time (e.g. displaying `~42 tokens` near the send button).
   - Updates instantly on every keystroke with zero network latency, giving immediate cost and context awareness before the message is sent.
2. **Corner Circular Context Saturation Ring**:
   - Displayed in the conversation corner / message dock showing the active model's maximum window ceiling (e.g. `1M` or `128k`).
   - An intuitive circular progress arc that fills up as conversation history and responses accumulate.
   - When the circle becomes completely full, it visually alerts the user that the model's context window limit has been reached.
3. **Interactive Double-Tap Deep Dive Gesture**:
   - **Double-tapping** the corner circular bar opens an instant **Context Diagnostics Sheet**:
     - **Exact Prompt Tokens**: Breakdown of user messages, system prompt, and tools.
     - **Exact Completion Tokens**: Model-generated output tokens across turns.
     - **Remaining Free Runway**: Exact number of remaining free tokens before truncation/compaction triggers.
     - **Context Health Action**: Quick one-tap shortcut to trigger manual context compaction or switch to a higher-capacity 1M-token model.
4. **Backend REST Endpoints**:
   - `GET /api/rooms/:id/context-breakdown` -> Returns detailed token allocation: `{ system_tokens, history_tokens, active_draft_tokens, completion_tokens, free_tokens, max_window: 1000000 }`.
5. **UI Mapping (Pending Frontend)**:
   - *Compose Token Badge*: Subdued monospace tag in the input box showing live token estimate (`45 tok`).
   - *Corner Circular Gauge*: Compact circular ring with `1M` center label that fills clockwise as context saturates.
   - *Double-Tap Context Deep-Dive Modal*: Glassmorphic HUD overlay showing exact token distributions and compaction actions.

---

## 15. Multi-Agent Group Rooms & Mention-Based Inter-Agent Dispatch

Allows creating multi-agent collaborative rooms where users and agents communicate, pass work, and trigger each other using `@mentions`.

### Specifications & Behavior:
1. **Isolated Multi-Agent Rooms**:
   - Users can create customized rooms (e.g. "Core Team", "Rust Refactor", "Security Audit") and invite multiple CLI agents (`opencode`, `hermes`, `omnirush`, `codex`, `cline`).
   - Each room maintains its own isolated message history, execution state, and active process slots.
2. **Mention-Based Dispatch Engine (`@AgentName`)**:
   - **User Mentions**: Typing `@opencode` or `@hermes` routes the turn exclusively to that specific agent.
   - **Inter-Agent Autonomous Collaboration**: When an agent finishes a step and mentions another agent in its reply (e.g., *\"@codex please run the compiler checks on this patch\"*), the mentioned agent automatically wakes up, takes the context, executes the task, and responds back into the room!
   - **Natural Name Recognition**: Agents also respond when addressed by their exact natural name in conversation without needing rigid syntax.
3. **Broadcast & Consensus Modes**:
   - Sending a message with `@all` or omitting mentions in a collaborative room allows all participants to analyze the problem or convene a multi-agent review loop.
4. **Backend REST & WebSocket Endpoints**:
   - `POST /api/rooms` -> Create room (`{ "name": "...", "agent_ids": ["hermes", "opencode", "codex"] }`).
   - `POST /api/rooms/:id/messages` -> Post message with automated `@mention` parsing and async task dispatch.
   - `WS /ws/:room_id/:client_id` -> Real-time multi-agent message delivery and typing indicators.
5. **UI Mapping (Pending Frontend)**:
   - *Multi-Agent Room Creator*: Dialog to name the room, set the goal, and pick participating agents from the fleet.
   - *Mention Auto-Complete Popup*: Typing `@` in the compose box pops up a participant picker with agent avatars.
   - *Active Speaker Indicator*: Displays which agent is currently executing or responding in the room.

---

## 16. Metallic M3 Expressive Graphical Interface, Extensible Feature Architecture & Message-Grade Motion System

The Android client becomes a **metallic, motion-first control surface** for the whole
agent fleet, architected so **new/external features can be added later without
rewriting existing code** (one new file + one registry line), and so every future
edit lands in a single, small, single-responsibility file.

### 16.1 Extensible Feature Slot Registry (external-feature ready)

1. **Feature contract** (`core/feature/FeatureSlot.kt`):
   - A feature is a pure data+composable unit: `id`, `title`, `icon`, `order`,
     `enabled`, and a `@Composable fun FeatureSlot.Content(scope, client)`.
   - No feature may reach into another feature's internals. Features communicate
     only through the shared repositories and the WebSocket bus.
2. **Catalog** (`feature/registry/FeatureCatalog.kt`): the single list where every
   shipped feature is registered. Adding an external feature = write one new file
   under `feature/<name>/` + add one `FeatureSlot(...)` entry here.
3. **Shell** (`feature/shell/`): renders the registered slots as a metallic
   navigation rail + content pane, so a new slot appears in the shell with zero
   changes to the shell code.
4. **Backend-driven slots**: the core's `GET /api/features` (when present) can
   advertise extra slots; the client renders unknown slots as a safe
   "read-only capability" card instead of crashing.

### 16.2 Chunked Module Layout (one concern per file)

| Path | Responsibility |
|---|---|
| `core/feature/FeatureSlot.kt` | The extensibility contract only. |
| `core/motion/MotionTokens.kt` | All easings/springs/durations in one file. |
| `core/design/MetallicTokens.kt` | Metallic palette roles, brushes, shape scale. |
| `data/model/*.kt` | One file per domain (`Agent`, `Room`, `Message`, `Telemetry`, `Provider`). |
| `data/remote/TalosApi.kt` | One API client per domain group. |
| `data/repository/*.kt` | One repository per domain, `StateFlow`-exposed. |
| `feature/<name>/*.kt` | One screen + one state holder per feature. |
| `ui/MetallicKit.kt` | The shared metallic component kit every screen uses. |

Rule: **no file exceeds one screen's worth of logic.** A single line change must
never require touching more than the file that owns the behaviour.

### 16.3 Metallic UI Component Kit (every button, every surface)

Every interactive element is re-authored on the metallic system — no stock M3
buttons, no hardcoded `Color.White`, no legacy tokens:

1. `MetalButton` (primary/tonal/outlined) — spring press `scale(0.96f)` on the
   M3 Expressive fast-spatial spring, in-button `LoadingIndicator` crossfade
   (never a stock spinner), disabled state at `onSurface @ 12%`.
2. `MetalIconButton` — 40dp minimum target, machined socket, tint from scheme role.
3. `MetalChip` / `MetalFilterChip` / `MetalSegmentedSwitch` — segmented switcher
   for view perspectives, animated thumb on the default spatial spring.
4. `MetalCard` / `MetalSheet` / `MetalDialog` — multi-stop brushed-metal border
   brush from the active scheme, 5 surface-container tiers, tonal elevation.
5. `MetalListItem` — leading machined emblem well, headline/supporting, trailing slot.
6. `MetalTextField` / `MetalSearchField` — scheme-role fills, focused border in `primary`.
7. `MetalProgressRing` / `MetalContextRing` — determinate arc, bounded repaint rate.
8. `MetalStatusPill` — live state with hardware status dot + uppercase tracking.
9. `MetalNavRail` / `MetalBottomDock` — tactile, spring-animated selection.
10. **Zero emoji rule**: Material vector icons only, sentence-case labels.

### 16.4 Message-Grade Motion System (research-grounded)

All motion is centralized in `core/motion/MotionTokens.kt` and derived from
measured Telegram/WhatsApp behaviour (see `docs/MOTION_SPEC.md` for the raw
measurements and sources):

| Interaction | Spec | Source lineage |
|---|---|---|
| Standard transition curve | `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)` | Telegram Web K `--transition-standard-easing` |
| Enter transition | 200–300 ms / emphasized decelerate `cubic-bezier(0.05,0.7,0.1,1)` | Telegram Web K `--transition-standard-in-time` |
| Exit transition | 150–250 ms / emphasized accelerate `cubic-bezier(0.3,0,0.8,0.15)` | Telegram Web K `--transition-standard-out-time` |
| Sheet present/dismiss | 250 ms, standard curve | Telegram Android `BottomSheet` |
| Press feedback | spatial spring `damping 0.6 / stiffness 800`, scale `0.96f` | M3 Expressive + Telegram Android `EASE_OUT_BACK` |
| Message bubble enter | alpha + `translateY 12dp→0` + `scale 0.96→1`, default spatial spring | Telegram/WhatsApp list entry behaviour |
| Typing indicator | keyframe loop on emphasized decelerate (springs forbidden for loops) | WhatsApp Web 0.333 s / 0.417 s cadence |
| Mention popup | `scale 0.92→1` fast spatial spring + alpha | WhatsApp Web `cubic-bezier(.22,1,.36,1)` |
| Context ring sweep | continuous arc, bounded to 1 repaint per frame, no layout work | M3 live-telemetry rule |
| List stagger | 30–50 ms per item, `translateY` + alpha | WhatsApp Web stagger cadence |

**Rules**: spatial springs for position/size/shape; critically damped effects
springs for color/alpha (never overshoot); springs never used for infinite
loops; decorative motion is skipped when the system animation scale is 0.

### 16.5 Screen Inventory & UI Mapping

| Slot | Route source | Core endpoints consumed |
|---|---|---|
| **Agent Fleet** | `GET /api/agents`, `/api/agents/pool`, `/api/agents/:id/models`, `POST /api/agents/:id/model`, `POST /api/agents/scan`, `POST /api/agents/register`, `POST /api/agents/:id/setup`, `POST /api/agents/:id/run` | agent registry, dynamic model switching, setup, pool status |
| **Agent Tree** | `GET /api/agents/:id` (parent/child fields) | hierarchical inspection + live sub-agent steering |
| **Reasoning Control** | `POST /api/agents/:id/reasoning` | 8-tier reasoning effort ladder + ultra code mode |
| **Provider Vault** | `GET/POST /api/providers/custom`, `POST /api/providers/custom/:id/fetch-models`, `PATCH /api/providers/custom/:id/status`, `PUT /api/providers/custom/:id/fallbacks`, `DELETE /api/providers/custom/:id` | vaulted providers, auto model fetch, priority fallback cascade |
| **Context Telemetry** | `GET /api/agents/:id/context`, `GET /api/rooms/:id/context-breakdown`, `GET/POST /api/telemetry/config` | 1M saturation, token velocity, 0s/5s dual sync |
| **Composer Context Ring** | local tokenizer + `GET /api/agents/:id/context` | live prompt token estimate, corner ring, double-tap exact inspect |
| **Group Rooms** | `GET/POST /api/rooms`, `GET/POST /api/rooms/:id/messages`, `WS /ws/:room_id/:client_id` | room creation, `@mention` dispatch, active speaker |
| **Raw Log Mirror** | `WS /ws/:room_id/:client_id` (+ provider stdout) | zero-filtering upstream log streaming |
| **Shell** | registry + mesh status | Tailscale gate, feature slots |

### 16.6 Privacy & Runtime Rules (unchanged, enforced in UI)

- No IP, hostname, overlay address, peer name or host system detail is ever
  rendered; rows show node names and transport only.
- Provider API keys are masked in every surface (`••••` + last 4) and never
  returned by the core.
- No fabricated addresses: the client re-discovers the endpoint at runtime and
  reports "offline" honestly.

---

## 17. Host-Authorized Agent API Key Lifecycle & One-Tap Pairing

Shifts the authorization authority entirely to the desktop/laptop host machine, eliminating complex mobile-side key minting.

- [x] **Host-Centric Key Generation**: The desktop/laptop host (`talos-core`) is the single source of truth for generating, hashing, and storing API keys (`POST /api/tokens`, `GET /api/pairing/invite`).
- [x] **Tailscale CGNAT Detection**: Client inspects network interfaces and `LinkProperties` for the `100.64.0.0/10` address space (`100.64.x.x`–`100.127.x.x`), identifying active Tailscale WireGuard overlay without relying on brittle Android OS heuristics.
- [x] **Host API Key Input (Wizard Step 3)**: Mobile app prompts user to activate Tailscale and paste or confirm their host-minted API key rather than attempting client-side token fabrication.
- [x] **UI Mapping**:
  - *Host API Key Authorization Step*: Monospace token field with verification badge and host generation commands (`bash scripts/pairing_link.sh`).

---

## 18. Native In-App Web Browser & Strict 200 OK Handshake Gating

Eliminates external browser roundtrips and enforces strict cryptographic verification with zero bypass shortcuts.

- [x] **Zero Bypass Architecture**: Removed all bypass/continue anyway buttons across ServiceGate and OnboardingWizard. Real Tailscale health check (`GET /health`) is mandatory.
- [x] **Zero Token Fabrication**: `POST /api/auth/pair` strictly returns 401 Unauthorized on invalid tokens and never auto-generates guest tokens. Mobile client no longer fabricates fake UUID tokens.
- [x] **Live 200 OK Verification**: `verifyAndConnect` actively validates both Tailscale node reachability and host-minted API token, transitioning into the Council chamber only upon genuine HTTP 200 OK response.
- [x] **Native Embedded Browser (`TalosBrowserScreen`)**: Built-in Android WebView with metallic address bar, progress bar, reload, and navigation controls registered in `FeatureCatalog` (slot 40), allowing users to browse host portals and local node tools without leaving the app.

---

## 19. Dynamic Tailscale Host IP Update & Agent Bootstrap PRD

Eliminates network scanning friction and provides flexible runtime host IP management for changing network environments.

- [x] **Settings Tailscale IP Update**: Dedicated connection settings dialog in the Android app allows updating the host Tailscale endpoint (`baseUrl`) and API Key at runtime without clearing app data or resetting council state.
- [x] **Live Verification in Settings**: Built-in `verifyAndConnect` check verifies the updated IP against `/health` and the key against `/api/auth/pair`, displaying live status (green 200 OK or detailed error).
- [x] **Direct Setup Step**: Replaced animated radar scanning loops in the onboarding wizard with a clean, immediate prompt card and direct Tailscale IP + Key fields.
- [x] **Agent Setup Prompt (`INSTALLING.md`)**: A standardized, copy-pasteable prompt card in the mobile UI instructing AI coding agents:
  `"Read this agent installing .Md file (INSTALLING.md) to start talos-core in the background and output the Tailscale IP and API Key."`
- [x] **Automated Host Setup (`scripts/agent_setup.sh`)**: One-line script that compiles and starts `talos-core`, resolves the Tailscale IPv4 address, mints an authorized pairing token, and formats the user output.

---

## 20. Single Host API Key Policy & Universal Safe Credential Management

Enforces cryptographic key discipline and safe multi-platform host bootstrap.

- [x] **Single Host API Key Policy**: The host daemon maintains exactly ONE persistent API key for companion pairing. Subsequent bootstrap runs test and reuse the existing key, preventing token pollution and desynchronization.
- [x] **Safe Credential Storage (`~/.config/talos/credentials.json`)**: Credentials are stored in a dedicated user configuration directory with owner-only permissions (`chmod 600`), completely avoiding `/tmp` or repository pollution.
- [x] **Universal Cross-Platform Bootstrap (`scripts/agent_setup.py` / `scripts/agent_setup.sh`)**: Natively executes on Linux, macOS, WSL, and Windows with automatic environment detection, background daemon supervision, Tailscale IP discovery, and idempotent single-key management.
- [x] **Standardized Agent Output**: Enforces the exact two-line pairing response contract for all AI coding agents.

---

## 21. Official OpenClaw Brand Vector Integration from Dashboard Icons MCP

Replaced custom hand-drawn SVG with the official brand asset fetched directly via Dashboard Icons MCP.

- [x] **Official SVG Extraction**: Retrieved official `openclaw` vector asset from `homarr-labs/dashboard-icons` via `mcp__dashboard_icons__get_icon_svg`.
- [x] **VectorDrawable Precision Conversion**: Converted all 57 SVG paths (`viewBox 0 0 500 500`) with authentic styling classes into `ic_openclaw.xml` (`#EF0011`, `#BA000D`, `#0B0303`, `#F6F4F4`).
- [x] **Seamless Agent Card Hierarchy**: OpenClaw card on Step 4 of the Onboarding Wizard and in the Council Chamber now renders with authentic brand quality identical to Hermes, Claude, Codex, Gemini, and OpenAI.

---

## 22. Anti-Slop Molten Forge Design Overhaul & M3 Expressive Motion Integration

Eliminated the generic AI violet baseline in favor of an authentic machined instrument aesthetic: Molten Forge (Copper, Steel, and Verdigris Patina on Graphite).

- [x] **Brand Palette Transformation**:
  - Replaced generic AI-default purple (`#6750A4`/`#D0BCFF`) with Molten Copper (`#E0A261` Dark / `#8A5524` Light), Machined Steel (`#B9C0C8` / `#4E5A66`), and Verdigris Patina (`#7FC8BB` / `#2F6A60`).
  - Verified WCAG AA contrast compliance across all 24 semantic roles in both Dark (OLED Graphite `#121316`) and Light (Bone Paper `#FBF8F3`) modes.
- [x] **M3 Expressive Motion Polish**:
  - Re-anchored all dialog confirmations and update actions to canonical `M3EButton` featuring spatial spring press physics and `LoadingIndicator` in-button loading states.
  - Implemented critically-damped effects spring transitions (`animatedColorSchemeOf`) for theme interpolation without chromatic overshoot.
  - Replaced fixed-color status text and hardcoded tints with mode-aware `LocalTalosStatus` semantics and `EmblemSocket` substrates.
- [x] **Dynamic Core Token Migration**:
  - Updated `talos-core` theme repository and runtime database migration to serve Molten Forge tokens over `GET /api/theme`.

---

## 23. Benchmark Command Dock Architecture (Apple Design & M3 Expressive Integration)

Overhauled the mobile chat input bar inspired by Telegram and WhatsApp benchmark architectures, refined through Apple Design foundations and Anti-Slop UI discipline.

- [x] **Calibrated Touch Geometry & Exact Proportions**:
  - Anchored layout to exact 1080p mobile resolution (48dp / 84px capsule and action button heights).
  - Standalone floating Molten Copper action button placed with 8dp optical clearance from input capsule.
  - Symmetrical geometry across Dark (`#1E2024` on OLED `#121316`) and Light (`#FFFFFF` on `#FBF8F3`) modes.
- [x] **System-Grade Tool Clustering**:
  - Replaced generic payment/currency icons with professional system-grade vector icons: leading `(☺)` Agent mode toggle, trailing inline `(📎)` Attach Context, and `(🎙️)` Audio Prompt Mic.
  - Eliminated rigid vertical jail-bar dividers in favor of natural optical breathing margins.
- [x] **Apple Specular Catching & Zero-Glow Physics**:
  - Replaced blurry neon halos and fuzzy dropshadows with tight physical contact shadows (`feDropShadow dx=0 dy=3 stdDeviation=4`).
  - Added subtle 1px specular highlight along the top edge representing light catching the material edge.
  - Preserved M3 Expressive 12-lobed scallop shape morph (`circle` -> `scallop12`) and spatial press spring feedback.

---

## 24. Fluid Dynamic Mic-to-Send Morph & Quick Command Strip

Further refined the mobile chat input bar inspired by Apple Design interruptibility and Telegram/WhatsApp seamless tool switching.

- [x] **Dynamic Standalone Action Dial**:
  - Standalone Molten Copper floating button now automatically morphs between Audio/Mic Mode (`Icons.Default.Mic` when input is empty) and Send Command Mode (`Icons.AutoMirrored.Filled.Send` when text is present) via M3 Expressive fast spatial `Crossfade`.
  - Eliminated the redundant inside mic button, keeping the input capsule interior clean with only `AttachFile` on the right.
- [x] **Apple Proximity Quick Command Strip**:
  - Integrated an interactive horizontal command chip bar (`/consensus`, `/mesh`, `/clear`, `/benchmark`, `/status`) above the input capsule when typing or entering `/`.
  - Tapping any chip immediately fills the input field with haptic responsiveness.

---

## 25. Complete Legacy Token Purge & Authentic Brand Anchor Unification

Audited and eliminated all stray legacy slate and violet color tokens across hero components and landing animations.

- [x] **Full Legacy Token Eradication**:
  - Replaced remaining `Color(0xFF0F172A)` slate surfaces in `MainActivity.kt` with theme-governed `EmblemSocket` (`#101114`).
  - Purged retired baseline-violet `Color(0xFF6750A4)` from `LandingScreen.kt` theme toggle button, harmonizing it with `PrimaryAccent` (`#E0A261`).
- [x] **Dynamic Node Beam Accents**:
  - Connected landing page fluid conduit beams and particle photons directly to authentic brand emblems (`emblemByKey(key).accent`), removing hardcoded hex values across all 5 agent nodes.

---

## 26. Agent Fleet Dynamic Feedback & Apple Rounded Modal Polish

Refined the multi-agent management console in `AgentFleetScreen.kt` adhering to Apple latency and modal sheet guidelines.

- [x] **Zero-Latency Loading Feedback**:
  - Added a centered `CircularProgressIndicator` with `PrimaryAccent` while `client.fetchAgents()` queries the core daemon, eliminating empty screen flashes.
- [x] **Apple Continuous Curvature Modal**:
  - Styled model switcher `AlertDialog` with continuous 22dp rounded corners (`RoundedCornerShape(22.dp)`), high-contrast dismiss typography, and unified metallic border framing.

---

## 27. Speech Bubble Natural Geometry & Dynamic Wave Typing Indicator

Polished chat message components in `Components.kt` aligning with Apple iMessage and Telegram physical speech bubble ergonomics.

- [x] **Natural Speech Bubble Anchor Geometry**:
  - Reoriented message bubble tails to anchor naturally at the bottom baseline (`bottomEnd = 4.dp` for user, `bottomStart = 4.dp` for agents), replacing inverted top-tail corners.
  - Aligned audio player bubble borders with metallic chamfer brushes.
- [x] **Dynamic Wave Typing Motion**:
  - Enhanced `ThreeDotTypingIndicator` with synchronized vertical wave bouncing (`translationY`) alongside alpha fades, matching Apple iMessage fluid conversation cues.

---

## 28. Multi-Skill UI/UX Pro Max & M3 Expressive Engineering: Zero RenderNode Blur & Metallic Unification

Applied cross-cutting design rules from `ui-ux-pro-max`, `make-interfaces-feel-better`, and `m3-expressive-ui-engineering` to chat surfaces.

- [x] **Zero Shadow Blur Pass on Scrollable Cards**:
  - Eliminated hardware `shadowElevation = 1.dp` passes from nested cards (`NativeAudioPlayerBubble`, `NativeVideoPreviewBubble`, `CollapsibleThoughtBlock`, `ToolCallItem`), removing 40fps GPU blit stalls during rapid flings and message feeds.
- [x] **Machined Metallic Chamfer Framing**:
  - Replaced flat `outlineVariant` strokes with brushed gradient `metallicBorderBrush()` across all nested reasoning and tool status items, unifying them with the Molten Forge hardware aesthetic.

---

## 29. Accessible Touch Target Geometry & Tabular Numerals Telemetry

Implemented design engineering principles from `make-interfaces-feel-better` and `ui-ux-pro-max` on the chamber top bar and telemetry instrumentation.

- [x] **Tabular Monospace Latency Telemetry**:
  - Styled mesh ping and live latency indicators (`MESH LINKED · 12ms`) with `FontFamily.Monospace`, eliminating horizontal layout jitter and width shifts as latency updates.
- [x] **WCAG Accessible Touch Targets (40dp)**:
  - Scaled action button containers (`Theme Toggle`, `Settings`, `Return to Landing`) from cramped 34dp to 40dp with optically centered 19dp icons, fulfilling mobile touch target standards and preventing missed taps.

---

## 30. Tactile Press Feedback Calibration & MotionToken Normalization

Calibrated touch physics across onboarding flow components according to `make-interfaces-feel-better` Rule 12 and `frontend/multi-skill-ui-refinement`.

- [x] **Universal 0.96f Tactile Press Scale**:
  - Replaced subtle, imperceptible 0.98f scale factors in `OnboardingWizard.kt` (`WizardBottomBar` CTA and `OverviewElevatedCard`) with canonical `0.96f` tactile press feedback.
  - Aligned wizard touch targets with `MotionTokens.PRESS_SCALE` (`0.96f`), unifying tactile response across both onboarding cards and main chamber controls.

---

## 31. Navigation Drawer Emblem Sockets & Tactile Selection State

Refined drawer navigation and drawer toggle ergonomics adhering to `frontend/touch-gestures-and-vector-craft` and `frontend/multi-skill-ui-refinement`.

- [x] **Machined Drawer Emblem Sockets**:
  - Wrapped room and agent emblems in `ModalNavigationDrawer` inside dedicated 32dp `EmblemSocket` containers (`#101114`) with dynamic accent borders, preventing multi-colored vendor marks from washing out on light drawer sheets.
- [x] **Tactile Chamber Selection State**:
  - Upgraded chamber item rows from basic 8dp rounded rectangles to 12dp concentric shapes with subtle 1dp Molten Copper highlight borders (`PrimaryAccent.copy(alpha = 0.35f)`) and 40dp drawer toggle hit area.

---

## 32. M3 Motion Effects Physics Alignment (Zero-Overshoot Crossfade)

Audited motion physics according to `m3-motion` and `frontend/multi-skill-ui-refinement` guidelines regarding spatial vs effects spring separation.

- [x] **Critically Damped Effects Spring for Opacity Crossfades**:
  - Identified and eliminated an M3 anti-pattern where an underdamped spatial spring (`damping = 0.6f`, `stiffness = 800f`) was driving an opacity `Crossfade` in `MainActivity.kt`.
  - Re-bound the Action Dial icon transition (Mic -> Send) to `M3EMotion.fastEffects()` (critically damped `zeta = 1.0f`), guaranteeing that opacity interpolations never bounce or overshoot past 1.0.

---

## 33. Anti-Slop Unification: Consolidation of Stacked Navigation Rails

Eliminated dual stacked horizontal scrollbars adhering to `anti-slop-ui-discipline` Rule 1 and Rule 2 ("No Decorative Filler Elements").

- [x] **Unified Single-Row Contextual Navigation Rail**:
  - Removed the redundant secondary `LazyRow` that previously forced users to scroll two vertically stacked chip rails on mobile viewports.
  - Consolidated active chamber selectors (`Core DM`, `The Council`) and modular perspective switchers (`Fleet`, `Telemetry`, `Providers`, `Browser`) into a single, cohesive horizontal rail, saving **44dp of vertical viewport** on mobile screens.

---

## 34. Mobile Command Action Dial Press Physics Normalization

Calibrated floating action dial haptic response according to `mobile-chat-architecture` Section 3.3 and `make-interfaces-feel-better` Rule 12.

- [x] **Canonical 0.96f Action Dial Press Scale**:
  - Replaced an exaggerated 0.92f deflation scale on the floating Action Dial (Mic / Send button) with standard `0.96f` tactile press feedback (`MotionTokens.PRESS_SCALE`).
  - Achieved 100% tactile scale unification across all interactive elements in `MainActivity`, `OnboardingWizard`, and `LandingScreen`.

---

## 35. Clean Architecture Deterministic WebSocket Lifecycle Teardown

Implemented deterministic lifecycle management conforming to `android-clean-architecture` resource management principles.

- [x] **Public `disconnect()` in `TalosClient`**:
  - Added clean WebSocket closure method sending standard code 1000 (`Client disconnect`), setting `_isConnected` to false, and nulling the active socket.
- [x] **Activity Lifecycle Teardown Hook in `MainActivity`**:
  - Overrode `onDestroy()` in `MainActivity` to invoke `client.disconnect()`, guaranteeing that socket connections and background reader threads do not linger or leak system resources when the Android Activity is closed or killed.

---

## 36. System Edge-to-Edge Navigation Architecture

Implemented official Google `android/skills@edge-to-edge` patterns for Android 15 (SDK 35) system bar immersion.

- [x] **Canonical `enableEdgeToEdge()` Initialization**:
  - Bound `androidx.activity.enableEdgeToEdge()` immediately before `super.onCreate(savedInstanceState)` in `MainActivity.kt`.
  - Allowed Compose scaffolds, top app bars, and bottom action dials to draw natively beneath system status and gesture navigation bars without letterboxing or black letterbox pillars.

---

## 37. Landing Screen Accessible Touch Targets & Molten Forge Border Unification

Aligned `LandingScreen.kt` header ergonomics with `ui-ux-pro-max` WCAG standards and Molten Forge hardware materials.

- [x] **Universal 40dp Theme Toggle Target**:
  - Upgraded landing screen theme toggle container from 38dp to standard `40.dp` with an optically balanced 19dp icon.
- [x] **Molten Forge Brushed Rim**:
  - Replaced low-contrast `outlineVariant` border with `metallicBorderBrush()`, matching the machined chamfer styling of the main chamber top bar.

---

## 38. WCAG Semantics & Actionable Accessibility Parity

Audited accessibility strings and container semantics adhering to `ibelick/ui-skills@fixing-accessibility` Priority 1 ("Accessible Names").

- [x] **Actionable Theme Toggle Semantics**:
  - Replaced ambiguous static noun content description (`"Theme"`) with explicit dynamic state descriptions (`if (isDark) "Switch to Light Mode" else "Switch to Dark Mode"`), aligning `MainActivity.kt` with `LandingScreen.kt`.
- [x] **Action Dial Surface Semantics**:
  - Bound explicit accessibility semantics (`if (isSendMode) "Send message" else "Voice input"`) to the floating Action Dial container surface, ensuring screen readers announce the morphing intent prior to child icon traversal.

---

## 39. Synchronized updateTransition Frame Architecture

Replaced fragmented multi-state animation drivers conforming to `chrisbanes/skills@compose-animations` Section 53 ("Coordinate multiple properties from one state").

- [x] **Single-Clock `updateTransition` Coordination**:
  - Replaced pairs of independent `animateFloatAsState` coroutine drivers with unified `updateTransition(targetState = isDark)` in both `MainActivity.kt` and `LandingScreen.kt`.
  - Bound rotation (`themeRot`) and scale (`themeScale`) to the exact same frame-clock transition, eliminating frame-time phase drift, coroutine scheduling overhead, and multi-state race conditions during theme switching.

---

## 40. Context-Aware Message Scroll Effect Keying & Instant Room Switching

Refactored side-effect lifecycle management conforming to `chrisbanes/skills@compose-state-and-effects` Rule 2 ("Key an effect by the semantic input that should restart or dispose it").

- [x] **Compound Effect Keying (`currentRoomId, messages.size`)**:
  - Previously, `LaunchedEffect(messages.size)` failed to scroll to the bottom when switching between rooms that happened to possess identical message counts.
  - Added `currentRoomId` to the effect key tuple so room transitions always guarantee the user lands at the most recent message.
- [x] **Instant Room Navigation vs Animated Incoming Messages**:
  - Switching rooms now executes immediate `listState.scrollToItem()`, eliminating disorienting multi-second animated flings across long message logs.
  - New incoming messages in the active room continue to smoothly call `listState.animateScrollToItem()`.

---

## 41. Lazy Layout Performance Optimization: Stable Keys, contentType, and animateItem

Applied Google Developer Expert benchmarks from `skydoves/compose-performance-skills@optimizing-lazy-layouts` across all scrollable lists.

- [x] **Stable Identity Keys on Drawer & Navigation Rails**:
  - Added server-side ID keys (`key = { it.id }`) and `Modifier.animateItem()` to drawer rooms and feature slots, preventing whole-tree recomposition during room creation or reordering.
- [x] **View-Type Recycling via `contentType = { it.sender_role }`**:
  - Supplied explicit content type discriminators to message feed `items(...)`, ensuring Compose reuses layout slots for identical message roles (User vs Agent) rather than rebuilding diverse composable trees on scroll ticks.
- [x] **Allocation Hoisting in Command Autocomplete**:
  - Hoisted suggestions list allocation into `remember { listOf(...) }` outside the `LazyRow` DSL block, eliminating object churn per scroll tick.

---

## 42. Compose Component Design: Root Modifier Placement & Wrapper Node Pruning

Refactored component signatures adhering to `chrisbanes/skills@compose-component-design` Rule 1 & Rule 2 ("Caller-placeable roots and modifier placement").

- [x] **Root `modifier: Modifier = Modifier` in `MessageBubble`**:
  - Added standard default modifier parameter to `MessageBubble` composable and bound it as the first element in the root `Row` modifier chain.
- [x] **Pruning Artificial `Box` Wrapper Composables in Chat Feed**:
  - Eliminated the redundant intermediate `Box(modifier = Modifier.animateItem())` wrapper around every message item in `MainActivity.kt`.
  - Passed `Modifier.animateItem()` directly to `MessageBubble`, reducing the Compose layout tree depth by 1 full layer across every message item in the chat feed.

---

## 43. Edge-to-Edge IME Keyboard Insets & Interactive Focus Navigation

Resolved software keyboard layout occlusion identified in `OnboardingWizard` Step 2 ("Tailscale Mesh & Host Link") conforming to Google's `android/skills@edge-to-edge` and `chrisbanes/skills@compose-focus-navigation`.

- [x] **IME Window Inset Padding in `OnboardingWizard` & `ServiceGate`**:
  - Bound `.imePadding()` to the root container columns in `OnboardingWizard.kt` and `ServiceGate.kt`.
  - Added `.imeNestedScroll()` on the scrollable step viewport, allowing the scroll container to shrink dynamically when Gboard opens and scroll the focused "Core Tailscale Address" and "Host API Key" text fields cleanly into view above the keyboard.
- [x] **Tactile Focus Reaction on Command Capsule (`MainActivity.kt`)**:
  - Bound `Modifier.onFocusChanged` to the chat message input field.
  - Dynamically switches the outer metallic capsule stroke from resting machined grey to an active Molten Copper + Verdigris gradient highlight when focused.
- [x] **Accessible Quick Command Chips (`Role.Button`)**:
  - Added explicit semantic `Role.Button` and `onClickLabel` descriptors on command suggestion pills (`/consensus`, `/mesh`, `/status`), adhering to accessibility traversal standards.

---

## 44. Package Manifest Version Code & Name Parity

Resolved Android package manager stale version stamp in `android/app/build.gradle.kts`.

- [x] **Version Code & Name Synchronization**:
  - Bumped `versionCode` from stagnant `103` to `21012` and `versionName` from legacy `"2.3.0"` to match release `"2.10.12"`.
  - Ensures Android Package Installer immediately recognizes incoming releases as clean upgrades, overwriting older builds and accurately displaying `version 2.10.12` in system App Info settings.

---

## 45. Release Artifact Dual-Naming Architecture

Resolved generic artifact filename ambiguity in `.github/workflows/build-apk.yml`.

- [x] **Tag-Specific Versioned Binary Uploads**:
  - Tagged release builds now generate and publish both `talos-v${TAG}.apk` (e.g. `talos-v2.10.13.apk`) and `talos-latest.apk`.
  - Eliminates filename collisions in Android downloads (`talos-latest (1).apk`, `(2).apk`) and gives instant clarity on the exact binary version installed on device.
- [x] **Incremented Version Code & Name**:
  - Bumped `versionCode` to `21013` and `versionName` to `"2.10.13"`.

---

## 46. Official Material Design 3 Typescale Calibration

Integrated Google's official Material Design 3 specifications discovered via `hamen/material-3-skill` (`github.com/hamen/material-3-skill`).

- [x] **Calibrated `TalosTypography` to Exact MD3 Specification**:
  - Replaced non-standard scaled type values with Google MD3 baseline standards across all 15 typographic roles:
    - `Display`: Large (57sp/64sp), Medium (45sp/52sp), Small (36sp/44sp)
    - `Headline`: Large (32sp/40sp), Medium (28sp/36sp), Small (24sp/32sp)
    - `Title`: Large (22sp/28sp), Medium (16sp/24sp), Small (14sp/20sp)
    - `Body`: Large (16sp/24sp), Medium (14sp/20sp), Small (12sp/16sp)
    - `Label`: Large (14sp/20sp), Medium (12sp/16sp), Small (11sp/16sp)
  - Eliminates artificial text compression in standard Compose components and establishes typographic balance across all screen densities.
- [x] **Incremented Version Code & Name**:
  - Bumped `versionCode` to `21014` and `versionName` to `"2.10.14"`.

---

## 47. Action Dial Double-Tap Camera Reveal & Mode-Switch Morph

Implemented user-requested gesture + animation upgrade on the command dock's Floating Action Dial (`MainActivity.kt`).

- [x] **Double-Tap Mic → Camera Reveal**:
  - Replaced the dial's plain `clickable` with `pointerInput` + `detectTapGestures` carrying `onTap`, `onDoubleTap`, and tactile `onPress` (press-scale + scallop morph physics preserved).
  - Double-tap in mic mode morphs the glyph **Mic → Camera** via `AnimatedContent` (spring scale 0.55→1.0 + fade, M3E `fastSpatial`), holds 700 ms, then launches the system camera (`MediaStore.ACTION_IMAGE_CAPTURE`, `runCatching`-guarded).
  - Glyph morphs back to Mic automatically after the camera hand-off.
- [x] **Typing ↔ Mic Mode Switch Animation**:
  - Upgraded the send/mic icon switch from a flat `Crossfade` to a full `AnimatedContent` morph: incoming glyph springs in with scale + fade (`fastSpatial` overshoot, `fastEffects` fade), outgoing glyph scales down and fades out.
  - Both directions (finish typing → send glyph; clear text → mic glyph) now play the expressive transition.
- [x] **Gesture Semantics & Availability**:
  - Agent-busy state (`isTyping`) keeps the dial inert (no gesture handling), matching previous `enabled = false`.
  - TalkBack descriptions updated: `"Voice input, double-tap for camera"` / `"Opening camera"` / `"Send message"`.
  - In send mode, taps stay instant (no double-tap timeout delay); the double-tap detector is only active in mic mode.
- [x] **Incremented Version Code & Name**:
  - Bumped `versionCode` to `21015` and `versionName` to `"2.10.15"`.

---

## 48. Gesture Patterns & Microinteractions Calibration: Hold-to-Camera & Rotational Switch Morph

Calibrated action dial gesture engine and micro-interaction animations based on `owl-listener/designer-skills@gesture-patterns` and `wondelai/skills@microinteractions`.

- [x] **Conflict-Free Long-Press (1000ms Threshold)**:
  - Eliminated single-tap / double-tap ambiguity. Single tap strictly triggers voice consensus without any camera interference.
  - Implemented 1000ms deliberate long-press trigger with immediate abort/cancel on early finger lift (<1000ms snaps back in 150ms with zero accidental triggers).
- [x] **Progressive Microinteraction Feedback**:
  - Added real-time 360° glowing Molten Copper + Verdigris sweep arc around the dial button during the 1-second hold.
  - Button smoothly blooms from 0.94f to 1.10f scale under finger, providing visual and tactile charging momentum.
  - Shutter snap and camera launch fire upon reaching full 1000ms completion.
- [x] **360° Spring Spin Morph on Typing Switch**:
  - Added full 360-degree rotational spring spin (`modeSpinAngle`) with `M3EMotion.defaultSpatial()` when switching between Mic and Send modes.
  - Accompanied by scale (0.5f ↔ 1.0f) and fade transitions on the glyph, making typing onset and text clearance feel responsive and alive.
- [x] **Incremented Version Code & Name**:
  - Bumped `versionCode` to `21016` and `versionName` to `"2.10.16"`.

---

## 49. Action Dial Architecture: Single-Tap Camera, Double-Tap Recording Dial & Swipe-Up Lock Interaction

Re-architecting the Command Dock Floating Action Dial to match Telegram / WhatsApp style camera and voice note mechanics as requested.

### Specification & State Machine
1. **Default Mode (Camera Action Dial)**:
   - **Glyph**: Camera icon (`Icons.Default.PhotoCamera`).
   - **Single Tap**: Instantly launches system camera (`Intent(MediaStore.ACTION_IMAGE_CAPTURE)`).
   - **Double Tap**: Expressive rotational morph into **Audio Recording Section** (glyph morphs smoothly to `Icons.Default.Mic`).
   - **Text Typing Override**: When user types (`inputText.isNotBlank()`), dial morphs into `Icons.AutoMirrored.Filled.Send` with 360° spring spin.

2. **Audio Recording Section**:
   - **Glyph**: Mic icon (`Icons.Default.Mic`).
   - **Double Tap** (when idle): Toggles back to **Camera Mode**.
   - **Hold-to-Record (Press Down)**:
     - Holding initiates recording state (`isRecording = true`).
     - A vertical floating **Swipe-Up Lock Capsule** animates above the mic button:
       - Glowing track with upward pulsing animated chevrons (`↑`).
       - Padlock glyph (`Lock` / `LockOpen`) with spring scale.
     - **Swipe Up Gesture Detection**:
       - While holding, vertical finger displacement (`dragY < -60.dp`) pulls the slider towards the lock target.
       - Reaching the threshold triggers **Hands-Free Lock (`isLocked = true`)** with tactile haptic snap.
   - **Locked Recording Bar (Hands-Free)**:
     - User releases finger; recording continues uninterrupted.
     - Input dock transitions into live recording telemetry:
       - Blinking red recording indicator + elapsed time counter (`00:01`, `00:02`...).
       - Animated audio waveform bars.
       - Discard / Cancel button (`Delete`) to abort recording.
       - Send button (`Send`) to finish and transmit audio command.
   - **Non-Locked Release**:
     - Lifting finger before locking stops and sends recording (or discards if < 300ms).
   - **Slide-to-Cancel**:
     - Dragging left (`dragX < -80.dp`) cancels recording with smooth fade-out.

- [x] **Implementation Plan**:
  - Add `VoiceRecordingState` (Idle, PressRecording, LockedRecording).
  - Build `SwipeUpLockTrack` overlay composable with spring physics.
  - Wire drag gestures on `pointerInput` tracking `awaitFirstDown` and pointer motion.
  - Implement hands-free locked recording control bar.
- [x] **Incremented Version Code & Name**:
  - Bumped `versionCode` to `21017` and `versionName` to `"2.10.17"`.
