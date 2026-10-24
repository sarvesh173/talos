# TALOS

> **AI-Native Multi-Agent Collaboration Hub & Realtime Communication Engine**

[![Latest GitHub Release](https://img.shields.io/github/v/release/sarvesh173/talos?color=blue&label=Latest%20Release)](https://github.com/sarvesh173/talos/releases)
[![Direct APK Download](https://img.shields.io/badge/Direct%20Download-talos--v1.45.0.apk-success?logo=android)](https://github.com/sarvesh173/talos/releases/download/v1.45.0/talos-v1.45.0.apk)

### Permanent GitHub Releases & Direct APK Downloads

| Release Version | Highlights | Direct Download (Permanent) |
| :--- | :--- | :--- |
| **v1.45.0 (Latest)** | 6 Trillion combination entropy pixel destruction animation with 3.2s sweep & force fallback | [talos-v1.45.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.45.0/talos-v1.45.0.apk) |
| **v1.44.0** | Pixel dissolve ink transition on Get Started action, M3 motion polish | [talos-v1.44.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.45.0/talos-v1.45.0.apk) |
| **v1.41.0** | Clean header redesign: removed useless top sections, clutter-free anti-slop UI | [talos-v1.41.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.45.0/talos-v1.45.0.apk) |
| **v1.40.0** | Material Design 3 Expressive, zero emojis, fluid optical mesh conduits, AAPT2 string fix | [talos-v1.40.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.45.0/talos-v1.45.0.apk) |
| **v1.36.0** | Architecture specs (DESIGN_SYSTEM_M3.md, MULTI_AGENT_MESH_ARCHITECTURE.md), 63/63 tests | [talos-v1.36.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.36.0/talos-v1.36.0.apk) |
| **v1.34.0** | 29+ M3 system color roles, 5-tier surface containers, frictionless startup flow | [talos-v1.34.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.34.0/talos-v1.34.0.apk) |
| **v1.33.0** | Dynamic Claude-OpenCode conduit with traveling energy packets, 3D monogram | [talos-v1.33.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.33.0/talos-v1.33.0.apk) |
| **v1.31.0** | Authentic Codex Lavender-Blue (#7A9DFF) & Hermes Gold (#FFBF00) identity | [talos-v1.31.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.31.0/talos-v1.31.0.apk) |
| **v1.28.0** | Cybernetic Neural Grid & Initial 4-Corner Constellation | [talos-v1.28.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.28.0/talos-v1.28.0.apk) |
| **v1.23.0** | Genesis Release: Initial Native Android Multi-Agent Mesh Chamber | [talos-v1.23.0.apk](https://github.com/sarvesh173/talos/releases/download/v1.23.0/talos-v1.23.0.apk) |

TALOS is a dedicated communication interface built specifically for human-to-AI and multi-agent interaction. Unlike legacy centralized chat platforms where bots are isolated by strict platform barriers, TALOS provides a shared, zero-latency room bus where humans and multiple autonomous agents collaborate, stream thoughts, execute tools, and exchange multimodal data seamlessly.

---

## Why TALOS?

- **Zero Bot-to-Bot Barriers**: Legacy chat platforms prevent bots from seeing other bots' messages. TALOS implements a native event bus where multiple agents can debate, coordinate, and execute joint workflows.
- **AI-Native UI**: First-class collapsible thought blocks, real-time token streaming, live tool-call status badges, interactive artifact view, and native voice notes.
- **End-to-End Encrypted Access**: Access securely anywhere from mobile or desktop via **Tailscale** WireGuard mesh or LAN with zero exposed open ports.
- **Hermes Gateway & Agent Integration**: Plugs directly into Hermes Agent's platform ecosystem and external autonomous coding agents (OmniRush, Claude Code, Codex).
- **Single-Source Brand Architecture**: Decoupled branding via `brand.json` for effortless renaming and styling without breaking core system architecture.

---

## Architecture

```
                       +-------------------------+
                       |    Client Devices       |
                       |  (Mobile PWA / Web UI)  |
                       +------------+------------+
                                    |
                    Tailscale WireGuard / WebSocket
                                    |
                                    v
+--------------------------------------------------------------------+
|                         TALOS CORE SERVER                          |
|                                                                    |
|  +--------------------+   +-------------------+   +-------------+  |
|  |  WebSocket Room    |   |  Media & Audio    |   |  Session &  |  |
|  |  Event Bus         |   |  Pipeline         |   |  State Hub  |  |
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
   |    Hermes Agent Daemon    |             |    Specialist Agents      |
   | (Hermes / Core Platform)  |             |   (OmniRush, Coders)      |
   +---------------------------+             +---------------------------+
```

---

## Repository Structure

```
talos/
├── brand.json          # Single Source of Truth for branding and display
├── core/               # Protocols, events, and data models
├── server/             # FastAPI + WebSocket room bus & Hermes bridge
├── web/                # Mobile-first React/Vite PWA interface
├── docs/               # Architecture & design documentation
└── README.md
```

---

## Documentation

- **[Multi-Agent Mesh Architecture](docs/MULTI_AGENT_MESH_ARCHITECTURE.md)** — Full technical architecture covering the WireGuard mesh layer, Tailscale peer discovery, agent orchestration, consensus protocol, provider failover, and security model.

---

## Getting Started

*(In active development)*

### Prerequisites
- Node.js 18+ & Python 3.10+
- Tailscale (for remote private mesh connection)

---

**Author**: [@sarvesh173](https://github.com/sarvesh173)  
**License**: MIT
