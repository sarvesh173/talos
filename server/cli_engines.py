"""CLI engine discovery and delegation hierarchy.

The Android screen used to render two hardcoded engines with fabricated
delegates and fake child agents — "Rust Core Maintainer", "Tokio Runtime
Watcher", a 24/50 turn counter that never moved. That is worse than an empty
list: it looks like telemetry.

This module answers the only honest question the screen can ask: *which CLI
agents are installed on this machine right now, and which delegates are
actually running under them?* Everything else is derived from that.

Discovery is filesystem + ``shutil.which`` only. No network, no guessed
binaries: if ``opencode`` is not on PATH, the engine is reported unavailable
with the reason instead of being invented.
"""

from __future__ import annotations

import os
import shutil
from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass(frozen=True)
class CliEngineSpec:
    """One CLI agent that TALOS can delegate to."""

    id: str
    name: str
    #: Binary looked up on PATH.
    binary: str
    #: How the agent is normally started, shown in the UI.
    command: str
    description: str
    #: Extra paths to check when the binary is not on PATH (npm/pip installs).
    fallback_paths: tuple = ()
    #: Environment variable holding the model list, if the CLI exposes one.
    models_env: Optional[str] = None


#: Every engine the app knows how to drive. Ordered by how much the operator
#: usually wants them: local Hermes first, then coding agents.
CLI_ENGINES: tuple = (
    CliEngineSpec(
        id="hermes",
        name="Hermes Agent",
        binary="hermes",
        command="hermes",
        description="Primary agent runtime with tools, memory and skills.",
        fallback_paths=("~/.local/bin/hermes",),
    ),
    CliEngineSpec(
        id="opencode",
        name="OpenCode",
        binary="opencode",
        command="opencode serve",
        description="Open-source terminal coding agent. Drives the local FreeBridge.",
        fallback_paths=("~/.opencode/bin/opencode", "~/.local/bin/opencode"),
    ),
    CliEngineSpec(
        id="omnirush",
        name="OmniRush",
        binary="omnirush",
        command="omnirush",
        description="Sandboxed multi-model runner for long autonomous tasks.",
        fallback_paths=("~/.local/bin/omnirush",),
    ),
    CliEngineSpec(
        id="claude-code",
        name="Claude Code",
        binary="claude",
        command="claude",
        description="Anthropic terminal agent, plan-locked to the Claude CLI route.",
        fallback_paths=("~/.local/bin/claude", "~/.claude/local/claude"),
    ),
    CliEngineSpec(
        id="codex",
        name="Codex CLI",
        binary="codex",
        command="codex",
        description="OpenAI Codex terminal agent for PR-sized changes.",
        fallback_paths=("~/.local/bin/codex",),
    ),
    CliEngineSpec(
        id="antigravity",
        name="Antigravity",
        binary="agy",
        command="agy",
        description="Plugin-driven autonomous engineering agent.",
        fallback_paths=("~/.local/bin/agy",),
    ),
    CliEngineSpec(
        id="zcode",
        name="ZCode",
        binary="zcode",
        command="zcode",
        description="GLM-backed CLI agent.",
        fallback_paths=("~/.local/bin/zcode",),
    ),
)


def _resolve(engine: CliEngineSpec) -> Optional[str]:
    """Absolute path of the engine binary, or ``None`` when not installed."""
    found = shutil.which(engine.binary)
    if found:
        return found
    for raw in engine.fallback_paths:
        path = os.path.expanduser(raw)
        if os.path.isfile(path) and os.access(path, os.X_OK):
            return path
    return None


@dataclass
class CliEngine:
    """A discovered engine plus the delegates running under it."""

    id: str
    name: str
    command_binary: str
    is_available: bool
    description: str = ""
    resolved_path: Optional[str] = None
    delegates: List[Dict] = field(default_factory=list)

    def to_payload(self) -> Dict:
        return {
            "id": self.id,
            "name": self.name,
            "command_binary": self.command_binary,
            "is_available": self.is_available,
            "description": self.description,
            "resolved_path": self.resolved_path,
            "delegates": self.delegates,
        }


