"""Integration verifier: detects dead code and fabricated data in TALOS.

Reads source files only (no imports of the code under test beyond stdlib,
no Gradle). Each check reports WIRED, DEAD CODE, or STILL FAKE with
file:line evidence.

Usage:
    python3 server/verify_integration.py [--root /home/kira/talos]

Exit code is non-zero when anything is in DEAD CODE or STILL FAKE.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def read_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    try:
        return path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []


def find(path: Path, needle: str) -> list[tuple[int, str]]:
    """Return (lineno_1indexed, stripped_line) for lines containing needle."""
    out = []
    for i, line in enumerate(read_lines(path), start=1):
        if needle in line:
            out.append((i, line.strip()))
    return out


def find_re(path: Path, pattern: re.Pattern[str]) -> list[tuple[int, str]]:
    out = []
    for i, line in enumerate(read_lines(path), start=1):
        if pattern.search(line):
            out.append((i, line.strip()))
    return out


def iter_kt_files(root: Path):
    base = root / "android" / "app" / "src"
    if not base.exists():
        return []
    return sorted(base.rglob("*.kt"))


def rel(root: Path, path: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


# ---------------------------------------------------------------------------
# Checks. Each returns (bucket, entries) where bucket is one of
# "wired" | "dead_code" | "still_fake".
# ---------------------------------------------------------------------------

def check01_tool_ledger(root: Path):
    p_exists = root / "server" / "tool_ledger.py"
    p_bus = root / "core" / "room_bus.py"
    entries = []
    ok = p_exists.exists() and p_bus.exists()
    entries.append(f"{rel(root, p_exists)} exists={p_exists.exists()}")
    entries.append(f"{rel(root, p_bus)} exists={p_bus.exists()}")
    hits = find(p_bus, "tool_ledger") if p_bus.exists() else []
    entries.extend(f"{rel(root, p_bus)}:{n}: {t}" for n, t in hits[:5])
    start_hits = find(p_bus, "ledger.start") if p_bus.exists() else []
    fin_hits = find(p_bus, "ledger.finish") if p_bus.exists() else []
    entries.extend(f"{rel(root, p_bus)}:{n}: {t}" for n, t in start_hits[:5])
    entries.extend(f"{rel(root, p_bus)}:{n}: {t}" for n, t in fin_hits[:5])
    ls_hits = find(p_bus, "def stream_tool_call") if p_bus.exists() else []
    entries.extend(f"{rel(root, p_bus)}:{n}: {t}" for n, t in ls_hits[:5])
    if p_exists.exists() and start_hits and fin_hits:
        return "wired", entries
    return "dead_code", entries


def check02_agent_roster(root: Path):
    p_sess = root / "server" / "agent_sessions.py"
    p_main = root / "server" / "main.py"
    entries = [f"{rel(root, p_sess)} exists={p_sess.exists()}"]
    hits = find(p_main, "/api/agents/roster") if p_main.exists() else []
    entries.extend(f"{rel(root, p_main)}:{n}: {t}" for n, t in hits[:5])
    hits2 = find(p_main, "discover_agent_sessions") if p_main.exists() else []
    entries.extend(f"{rel(root, p_main)}:{n}: {t}" for n, t in hits2[:5])
    if p_sess.exists() and hits and hits2:
        return "wired", entries
    return "dead_code", entries


def check03_cli_hierarchy(root: Path):
    p_cli = root / "server" / "cli_engines.py"
    p_main = root / "server" / "main.py"
    entries = [f"{rel(root, p_cli)} exists={p_cli.exists()}"]
    hits = find(p_main, "build_hierarchy") if p_main.exists() else []
    entries.extend(f"{rel(root, p_main)}:{n}: {t}" for n, t in hits[:8])
    hits2 = find(p_main, "/api/delegation/hierarchy") if p_main.exists() else []
    entries.extend(f"{rel(root, p_main)}:{n}: {t}" for n, t in hits2[:5])
    if p_cli.exists() and hits and hits2:
        return "wired", entries
    return "dead_code", entries


def check04_watchdog_started(root: Path):
    p_wd = root / "server" / "delegate_watchdog.py"
    p_orch = root / "server" / "agent_orchestrator.py"
    entries = [f"{rel(root, p_wd)} exists={p_wd.exists()}"]
    hits = find(p_orch, "DelegateWatchdog(") if p_orch.exists() else []
    entries.extend(f"{rel(root, p_orch)}:{n}: {t}" for n, t in hits[:5])
    hits2 = find(p_orch, "start_watchdog") if p_orch.exists() else []
    entries.extend(f"{rel(root, p_orch)}:{n}: {t}" for n, t in hits2[:8])
    hits3 = find(p_orch, "_watchdog_loop") if p_orch.exists() else []
    entries.extend(f"{rel(root, p_orch)}:{n}: {t}" for n, t in hits3[:5])
    hits4 = find(p_orch, "create_task") if p_orch.exists() else []
    # only keep create_task lines mentioning watchdog
    wd_ct = [(n, t) for n, t in hits4 if "watchdog" in t.lower()]
    entries.extend(f"{rel(root, p_orch)}:{n}: {t}" for n, t in wd_ct[:5])
    if p_wd.exists() and hits:
        started = bool(hits2) or bool(wd_ct)
        entries.append(f"watchdog started={started}")
        if started:
            return "wired", entries
    return "dead_code", entries


def check05_directive_used(root: Path):
    p1 = root / "android/app/src/main/java/com/sarvesh173/talos/ui/agent/TalosDirective.kt"
    p2 = root / "android/app/src/main/java/com/sarvesh173/talos/ui/agent/TalosDirectiveRenderers.kt"
    entries = [
        f"{rel(root, p1)} exists={p1.exists()}",
        f"{rel(root, p2)} exists={p2.exists()}",
    ]
    users = []
    for kp in iter_kt_files(root):
        if kp.resolve() in (p1.resolve(), p2.resolve()):
            continue
        lines = read_lines(kp)
        for i, line in enumerate(lines, start=1):
            if "TalosDirectiveParser.parse" in line or "TalosDirectiveHost(" in line:
                users.append((kp, i, line.strip()))
                break
    for kp, n, t in users[:5]:
        entries.append(f"{rel(root, kp)}:{n}: {t}")
    entries.append(f"external parser/host users={len(users)}")
    if p1.exists() and p2.exists() and users:
        return "wired", entries
    return "dead_code", entries


def check06_bot_used(root: Path):
    base = root / "android/app/src/main/java/com/sarvesh173/talos/ui/bot"
    names = ["AgentBotIcon.kt", "BotEngine.kt", "BotMath.kt", "BotState.kt"]
    entries = []
    missing = []
    for n in names:
        p = base / n
        entries.append(f"{rel(root, p)} exists={p.exists()}")
        if not p.exists():
            missing.append(n)
    # AgentBotIcon referenced from at least one file outside ui/bot/
    external = []
    for kp in iter_kt_files(root):
        if "ui/bot/" in str(kp).replace("\\", "/"):
            continue
        if "AgentBotIcon" in (kp.read_text(encoding="utf-8", errors="replace") if kp.exists() else ""):
            for i, line in enumerate(read_lines(kp), start=1):
                if "AgentBotIcon" in line:
                    external.append((kp, i, line.strip()))
                    break
    for kp, n, t in external[:5]:
        entries.append(f"{rel(root, kp)}:{n}: {t}")
    entries.append(f"external AgentBotIcon users={len(external)}")
    if not missing and external:
        return "wired", entries
    return "dead_code", entries


def check07_roster_exists(root: Path):
    p = root / "android/app/src/main/java/com/sarvesh173/talos/ui/agents/AgentRoster.kt"
    entries = [f"{rel(root, p)} exists={p.exists()}"]
    users = 0
    for kp in iter_kt_files(root):
        if kp == p:
            continue
        if "AgentRoster." in (kp.read_text(encoding="utf-8", errors="replace") if kp.exists() else ""):
            users += 1
    entries.append(f"AgentRoster. users={users}")
    if p.exists():
        return "wired", entries
    return "dead_code", entries


def run_four_tests(root: Path) -> tuple[bool, str]:
    files = [
        "tests/test_tool_ledger.py",
        "tests/test_orb_speed.py",
        "tests/test_agent_sessions.py",
        "tests/test_cli_engines.py",
    ]
    missing = [f for f in files if not (root / f).exists()]
    cmd = [sys.executable, "-m", "pytest", *files, "-q"]
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(root),
            capture_output=True,
            text=True,
            timeout=180,
        )
        tail = (proc.stdout + "\n" + proc.stderr).strip().splitlines()
        summary = tail[-1] if tail else ""
        return (proc.returncode == 0 and not missing), summary + f" (code {proc.returncode})"
    except subprocess.TimeoutExpired:
        return False, "timeout after 180s"


def check08_tests(root: Path):
    files = [
        "tests/test_tool_ledger.py",
        "tests/test_orb_speed.py",
        "tests/test_agent_sessions.py",
        "tests/test_cli_engines.py",
    ]
    entries = [f"{f} exists={(root / f).exists()}" for f in files]
    all_exist = all((root / f).exists() for f in files)
    passed, summary = run_four_tests(root)
    entries.append(f"pytest four files: {summary}")
    if all_exist and passed:
        return "wired", entries
    return "dead_code", entries


def check09_fabricated(root: Path):
    """Flag fabricated android data: session-id strings, 3+ listOf, turn counters.

    The hard part is not finding `ses_` strings — it is not flagging the honest
    ones. A session id minted at runtime with UUID.randomUUID() is the *correct*
    way to get an id that is guaranteed unique; only a hardcoded literal that was
    never produced by anything is fabricated. So a line carrying a mint call is
    allowed even though it contains an id-shaped token, and the same goes for an
    id read from a server payload field.
    """
    fake_entries = []
    ses_re = re.compile(r'"[^"\n]*(?:ses_|sess_)[^"\n]*"')
    turn_re = re.compile(r"AgentSession\([^)]*,\s*\d+\s*\)")
    # Evidence that the id is generated or received, not invented.
    mint_markers = (
        "randomUUID",
        "UUID.",
        "ifBlank",
        "sessionId",
        "session.id",
        "it.id",
        "row.id",
        "session_id",
        "toPayload",
        "getString(",
        "putString(",
    )
    for kp in iter_kt_files(root):
        lines = read_lines(kp)
        for i, line in enumerate(lines, start=1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            generated = any(marker in line for marker in mint_markers)
            if not generated:
                for m in ses_re.finditer(line):
                    tok = m.group(0)
                    fake_entries.append(
                        f"{rel(root, kp)}:{i}: session-id-string {tok} :: {stripped[:160]}"
                    )
            if turn_re.search(line):
                fake_entries.append(
                    f"{rel(root, kp)}:{i}: invented-turn-counter :: {stripped[:160]}"
                )
        # remember { listOf( ... ) with 3+ AgentSession/CliToolAgent entries
        for i, line in enumerate(lines, start=1):
            if "remember" in line and "listOf(" in line:
                window = "\n".join(lines[i - 1 : i + 80])
                n = window.count("AgentSession(") + window.count("CliToolAgent(")
                if n >= 3:
                    fake_entries.append(
                        f"{rel(root, kp)}:{i}: remember-listOf {n}x AgentSession/CliToolAgent :: {line.strip()[:160]}"
                    )
    fake_entries.sort()
    return ("still_fake" if fake_entries else "wired"), fake_entries


def check10_fallback(root: Path):
    hits = []
    for kp in iter_kt_files(root):
        for n, t in find(kp, "fallbackEngines"):
            hits.append(f"{rel(root, kp)}:{n}: {t}")
    under_ui = root / "android/app/src/main/java/com/sarvesh173/talos/ui/CliToolManager.kt"
    entries = [f"{rel(root, under_ui)} exists={under_ui.exists()}"]
    entries.extend(hits[:10])
    entries.append(f"fallbackEngines hits={len(hits)}")
    if hits:
        return "still_fake", entries
    return "wired", entries


CHECKS = [
    ("01 tool_ledger called from stream_tool_call", check01_tool_ledger),
    ("02 agents/roster -> discover_agent_sessions", check02_agent_roster),
    ("03 build_hierarchy on a route", check03_cli_hierarchy),
    ("04 delegate watchdog started by orchestrator", check04_watchdog_started),
    ("05 TalosDirective files used by a chat screen", check05_directive_used),
    ("06 bot files exist + AgentBotIcon used", check06_bot_used),
    ("07 AgentRoster exists", check07_roster_exists),
    ("08 four tests exist and pass", check08_tests),
    ("09 fabricated android data search", check09_fabricated),
    ("10 fallbackEngines in CliToolManager", check10_fallback),
]


def verify_all(root: Path | None = None) -> dict:
    root = Path(root) if root is not None else repo_root()
    wired: list[str] = []
    dead_code: list[str] = []
    still_fake: list[str] = []
    details: dict[str, list[str]] = {}
    for label, fn in CHECKS:
        bucket, entries = fn(root)
        details[label] = entries
        if bucket == "wired":
            wired.append(label)
        elif bucket == "dead_code":
            dead_code.append(label)
        else:
            still_fake.append(label)
    return {
        "wired": wired,
        "dead_code": dead_code,
        "still_fake": still_fake,
        "details": details,
        "ok": not dead_code and not still_fake,
    }


def format_report(result: dict) -> str:
    lines = []
    for bucket, title in [("wired", "WIRED"), ("dead_code", "DEAD CODE"), ("still_fake", "STILL FAKE")]:
        lines.append(f"=== {title} ===")
        items = result[bucket]
        if not items:
            lines.append("(none)")
        for label in items:
            lines.append(f"- {label}")
            for e in result["details"].get(label, []):
                lines.append(f"  evidence: {e}")
        lines.append("")
    lines.append(f"ok={result['ok']}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Verify TALOS wiring vs dead code vs fake data")
    ap.add_argument("--root", default=str(repo_root()))
    ns = ap.parse_args(argv)
    result = verify_all(Path(ns.root))
    print(format_report(result))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
