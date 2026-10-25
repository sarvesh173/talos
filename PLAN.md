# TALOS Master Engineering Plan: Ground-Up Material 3 Expressive (M3E) Rust-First Architecture

> **Document Status:** Active Execution  
> **Target:** Complete TALOS Production Suite with Ground-Up M3E UI & Rust-First Core/Backend/Client  
> **Progress Tracker:** 5% (Phase 0/1 Completed, Planning & Audit Initialized)  

---

## 1. System Scope & Architecture Audit

### 1.1 Complete Feature Scope
TALOS is an **AI-Native Multi-Agent Collaboration Hub & Realtime Communication Engine** operating across encrypted mesh networks (Tailscale WireGuard).

1. **Mesh Discovery & Authentication:**
   - Tailscale WireGuard local node status detection (`/api/mesh/status`)
   - Mesh peer discovery (`/api/mesh/peers/discover`)
   - Cryptographic pairing token issuance & validation (`/api/auth/pair`, Bearer token verification)
   - Role-based access control (RBAC): `admin`, `agent`, `user`
2. **Multi-Agent Room Deliberation Bus:**
   - Multi-agent rooms (`the-council`, `war-room`, `code-review`, DM channels)
   - Real-time bi-directional WebSocket event bus (`/ws/{room_id}/{client_id}`)
   - Streaming tokens, reasoning thought blocks (`thought_delta`), tool execution lifecycles (`tool_call_start`, `tool_call_end`), and typing indicators
   - Turn-taking management with deterministic per-room mutex to prevent race conditions
3. **Democratic Consensus & Quorum Voting:**
   - Quorum proposal lifecycle (`ConsensusProposal`: create, open, vote, resolve)
   - Agent votes with choice (`approve`, `reject`, `abstain`), rationale text, and confidence metric
   - Dynamic consensus ratio calculations
4. **Provider Failover & Health Probing:**
   - Live health and latency probing across multi-model providers
   - Dynamic failover routing chain (`FailoverRouteResponse`)
   - Context-window discovery and model catalog management
5. **Agent Bench & Fleet Orchestrator:**
   - Agent bench roster management: summoning, benching, system prompts, role descriptions, color tags
   - Mention aliases and room-specific summon policies
6. **Security & Token Management:**
   - API Key issuance, revocation, usage auditing, masked previews

### 1.2 Required Screens & Navigation Topology
- **Screen 1: M3E Onboarding & Mesh Pairing**
  - Smooth 3-step setup (Mesh verification, Token pairing, Welcome node handover)
  - Interactive status radar and passkey generator instructions
- **Screen 2: Active Chamber / Deliberation Canvas**
  - Multi-agent conversation timeline with streaming message cards
  - Collapsible terminal-style reasoning drawers (`thought`)
  - Live typing indicators with animated M3 dot pulses
  - Floating pill command dock with shape-shifting send button
- **Screen 3: Consensus & Quorum Hall**
  - Proposal creation and review
  - Agent vote tally with live progress bars, vote breakdowns, confidence scores, and rationales
- **Screen 4: Provider & Model Health Matrix**
  - Live provider ping latency meters, uptime badges, context-window sizes, and failover order controls
- **Screen 5: Agent Fleet Bench & Summoner**
  - Visual agent roster with avatar badges, system prompts, model associations, and active chamber assignments
- **Screen 6: Token Vault & Security Inspector**
  - Token creation modal, role selectors, revoke toggles, and access audit logs
- **Screen 7: Settings & Motion Laboratory**
  - M3E Color Seed customizer (HCT palette preview: Standard, Medium, High Contrast)
  - Motion Scheme tuner (Spatial & Effects spring stiffness, damping ratios)
  - Diagnostic error log inspector

---

## 2. Color Theme System: Seed & HCT Tonal Palettes

Baseline Seed Color: **`#6750A4`** (Google M3 Baseline Iris/Purple) & **`#6366F1`** (TALOS Electric Indigo)  
Scheme Variant: **M3 Expressive (Vibrant & High-Chroma)**