def discover_engines() -> List[CliEngine]:
    """Return every known engine with its real availability."""
    engines: List[CliEngine] = []
    for spec in CLI_ENGINES:
        path = _resolve(spec)
        engines.append(
            CliEngine(
                id=spec.id,
                name=spec.name,
                command_binary=spec.command,
                is_available=path is not None,
                description=spec.description,
                resolved_path=path,
            )
        )
    return engines


def delegate_owner_engine(delegate: Dict) -> str:
    """Engine a registered delegate should be grouped under.

    Delegates carry an optional ``engine`` field. When it is missing we infer
    from the model prefix, because a delegate running ``deepseek/*`` is being
    driven by the local Hermes runtime, not by OpenCode.
    """
    explicit = (delegate.get("engine") or "").strip().lower()
    if explicit:
        return explicit
    model = (delegate.get("model") or "").strip().lower()
    if model.startswith("opencode") or "free" in model:
        return "opencode"
    if model.startswith("claude"):
        return "claude-code"
    if model.startswith("gpt-5") or model.startswith("codex"):
        return "codex"
    return "hermes"


def build_hierarchy(delegates: List[Dict]) -> Dict:
    """Assemble the engine → delegate → child tree the Android screen renders.

    ``delegates`` is the live registry from
    :meth:`AgentOrchestrator.list_delegates`. Nothing is synthesised: an engine
    with no delegates renders empty, and an engine whose binary is missing is
    marked unavailable rather than hidden.
    """
    engines = discover_engines()
    by_id: Dict[str, CliEngine] = {engine.id: engine for engine in engines}

    for delegate in delegates:
        owner = delegate_owner_engine(delegate)
        engine = by_id.get(owner)
        if engine is None:
            # A delegate may reference an engine that is no longer in the
            # catalog (renamed binary, removed tool). Surface it as unknown
            # instead of dropping the work silently.
            engine = CliEngine(
                id=owner or "unknown",
                name=(owner or "Unknown").replace("-", " ").title(),
                command_binary=owner or "unknown",
                is_available=False,
                description="Referenced by a running delegate but not installed.",
            )
            by_id[engine.id] = engine
        engine.delegates.append(_delegate_payload(delegate))

    payload_engines = [engine.to_payload() for engine in by_id.values()]
    total_delegates = sum(len(engine.delegates) for engine in by_id.values())
    total_children = sum(
        len(child_list)
        for engine in by_id.values()
        for child_list in (d.get("children", []) for d in engine.delegates)
    )
    return {
        "total_engines": len(payload_engines),
        "total_available_engines": sum(1 for e in payload_engines if e["is_available"]),
        "total_delegates": total_delegates,
        "total_active_children": total_children,
        "engines": payload_engines,
    }


def _delegate_payload(delegate: Dict) -> Dict:
    """Normalise one registry entry for the UI contract."""
    children = []
    for child in delegate.get("children", []) or []:
        children.append(
            {
                "id": child.get("id", ""),
                "name": child.get("name", ""),
                "parent_delegate_id": delegate.get("id", ""),
                "task_goal": child.get("task", "") or child.get("task_goal", ""),
                "state": child.get("status", "unknown"),
                "turns_used": int(child.get("turns_used", 0) or 0),
                "created_at": child.get("started_at", 0.0) or 0.0,
            }
        )
    return {
        "id": delegate.get("id", ""),
        "name": delegate.get("name", ""),
        "engine_id": delegate_owner_engine(delegate),
        "assigned_model": delegate.get("model", ""),
        "supported_models": delegate.get("supported_models", []) or [],
        "reasoning_effort": (delegate.get("reasoning_effort", "") or "medium").title(),
        "command_turn_limit": int(delegate.get("command_limit", 0) or 0),
        "turns_consumed": int(delegate.get("turns_used", 0) or 0),
        "is_paused": delegate.get("status") == "paused",
        "status": delegate.get("status", "running"),
        "progress": delegate.get("progress", 0.0),
        "tokens_generated": int(delegate.get("tokens_generated", 0) or 0),
        "elapsed_seconds": int(delegate.get("elapsed_seconds", 0) or 0),
        "remaining_seconds": int(delegate.get("remaining_seconds", 0) or 0),
        "children": children,
    }
