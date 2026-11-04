"""Tests for the integration verifier: assert current wiring states.

These fail if someone later deletes the wiring (tool_ledger call, roster
route, hierarchy route, watchdog start, directive usage) or ships new
fabricated android data. The Bot icon test intentionally asserts DEAD
CODE so the suite flags the unwired bot package until it is displayed.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from server import verify_integration as V  # noqa: E402


@pytest.fixture(scope="module")
def result():
    return V.verify_all(ROOT)


def _details(result, label):
    return "\n".join(result["details"][label])


def test_tool_ledger_wired(result):
    assert "01 tool_ledger called from stream_tool_call" in result["wired"]
    d = _details(result, "01 tool_ledger called from stream_tool_call")
    assert "core/room_bus.py" in d and "ledger.start" in d and "ledger.finish" in d
    assert "def stream_tool_call" in d


def test_agents_roster_wired(result):
    assert "02 agents/roster -> discover_agent_sessions" in result["wired"]
    d = _details(result, "02 agents/roster -> discover_agent_sessions")
    assert "/api/agents/roster" in d and "discover_agent_sessions" in d


def test_cli_hierarchy_wired(result):
    assert "03 build_hierarchy on a route" in result["wired"]
    d = _details(result, "03 build_hierarchy on a route")
    assert "build_hierarchy" in d and "/api/delegation/hierarchy" in d


def test_watchdog_started_by_orchestrator(result):
    assert "04 delegate watchdog started by orchestrator" in result["wired"]
    d = _details(result, "04 delegate watchdog started by orchestrator")
    assert "DelegateWatchdog(" in d and "start_watchdog" in d


def test_talos_directive_used_by_chat_screen(result):
    assert "05 TalosDirective files used by a chat screen" in result["wired"]
    d = _details(result, "05 TalosDirective files used by a chat screen")
    assert "TalosDirective.kt" in d and "TalosDirectiveRenderers.kt" in d
    assert "TalosDirectiveParser.parse" in d or "TalosDirectiveHost(" in d


def test_bot_package_is_dead_code(result):
    # Current state: bot files exist but AgentBotIcon has no external user.
    assert "06 bot files exist + AgentBotIcon used" in result["dead_code"]
    d = _details(result, "06 bot files exist + AgentBotIcon used")
    assert "AgentBotIcon.kt exists=True" in d
    assert "external AgentBotIcon users=0" in d


def test_agent_roster_exists(result):
    assert "07 AgentRoster exists" in result["wired"]


def test_four_tests_exist_and_pass(result):
    assert "08 four tests exist and pass" in result["wired"]
    assert "73 passed" in _details(result, "08 four tests exist and pass")


def test_no_landing_fabricated_sessions(result):
    # The old LandingScreen fabricated 17+ AgentSession rows. Those must be
    # gone. ses_default in MainActivity is a benign placeholder default and
    # is the only session-id string allowed.
    bucket, entries = V.check09_fabricated(ROOT)
    text = "\n".join(entries)
    assert "LandingScreen" not in text
    assert "AgentSession(" not in text
    assert "remember-listOf" not in text
    assert "invented-turn-counter" not in text
    # At most the placeholder default may remain.
    allowed = [e for e in entries if "MainActivity.kt" in e and "ses_default" in e]
    assert entries == allowed


def test_fallback_engines_removed(result):
    # fallbackEngines must be absent; CliToolManager reads server data.
    assert "10 fallbackEngines in CliToolManager" in result["wired"]
    assert "fallbackEngines hits=0" in _details(result, "10 fallbackEngines in CliToolManager")


def test_verifier_exit_nonzero_while_dead_or_fake(result):
    # Contract: exit non-zero if anything is DEAD CODE or STILL FAKE.
    if result["dead_code"] or result["still_fake"]:
        assert result["ok"] is False


def test_verifier_cli_runs():
    proc = subprocess.run(
        [sys.executable, str(ROOT / "server" / "verify_integration.py"), "--root", str(ROOT)],
        capture_output=True,
        text=True,
        timeout=240,
    )
    assert "=== WIRED ===" in proc.stdout
    assert "=== DEAD CODE ===" in proc.stdout
    assert "=== STILL FAKE ===" in proc.stdout
    # Non-zero while Bot is dead (current state).
    assert proc.returncode != 0
