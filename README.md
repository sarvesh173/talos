# TALOS

> Private multi-agent room bus and native Android client running over Tailscale WireGuard and local networks.

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
| **v2.7.4 (Latest)** | Normalized 24dp brand drawables, fixed vector parsing on agent grid, universal mobile Tailscale roaming, and one-time launch sequence. | [talos-latest.apk](https://github.com/sarvesh173/talos/releases/download/v2.7.4/talos-latest.apk) |
| **v2.7.3** | Universal cellular/Wi-Fi Tailscale discovery and persistent launch state. | [talos-v2.7.3.apk](https://github.com/sarvesh173/talos/releases/download/v2.7.3/talos-latest.apk) |
| **v2.7.2** | Official dashboard brand icons and M3 Expressive spatial spring transitions. | [talos-v2.7.2.apk](https://github.com/sarvesh173/talos/releases/download/v2.7.2/talos-latest.apk) |
| **v2.3.0** | Material 3 Expressive Purple Gradient theme and responsive mobile drawer. | [talos-v2.3.0.apk](https://github.com/sarvesh173/talos/releases/download/v2.3.0/talos-latest.apk) |
| **v2.0.0** | Liquid theme transitions, dark/light spring toggle, and initial room bus. | [talos-v2.0.0.apk](https://github.com/sarvesh173/talos/releases/download/v2.0.0/talos-v2.0.0.apk) |

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
