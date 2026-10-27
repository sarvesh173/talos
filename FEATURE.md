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

## 9. Dynamic Reasoning Effort & Thinking Budget Control

Provides granular control over the depth of reasoning/thinking tokens across AI models and CLI agents.

### Specifications & Behavior:
1. **Normalized Reasoning Tiers**:
   - `off` / `none`: Zero thinking overhead. Pure fast completion.
   - `low`: Quick reasoning pass (OpenAI `low` / Claude ~2,000 thinking tokens).
   - `medium`: Balanced reasoning (OpenAI `medium` / Claude ~8,000 thinking tokens).
   - `high`: Deep reasoning (OpenAI `high` / Claude ~32,000 thinking tokens / DeepSeek R1).
   - `max` / `ultra`: Exhaustive thinking mode (ChatGPT Pro Max, Fable `favor-ultra`, Claude 64k tokens, OmniRush `:max` suffix).
2. **Provider & CLI Translation Engine**:
   - **OpenAI / Codex**: Translates to `reasoning_effort: "low" | "medium" | "high"`.
   - **Anthropic / Claude (3.7 Sonnet)**: Translates to `thinking: { type: "enabled", budget_tokens: <count> }`.
   - **Google Gemini (2.0/2.5 Thinking)**: Translates to `thinking_config: { thinking_budget: <count> }`.
   - **OmniRush & CLI Coding Tools**: Translates to CLI flags (`--effort <tier>`, `--reasoning <tier>`) or model identifier overrides (e.g. appending `:max`).
3. **Backend REST Endpoints**:
   - `GET /api/agents/:id/reasoning` -> Returns `{ agent_id, current_effort, supported_efforts }`.
   - `POST /api/agents/:id/reasoning` -> Payload `{ "effort": "low" | "medium" | "high" | "max" | "ultra" | "off" }`.
4. **UI Mapping (Pending Frontend)**:
   - *Reasoning Effort Segmented Control*: 5-step pill bar (`Off` | `Low` | `Med` | `High` | `Max/Ultra`) in Agent Settings and Room Composer.
   - *Thinking Flame Badge*: Dynamic visual indicator on the message dock displaying the active reasoning depth.
