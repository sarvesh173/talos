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

import json
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


#: Where operators drop their own engine manifests. One JSON file per engine.
MANIFEST_DIR = os.path.expanduser("~/.talos/agents")


class ManifestError(ValueError):
    """A manifest file exists but cannot be trusted. Named, never skipped."""


def _manifest_fields(raw: Dict, source: str) -> Dict:
    """Validate one manifest object into ``CliEngineSpec`` keyword arguments.

    A half-written manifest is a configuration mistake the operator must see,
    so every problem raises a message naming the file and the offending field.
    Silently dropping a manifest would make an agent look "not installed" when
    the real fault is a typo in its path.
    """
    if not isinstance(raw, dict):
        raise ManifestError(f"{source}: expected a JSON object, got {type(raw).__name__}")

    engine_id = str(raw.get("id") or "").strip()
    if not engine_id:
        raise ManifestError(f"{source}: 'id' is required (a short stable key)")

    binary = str(raw.get("binary") or "").strip()
    custom_binary_path = str(raw.get("path") or "").strip()
    if not binary and not custom_binary_path:
        raise ManifestError(f"{source}: needs 'binary' (looked up on PATH) or 'path' (absolute)")

    enabled = raw.get("enabled", True)
    if not isinstance(enabled, bool):
        raise ManifestError(f"{source}: 'enabled' must be true or false")

    fallback_paths = raw.get("fallback_paths") or []
    if not isinstance(fallback_paths, list) or not all(isinstance(p, str) for p in fallback_paths):
        raise ManifestError(f"{source}: 'fallback_paths' must be a list of strings")

    if custom_binary_path:
        fallback_paths = [custom_binary_path, *fallback_paths]

    return {
        "id": engine_id,
        "name": str(raw.get("name") or engine_id.replace("-", " ").title()),
        "binary": binary or os.path.basename(custom_binary_path),
        "command": str(raw.get("command") or binary or custom_binary_path),
        "description": str(raw.get("description") or f"Custom engine from {source}"),
        "fallback_paths": tuple(fallback_paths),
        "enabled": enabled,
    }


def load_custom_engines(manifest_dir: Optional[str] = None) -> List[CliEngineSpec]:
    """Read operator-supplied engine manifests.

    FEATURE.md §1 promises a custom manifest loader at ``~/.talos/agents/*.json``
    and the catalog only ever shipped the built-in tuple, so a user-registered
    agent was invisible. This reads that directory for real.

    A malformed file never aborts discovery: it is reported through
    :func:`manifest_errors` so the UI can show "your manifest is broken"
    instead of "your agent is missing".
    """
    directory = manifest_dir or MANIFEST_DIR
    if not os.path.isdir(directory):
        return []

    specs: List[CliEngineSpec] = []
    seen = {spec.id for spec in CLI_ENGINES}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(directory, name)
        try:
            with open(path, "r", encoding="utf-8") as handle:
                raw = json.load(handle)
            fields = _manifest_fields(raw, name)
        except (OSError, json.JSONDecodeError) as exc:
            raise ManifestError(f"{name}: {exc}") from exc

        if fields["id"] in seen:
            raise ManifestError(f"{name}: id '{fields['id']}' is already a built-in engine")
        if not fields["enabled"]:
            seen.add(fields["id"])
            continue
        seen.add(fields["id"])
        specs.append(CliEngineSpec(**{k: v for k, v in fields.items() if k != "enabled"}))
    return specs


def manifest_errors(manifest_dir: Optional[str] = None) -> List[str]:
    """Every manifest problem, as operator-readable strings. Empty when clean.

    Validates with the same rules ``load_custom_engines`` enforces, including
    duplicate ids — otherwise a manifest rejected at load time would report
    zero errors here and the UI could not explain why the agent vanished.
    """
    directory = manifest_dir or MANIFEST_DIR
    if not os.path.isdir(directory):
        return []
    errors: List[str] = []
    seen = {spec.id for spec in CLI_ENGINES}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        try:
            with open(os.path.join(directory, name), "r", encoding="utf-8") as handle:
                fields = _manifest_fields(json.load(handle), name)
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"{name}: {exc}")
            continue
        except ManifestError as exc:
            errors.append(str(exc))
            continue

        engine_id = fields["id"]
        if engine_id in seen:
            errors.append(f"{name}: id '{engine_id}' is already a built-in engine")
            continue
        seen.add(engine_id)
    return errors


@dataclass
class CliEngine:
    """A discovered engine plus the delegates running under it."""

    id: str
    name: str
    command_binary: str
    is_available: bool
    description: str = ""
    resolved_path: Optional[str] = None
    #: Bare binary name that was probed, e.g. "opencode". Distinct from
    #: [command_binary], which is how the agent is started ("opencode serve") and
    #: is not a valid argument to `which`.
    binary: str = ""
    delegates: List[Dict] = field(default_factory=list)

    def to_payload(self) -> Dict:
        return {
            "id": self.id,
            "name": self.name,
            "command_binary": self.command_binary,
            "binary": self.binary,
            "is_available": self.is_available,
            "description": self.description,
            "resolved_path": self.resolved_path,
            "delegates": self.delegates,
        }


def discover_engines(manifest_dir: Optional[str] = None) -> List[CliEngine]:
    """Return every known engine with its real availability.

    Built-in engines first, then operator manifests from
    ``~/.talos/agents/*.json``. A broken manifest does not abort discovery —
    it is surfaced through ``manifest_errors`` in :func:`build_hierarchy` so
    the operator sees a configuration fault instead of a vanished agent.
    """
    specs = list(CLI_ENGINES)
    try:
        specs.extend(load_custom_engines(manifest_dir))
    except ManifestError:
        # Reported separately; built-ins stay usable.
        pass

    engines: List[CliEngine] = []
    for spec in specs:
        path = _resolve(spec)
        engines.append(
            CliEngine(
                id=spec.id,
                name=spec.name,
                command_binary=spec.command,
                binary=spec.binary,
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
        "manifest_errors": manifest_errors(),
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


#: Where operators declare which engines TALOS can actually *drive*.
#: One JSON file per connected engine, named <id>.json. A file's presence is
#: the whole signal: installed means the binary exists, connected means TALOS
#: has a control path for it. The app renders only connected engines, so adding
#: a CLI is a file here — never an app release.
CONTROL_DIR = os.path.expanduser("~/.talos/control")


def connected_ids(control_dir: Optional[str] = None) -> set:
    """Engine ids TALOS holds a control manifest for.

    Unreadable directory is an empty set, not an error: a machine that has
    never connected an agent simply has no connected agents.
    """
    directory = control_dir or CONTROL_DIR
    if not os.path.isdir(directory):
        return set()
    found = set()
    for name in os.listdir(directory):
        if name.endswith(".json"):
            found.add(name[: -len(".json")])
    return found
