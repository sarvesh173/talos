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


def test_bot_package_is_wired(result):
    # The bot package started life as dead code: the files existed but nothing
    # outside ui/bot referenced them. AgentFleetScreen now renders AgentBotIcon
    # as the agent identity, so this must be WIRED. If it ever regresses to
    # dead code the verifier will move it back and this test will fail.
    assert "06 bot files exist + AgentBotIcon used" in result["wired"]
    d = _details(result, "06 bot files exist + AgentBotIcon used")
    assert "AgentBotIcon.kt exists=True" in d
    assert "external AgentBotIcon users=0" not in d


def test_agent_roster_exists(result):
    assert "07 AgentRoster exists" in result["wired"]


def test_four_tests_exist_and_pass(result):
    assert "08 four tests exist and pass" in result["wired"]
    assert "73 passed" in _details(result, "08 four tests exist and pass")


def test_no_landing_fabricated_sessions(result):
    # The old LandingScreen fabricated 17+ AgentSession rows. Those must be
    # gone, and so must the old "ses_default" placeholder — a hardcoded id that
    # does not exist on the host is exactly the class of fake this check exists
    # to catch. A session id may still be minted at runtime (UUID) or read from
    # a server payload; the checker allows those and nothing else.
    bucket, entries = V.check09_fabricated(ROOT)
    text = "\n".join(entries)
    assert "LandingScreen" not in text
    assert "AgentSession(" not in text
    assert "remember-listOf" not in text
    assert "invented-turn-counter" not in text
    assert "ses_default" not in text, "the hardcoded placeholder id came back"
    assert entries == [], f"fabricated session ids found: {entries}"


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
    # The contract is that the exit code tracks the findings, not a fixed value:
    # clean tree -> 0, anything dead or fake -> non-zero. Asserting a constant
    # here would mean the test documents today's bugs instead of the rule.
    has_problems = bool(_parse_bucket(proc.stdout, "DEAD CODE")) or bool(
        _parse_bucket(proc.stdout, "STILL FAKE")
    )
    assert (proc.returncode != 0) == has_problems, (
        f"exit={proc.returncode} disagrees with findings "
        f"(dead={len(_parse_bucket(proc.stdout, 'DEAD CODE'))}, "
        f"fake={len(_parse_bucket(proc.stdout, 'STILL FAKE'))})"
    )


def _parse_bucket(stdout: str, name: str) -> list[str]:
    """Findings listed under one `=== NAME ===` heading."""
    lines = stdout.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == f"=== {name} ===")
    except StopIteration:
        return []
    out = []
    for line in lines[start + 1:]:
        if line.startswith("==="):
            break
        if line.strip().startswith("- "):
            out.append(line.strip())
    return out
