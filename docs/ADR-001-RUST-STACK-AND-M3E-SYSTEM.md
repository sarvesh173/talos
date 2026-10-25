# ADR-001: Rust-First Architecture and Ground-Up Material 3 Expressive (M3E) UI System

## Context & Problem Statement
The TALOS application requires a ground-up transformation into a production-grade, AI-Native Multi-Agent Collaboration Hub. The legacy application was ~1% complete with an over-complicated setup, stock Material palette swaps, and incomplete screens. The user has mandated:
1. **Rust-First Architecture:** Maximize Rust for core domain logic, peer networking, consensus protocols, persistence, and backend server routing.
2. **Ground-Up Material 3 Expressive (M3E) UI:** Re-author every container, button, state layer, and spring motion from scratch. No library re-skinning. Full adherence to M3E spacing, HCT tonal palettes, shape morphing (35+ shapes), and dual spring physics (Spatial vs Effects).
3. **Complete Screen Suite:** Implement every screen after Get Started: Deliberation Chamber, Consensus Quorum, Provider Health Matrix, Agent Bench Orchestrator, Security Token Vault, and M3E Motion Laboratory.

## Evaluated Options for UI & Core Stack

### Option A: Slint (Rust Native UI)
- *Pros:* Clean declarative DSL, native Rust compilation, lightweight memory footprint.
- *Cons:* Inflexible for custom 35-shape morphing and continuous SVG spline interpolation; limited text input/IME features on Linux Wayland; separate DSL compiler adds build fragility.

### Option B: egui (Immediate Mode Rust)
- *Pros:* 100% pure Rust, zero compile overhead, straightforward immediate-mode rendering.
- *Cons:* Lacks native accessibility tree (ARIA/AT-SPI); continuous redraw loop causes high CPU/battery consumption; immediate-mode layout makes fluid M3 responsive drawer and complex markdown message streams cumbersome.

### Option C: Iced / Makepad
- *Pros:* Modern GPU rendering and Elm-inspired architecture.
- *Cons:* Experimental ecosystem, lack of stable production tooling, missing robust text editing / IME controls.

### Option D: High-Performance Rust Core Engine (`talos-core`) + Ground-Up M3E Client Engine
- *Architecture:*
  1. **Core & Server (100% Rust):** High-throughput native Rust binary compiled with `tokio`, `axum`, `rusqlite`, `broadcast` channels, and `ring`/`sha2` cryptographic primitives. Handles real-time WebSocket room broadcasting, turn-taking locks, consensus quorum computation, Tailscale WireGuard mesh node discovery, and provider health probing with zero GC latency.
  2. **UI & Rendering Engine:** Ground-up, bespoke M3 Expressive design system authored from raw tokens, custom spring physics solver (closed-form differential equations for underdamped $\zeta < 1$ and critically damped $\zeta = 1$), SVG shape morphing paths, and accessible DOM tree.
  3. **Multi-Target Portability:** Runs standalone natively on Linux/macOS/Windows, in browser viewports, and packageable for Android WebView / Tauri v2 with 100% shared Rust contracts.

## Decision
We select **Option D: Native Rust Core (`talos-core`) + Bespoke M3 Expressive Client Architecture**.

### Justification:
- **Rust-First Core:** Replaces Python runtime bottlenecks for the event bus, consensus engine, and mesh discovery with a sub-millisecond compiled Rust binary.
- **Pure M3E Fidelity:** Building the UI from ground-up mathematical tokens guarantees full compliance with Google's May 2025/2026 M3 Expressive specifications:
  - Exact spring physics: Expressive fast spatial $(\zeta = 0.6, k = 800)$, default spatial $(\zeta = 0.8, k = 380)$, slow spatial $(\zeta = 0.8, k = 200)$, and critically damped effects springs $(\zeta = 1.0)$.
  - 10 shape levels ($0\text{px}$ to $48\text{px}$ and full pill) with expressive scallop and squircle morphs.
  - HCT tonal palettes for Light, Dark, and High Contrast.
- **No Palette Swaps:** Every button, container, card, modal, and drawer is custom-built with layered elevation, 8%/10%/12%/16% state layers, and spring-press tactile response.

## Consequences & Acceptance Criteria
- `talos-core` builds cleanly with `cargo build --release` with zero warnings.
- All 6 multi-agent core modules (Mesh, Rooms, Messages, Consensus, Providers, Auth) are implemented in Rust.
- All 7 complete screens are fully interactive and verified in real rendering.
- Zero TODOs, stubs, or fake placeholder data in core execution paths.