### 2.1 Dark Scheme (Default OLED & Deep Twilight)
- **Primary:** `#D0BCFF` (Tone 80)
- **On-Primary:** `#381E72` (Tone 20)
- **Primary Container:** `#4F378B` (Tone 30)
- **On-Primary Container:** `#EADDFF` (Tone 90)
- **Secondary:** `#CCC2DC` (Tone 80)
- **On-Secondary:** `#332D41` (Tone 20)
- **Secondary Container:** `#4A4458` (Tone 30)
- **On-Secondary Container:** `#E8DEF8` (Tone 90)
- **Tertiary (Warm Rose Accent):** `#EFB8C8` (Tone 80)
- **On-Tertiary:** `#492532` (Tone 20)
- **Tertiary Container:** `#633B48` (Tone 30)
- **On-Tertiary Container:** `#FFD8E4` (Tone 90)
- **Surface:** `#141218` (Tone 6)
- **Surface Container Lowest:** `#0F0D13` (Tone 4)
- **Surface Container Low:** `#1D1B20` (Tone 10)
- **Surface Container:** `#211F26` (Tone 12)
- **Surface Container High:** `#2B2930` (Tone 17)
- **Surface Container Highest:** `#36343B` (Tone 22)
- **Outline:** `#938F99` (Tone 60)
- **Outline Variant:** `rgba(208, 188, 255, 0.14)` (Tone 30)

### 2.2 Light Scheme (Clean Editorial Lilac)
- **Primary:** `#6750A4` (Tone 40)
- **On-Primary:** `#FFFFFF` (Tone 100)
- **Primary Container:** `#EADDFF` (Tone 90)
- **On-Primary Container:** `#21005D` (Tone 10)
- **Secondary:** `#625B71` (Tone 40)
- **On-Secondary:** `#FFFFFF` (Tone 100)
- **Secondary Container:** `#E8DEF8` (Tone 90)
- **Surface:** `#FEF7FF` (Tone 98)
- **Surface Container:** `#F3EDF7` (Tone 94)
- **Surface Container High:** `#ECE6F0` (Tone 92)
- **Outline:** `#79747E` (Tone 50)
- **Outline Variant:** `#CAC4D0` (Tone 80)

### 2.3 High-Contrast Scheme (Accessibility Mode 1.0)
- Maximizes tonal separation between container surfaces and text/glyphs to achieve 7:1+ AAA contrast.

---

## 3. Motion System & Spring Physics Tokens

### 3.1 Spring Specifications (Unit Mass $m = 1.0$)
- **Spatial Springs (Position, Size, Shape Morphing, Scale):**
  - **Fast Spatial:** Damping Ratio $\zeta = 0.60$, Stiffness $k = 800.0$ ($c \approx 33.94$)
  - **Default Spatial:** Damping Ratio $\zeta = 0.80$, Stiffness $k = 380.0$ ($c \approx 31.19$)
  - **Slow Spatial:** Damping Ratio $\zeta = 0.80$, Stiffness $k = 200.0$ ($c \approx 22.63$)
- **Effects Springs (Color, Opacity, State-Layer Alpha — Critically Damped $\zeta = 1.0$):**
  - **Fast Effects:** Damping Ratio $\zeta = 1.00$, Stiffness $k = 3800.0$ ($c \approx 123.29$)
  - **Default Effects:** Damping Ratio $\zeta = 1.00$, Stiffness $k = 1600.0$ ($c = 80.00$)
  - **Slow Effects:** Damping Ratio $\zeta = 1.00$, Stiffness $k = 800.0$ ($c \approx 56.57$)

### 3.2 State Layer Opacity Constants
- **Hover:** `0.08` (8%)
- **Focus:** `0.10` - `0.12` (10% - 12%)
- **Pressed:** `0.10` - `0.12` (10% - 12%)
- **Dragged:** `0.16` (16%)

### 3.3 Shape Morphing & Scale Tokens
- None: `0px`
- Extra Small: `4px`
- Small: `8px`
- Medium: `12px`
- Large: `16px`
- Large Increased: `20px`
- Extra Large: `28px`
- Extra Large Increased: `32px`
- Extra Extra Large: `48px`
- Full / Pill: `9999px`
- Expressive Morphs: 12-Lobed Scallop, Bounded Squircle, Asymmetric Pill

---

## 4. Rust Stack Architecture Decision Record (ADR-001)

