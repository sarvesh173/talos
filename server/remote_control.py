"""Remote control queue: an agent posts a command, the phone executes it.

Deliberately tiny and file-backed. The point is that any agent that can reach
the host can drive the phone, without a second service to keep alive and
without inventing an auth story: the host already has one.

A command is a dict with `id`, `action` and `payload`. The phone polls, runs it,
and posts back `ok` plus whatever the action produced (text, or a screenshot
path served by /api/remote/artifact/{id}).
"""

from __future__ import annotations

import json
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional

QUEUE = Path.home() / ".talos" / "remote_queue.json"
ARTIFACTS = Path.home() / ".talos" / "remote_artifacts"

VALID_ACTIONS = {
    "tap", "type", "key", "swipe", "screenshot", "text",
    "open_app", "launch", "scroll", "wait",
}


def _load() -> Dict[str, Any]:
    if not QUEUE.exists():
        return {"pending": [], "done": {}, "artifacts": {}}
    try:
        return json.loads(QUEUE.read_text())
    except Exception:
        return {"pending": [], "done": {}, "artifacts": {}}


def _save(state: Dict[str, Any]) -> None:
    QUEUE.parent.mkdir(parents=True, exist_ok=True)
    # Keep the file from growing forever; results are for debugging, not history.
    state["done"] = dict(list(state.get("done", {}).items())[-80:])
    QUEUE.write_text(json.dumps(state))


def enqueue(device: str, action: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    if action not in VALID_ACTIONS:
        return {"ok": False, "error": f"unknown action {action!r}", "valid": sorted(VALID_ACTIONS)}
    state = _load()
    cmd = {
        "id": uuid.uuid4().hex[:12],
        "device": device,
        "action": action,
        "payload": payload,
        "queued_at": time.time(),
    }
    state["pending"].append(cmd)
    _save(state)
    return {"ok": True, "command": cmd}


def next_command(device: str) -> Optional[Dict[str, Any]]:
    state = _load()
    for i, cmd in enumerate(state["pending"]):
        if cmd["device"] == device:
            return cmd
    return None


def ack(command_id: str) -> None:
    state = _load()
    state["pending"] = [c for c in state["pending"] if c["id"] != command_id]
    _save(state)


def finish(command_id: str, ok: bool, output: str, screenshot_b64: Optional[str] = None) -> Dict[str, Any]:
    """Record what the phone did. A base64 screenshot is written to disk and
    served back, because an agent that cannot see the screen is guessing."""
    import base64

    state = _load()
    shot = None
    if screenshot_b64:
        ARTIFACTS.mkdir(parents=True, exist_ok=True)
        shot = f"{command_id}.png"
        try:
            (ARTIFACTS / shot).write_bytes(base64.b64decode(screenshot_b64))
        except Exception:
            shot = None
    state["pending"] = [c for c in state["pending"] if c["id"] != command_id]
    state["done"][command_id] = {
        "ok": ok,
        "output": (output or "")[:4000],
        "artifact": shot,
        "at": time.time(),
    }
    _save(state)
    return {"ok": True}


def result(command_id: str) -> Optional[Dict[str, Any]]:
    return _load().get("done", {}).get(command_id)


def status() -> Dict[str, Any]:
    state = _load()
    return {
        "pending": len(state.get("pending", [])),
        "commands": state.get("pending", [])[-10:],
    }