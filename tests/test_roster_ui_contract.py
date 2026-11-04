"""Roster/UI fabrication contract tests.

The app once shipped a fabricated agent roster: six CLI tools with invented
session ids, titles like "Liquid Glass HIG audit", turn counts 20/11/22/62/182
and timestamps "Just now"/"3h ago". This file makes that class of bug fail CI
by (a) asserting the static auditor finds zero BLOCK findings in the real tree,
(b) proving the auditor itself catches synthetic violations of every class it
claims to detect, and (c) pinning the backend contract -- agent_sessions only
reports discovered engines, tool_ledger redacts credentials.
"""

import importlib.util
import re
import subprocess
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parent.parent
AUDITOR_PATH = REPO_ROOT / "tools" / "audit_fabrication.py"
sys.path.insert(0, str(REPO_ROOT))


def _load_auditor():
    spec = importlib.util.spec_from_file_location("audit_fabrication", AUDITOR_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules["audit_fabrication"] = module
    spec.loader.exec_module(module)
    return module


auditor = _load_auditor()

# ---------------------------------------------------------------------------
# (a) the real tree has no fabrication BLOCKs
# ---------------------------------------------------------------------------


def test_auditor_finds_no_block_findings_in_real_tree():
    findings = auditor.audit_tree(REPO_ROOT)
    blocks = [f for f in findings if f.severity == auditor.BLOCK]
    assert not blocks, "fabrication BLOCK findings in the real tree:\n" + "\n".join(
        f.render() for f in blocks
    )


def test_auditor_cli_exits_zero_on_real_tree():
    result = subprocess.run(
        [sys.executable, str(AUDITOR_PATH), "--root", str(REPO_ROOT)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr


# ---------------------------------------------------------------------------
# (b) the auditor actually catches each violation class (no silent pass)
# ---------------------------------------------------------------------------

SYNTHETIC_CASES = {
    "session-id-literal": (
        'val activeSessionId = "ses_abcdefghijklmnop"\n',
        auditor.BLOCK,
    ),
    "hardcoded-relative-time": (
        'Text("3h ago")\nText("Just now")\nText("12m ago")\n',
        auditor.BLOCK,
    ),
    "fabricated-roster": (
        'val roster = remember { listOf(\n'
        '    AgentSession(title = "Liquid Glass HIG audit", turns = 20),\n'
        '    AgentSession(title = "Mesh topology review", turns = 11),\n'
        '    AgentSession(title = "Token budget tuning", turns = 22),\n'
        ')\n',
        auditor.BLOCK,
    ),
    "fallback-engine-list": (
        'val fallbackEngines = listOf("opencode", "hermes")\n',
        auditor.BLOCK,
    ),
    "dangling-drawable": (
        'painterResource(R.drawable.ic_nope)\n',
        auditor.BLOCK,
    ),
}


@pytest.mark.parametrize("kind", list(SYNTHETIC_CASES))
def test_auditor_catches_synthetic_violation(kind):
    source, severity = SYNTHETIC_CASES[kind]
    findings = auditor.audit_source(source, f"synthetic/{kind}.kt", {"ic_real"})
    matching = [f for f in findings if f.kind == kind]
    assert matching, f"auditor did not flag synthetic {kind!r}: {source!r}"
    assert all(f.severity == severity for f in matching)
    assert all(f.line >= 1 for f in matching)


def test_auditor_ignores_comments_and_parsing():
    source = (
        '// fabricated "ses_abcdefghijklmnop" in a comment must not flag\n'
        '/* also a "3h ago" block comment */\n'
        'val real = dto.sessionId\n'
    )
    findings = auditor.audit_source(source, "synthetic/clean.kt", set())
    assert findings == []


def test_auditor_tree_catches_every_class_and_cli_exits_one(tmp_path):
    java_dir = tmp_path / "android" / "app" / "src" / "main" / "java" / "com" / "talos"
    res_dir = tmp_path / "android" / "app" / "src" / "main" / "res" / "drawable"
    java_dir.mkdir(parents=True)
    res_dir.mkdir(parents=True)
    (res_dir / "ic_real.xml").write_text("<vector/>")
    sources = "\n".join(src for src, _ in SYNTHETIC_CASES.values())
    (java_dir / "Fabricated.kt").write_text(sources)

    findings = auditor.audit_tree(tmp_path)
    kinds = {f.kind for f in findings if f.severity == auditor.BLOCK}
    assert kinds == set(SYNTHETIC_CASES), f"missing classes: {set(SYNTHETIC_CASES) - kinds}"

    result = subprocess.run(
        [sys.executable, str(AUDITOR_PATH), "--root", str(tmp_path)],
        capture_output=True, text=True,
    )
    assert result.returncode == 1


# ---------------------------------------------------------------------------
# (c) backend contract: only discovered engines, credentials redacted
# ---------------------------------------------------------------------------


def test_agent_sessions_returns_only_discovered_engines():
    from server import agent_sessions
    from server.cli_engines import discover_engines

    report = agent_sessions.discover_agent_sessions()
    discovered = {e.id for e in discover_engines()}
    reported = {agent["id"] for agent in report["agents"]}
    assert reported == discovered, (
        f"agent_sessions invented engines outside discovery: {reported - discovered}"
    )
    # No invented session ids anywhere in the report.
    fabricated = re.compile(r"ses_[A-Za-z0-9]{10,}|h_sess_|ses_e5")
    for agent in report["agents"]:
        assert not fabricated.search(agent["id"])
        for session in agent["sessions"]:
            assert "turns" not in session and "turn_count" not in session
            assert isinstance(session["updated_at"], (int, float))
            assert session["age_seconds"] >= 0


def test_tool_ledger_redacts_credentials():
    from server.tool_ledger import REDACTED, ToolLedger, redact

    fake_key = "sk-or-v1-" + "a1b2c3d4e5f60718"
    assert fake_key not in redact(f"api key {fake_key}")
    assert REDACTED in redact(f"api key {fake_key}")
    assert "ghp_" + "a" * 30 not in redact("ghp_" + "a" * 30)
    assert "9f8e7d6c5b4a39281706" not in redact("Authorization: Bearer 9f8e7d6c5b4a39281706")

    ledger = ToolLedger()
    record = ledger.start(
        room_id="room1", agent_id="a1", agent_name="Agent",
        tool="shell", arguments={"cmd": f"echo {fake_key}"},
    )
    payload = record.to_payload()
    assert fake_key not in str(payload)
    finished = ledger.finish(record, status="ok", error=f"failed with {fake_key}")
    assert fake_key not in finished.error
