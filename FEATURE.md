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
