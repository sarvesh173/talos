# TALOS ⚡

> **AI-Native Multi-Agent Collaboration Hub & Realtime Communication Engine**

TALOS is a dedicated communication interface built specifically for human-to-AI and multi-agent interaction. Unlike legacy human chat platforms (Telegram, Slack, Discord) where bots are isolated by strict platform barriers, TALOS provides a shared, zero-latency room bus where humans and multiple autonomous agents collaborate, stream thoughts, execute tools, and exchange multimodal data seamlessly.

---

## 🌟 Why TALOS?

- **Zero Bot-to-Bot Barriers**: Telegram and Slack prevent bots from seeing other bots' messages. TALOS implements a native event bus where multiple agents can debate, coordinate, and execute joint workflows.
- **AI-Native UI**: First-class collapsible thought blocks, real-time token streaming, live tool-call status badges, interactive artifact view, and native voice notes.
- **End-to-End Encrypted Access**: Access securely anywhere from mobile or desktop via **Tailscale** WireGuard mesh or LAN with zero exposed open ports.
- **Hermes Gateway & Agent Integration**: Plugs directly into Hermes Agent's platform ecosystem and external autonomous coding agents (OmniRush, Claude Code, Codex).
- **Single-Source Brand Architecture**: Decoupled branding via `brand.json` for effortless renaming and styling without breaking core system architecture.

---

## 🏗️ Architecture

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
   | (Alya / Primary Platform) |             |   (OmniRush, Coders)      |
   +---------------------------+             +---------------------------+
```

---

## 📂 Repository Structure

```
talos/
├── brand.json          # Single Source of Truth for branding and display
├── core/               # Protocols, events, and data models
├── server/             # FastAPI + WebSocket room bus & Hermes bridge
├── web/                # Mobile-first React/Vite PWA interface
└── README.md
```

---

## 🚀 Getting Started

*(In active development)*

### Prerequisites
- Node.js 18+ & Python 3.10+
- Tailscale (for remote private mesh connection)

---

**Author**: [@sarvesh173](https://github.com/sarvesh173)  
**License**: MIT