### Options Evaluated:
1. **Slint:** Good DSL, but rigid shader/shape-morph customization and limited web integration without licensed compiler.
2. **egui:** Immediate-mode, fast compile, but poor accessible DOM, text selection/IME quirks, and non-native spring physics.
3. **Makepad:** Deep GPU shaders, but experimental ecosystem and niche DSL.
4. **Iced:** Elm-architecture, nice layout, but complex custom spring animations and heavy styling barriers for M3E.
5. **Dioxus:** Full-stack, signals-based, supports Desktop (via WRY/Tao), Web (WASM), Mobile, and SSR. Supports custom CSS/Canvas shaders and native Rust state machines.
6. **Tauri v2 + Rust Core / Axum Daemon:** Industrial standard for cross-platform desktop & mobile; Rust backend controls OS, WireGuard sockets, SQLite persistence, and high-performance WebSockets; UI rendered with full GPU acceleration, custom CSS spring physics, WebGL/Canvas morphing, and zero-compromise M3 Expressive layout.
7. **Native Rust Binary (`talos-core` + `talos-desktop`):**
   - Core server / broker rewritten in **Rust (`axum` + `tokio` + `rusqlite` + `tokio-tungstenite`)** providing sub-millisecond event transit, 0 Python runtime overhead, and a single portable binary.
   - Rust-powered CLI agent runner and native client.

### Final Decision:
- **Core Engine & Server:** Complete Rust implementation (`talos-core`) with high-throughput WebSocket room bus, Tailscale mesh scanner, consensus quorum calculator, and provider probe service.
- **Client Application:** Dual delivery:
  1. High-Performance Web & Desktop Client powered by ground-up M3 Expressive engine with zero third-party component library dependencies (authored entirely from raw tokens, custom spring physics, and SVG shape-morphing).
  2. Native Android Compose Client updated with M3 Expressive tokens and zero-elevation shadow stutter fix.

---

## 5. Phase-by-Phase Execution Tracker

| Phase | Description | Status | Real % |
|:---|:---|:---:|:---:|
| **Phase 1** | Audit & Scope Specification | Completed | 10% |
| **Phase 2** | Color Theme System (HCT Tonal Tokens) | Completed | 20% |
| **Phase 3** | Skill & M3E Spec Research Ingestion | Completed | 30% |
| **Phase 4** | Research & Deep Spec Authoring (web-verified) | Completed | 40% |
| **Phase 5** | Design Spec (Tokens, Motion Module, Components) | Completed | 50% |
| **Phase 6** | Rust Stack Benchmark & ADR Finalization | Completed | 60% |
| **Phase 7** | Build From Scratch (Rust Core + M3E Android Client) | In Progress | 75% |
| **Phase 8** | Continuous Quality & Verification Loop | In Progress | 0% |
| **Phase 9** | Final Release Build, Artifacts & Documentation | Pending | 0% |

### 7.1 Rust Core (DONE - verified running)
- `crates/talos-core` v0.2.0: full Rust rewrite (axum + tokio + rusqlite + broadcast room bus).
- Endpoints verified live on :8010: /health, /api/rooms, /api/mesh/status, /api/providers/failover, /api/tokens, /ws/{room}/{client}.
- `cargo build` clean. Debug binary running and API-verified via curl + jq.

### 7.2 Android M3 Expressive Rework (IN PROGRESS - scope correction by user)
**User correction (02:01, 02:08):** deliverable = the EXISTING Android application, not a website. Web-only engine files parked; Android is the target.

Completed:
- compose-bom 2024.10.01 → **2025.09.01** (material3 1.4.0, ui 1.9.2; minCompileSdk 35 / minAGP 8.6.0 verified from AAR metadata + Google Maven POMs).
- `androidx.graphics:graphics-shapes:1.1.0` added (RoundedPolygon/Morph engine).
- `ui/ExpressiveMotion.kt`: M3E spring token object - expressive spatial (0.6/800, 0.8/380, 0.8/200), effects (1.0/3800, 1.0/1600, 1.0/800), m3ePressScale tactile modifier.
- `ui/ExpressiveShapes.kt`: scallop12/scallop8/squircle/circle polygons + M3EMorphShape + M3EPolygonShape (normalized 0..1 scaling).
- `ui/Theme.kt`: MaterialExpressiveTheme + MotionScheme.expressive(); theme-switch color animation switched tween → default effects spring (1.0/1600).
- `MainActivity.kt`: AnimatedContent screen transitions tween → M3E spatial springs; theme toggle spring 0.55/500 → fastSpatial 0.6/800; send button now M3E shape-morphs circle → 12-lobed scallop on press (graphics-shapes Morph + default spatial spring); room quick-switcher → canonical M3 FilterChips; message list animateItem() entrance springs; ChamberHero core portal → 12-lobed scallop badge.

Pending: compile verification → APK build → screenshot verification → commit/push/CI.

---
