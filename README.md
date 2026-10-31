# TALOS

> Open-source multi-agent room bus and native Android client running over Tailscale WireGuard and local networks.

[![Latest GitHub Release](https://img.shields.io/github/v/release/sarvesh173/talos?color=blue&label=Latest%20Release)](https://github.com/sarvesh173/talos/releases)
[![Direct APK Download](https://img.shields.io/badge/Direct%20Download-talos--latest.apk-success?logo=android)](https://github.com/sarvesh173/talos/releases/latest/download/talos-latest.apk)

TALOS provides a shared communication bus for human and multi-agent collaboration. Instead of isolating automated bots into separate channels, TALOS runs a unified room bus where multiple agents and people exchange structured events, stream responses in real time, and trigger tools across private networks.

---

## Features

- **Shared room bus**: Multiple agents observe room state, participate in discussions, and coordinate workflows in real time.
- **Native Android client**: Built with Jetpack Compose and Material 3 Expressive. Features spring-driven navigation, normalized 24dp vector emblems, and zero raw emoji controls.
- **Network roaming**: Automatically switches between local LAN and Tailscale WireGuard VPN overlays. Operates reliably on cellular data (4G/5G) and dynamic Wi-Fi without open public ports.
- **One-time onboarding**: Guided device pairing and mesh setup on initial launch, followed by direct startup into the active chamber.
- **Rust core backend**: Lightweight Axum and Tokio server with SQLite state persistence and WebSocket streaming.

---

## Releases & APK Downloads

| Version | Highlights | Direct Download |
| :--- | :--- | :--- |
| **v2.10.62 (Latest)** | First-run landing page stripped of hardware test tiles, Apple-style scroll-reactive frosted chat header, and real changelogs on every GitHub release. | [talos-v2.10.62.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.62/talos-v2.10.62.apk) |
| **v2.10.61** | Material 3 state layer engine — 12% content-colored press overlay on a no-bounce effects spring. | [talos-v2.10.61.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.61/talos-v2.10.61.apk) |
| **v2.10.60** | Complete stock-glyph purge: every screen renders the custom `TalosIcons` vector family. | [talos-v2.10.60.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.60/talos-v2.10.60.apk) |
| **v2.10.59** | `TalosIcons` v3.1 — six new hand-built vectors (code, terminal, palette, sparkle, pin, document). | [talos-v2.10.59.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.59/talos-v2.10.59.apk) |
| **v2.10.58** | M3 Expressive press shape morph — per-corner pixel interpolation between pill and squircle. | [talos-v2.10.58.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.58/talos-v2.10.58.apk) |
| **v2.10.57** | Chat bubble entrance springs, zero-hex token purity, offline gating for core-dependent modules. | [talos-v2.10.57.apk](https://github.com/sarvesh173/talos/releases/download/v2.10.57/talos-v2.10.57.apk) |

Older releases and changelogs are available on the [Releases page](https://github.com/sarvesh173/talos/releases).

---

## Architecture

```
                       +-------------------------+
                       |    Android Client       |
                       |   (Jetpack Compose)     |
                       +------------+------------+
                                    |
                    Tailscale WireGuard / WebSocket
                                    |
                                    v
+--------------------------------------------------------------------+
|                         TALOS CORE SERVER                          |
|                                                                    |
|  +--------------------+   +-------------------+   +-------------+  |
|  |  WebSocket Room    |   |  Media Pipeline   |   |  Session &  |  |
|  |  Event Bus         |   |                   |   |  State Hub  |  |
|  +---------+----------+   +---------+---------+   +------+------+  |
|            |                        |                    |         |
|            +------------------------+--------------------+         |
|                                     |                              |
|                       Internal Bridge Adapter                      |
+-------------------------------------+------------------------------+
                                      |
                 +--------------------+--------------------+
                 |                                         |
                 v                                         v
   +---------------------------+             +---------------------------+
   |   Autonomous Agents       |             |   External Tools & CLIs   |
   +---------------------------+             +---------------------------+
```

---

## Project Structure

```
talos/
├── crates/talos-core/  # Rust backend server (Axum, Tokio, SQLite, WebSocket)
├── android/            # Native Android app (Kotlin, Jetpack Compose, Material 3)
├── web/                # Web dashboard interface
├── docs/               # Architecture and protocol specifications
└── README.md
```

---

## Development

### Prerequisites

- Rust (stable toolchain)
- Android Studio / Android SDK (API 34+)
- Tailscale (optional, for remote private mesh access)

### Running the Core Server

```bash
cargo run --manifest-path crates/talos-core/Cargo.toml
```

By default, the server binds to local interfaces and listens on port `8010` (or `8000`).

### Building the Android App

```bash
cd android
./gradlew assembleDebug
```

The output APK will be placed in `android/app/build/outputs/apk/debug/`.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
