#!/usr/bin/env python3
"""Static fabrication auditor for the TALOS Android tree.

The app once shipped a roster built from invented data: six CLI tools with
fabricated session ids, titles like "Liquid Glass HIG audit", turn counts
20/11/22/62/182 and hardcoded timestamps "Just now"/"3h ago". Nothing in the
test suite could distinguish a real number from an invented one, so this
scanner exists to make that class of bug fail loudly.

It scans ``android/app/src/main/java`` for five violation classes:

1. Session-id-shaped string literals (``ses_...``, ``h_sess_``, ``ses_e5`` ...)
   that are NOT inside a comment and NOT produced by parsing.
2. Hardcoded relative-time literals ("Just now", "3m ago", "3h ago") inside
   Kotlin string literals -- real ages are computed, never written down.
3. ``remember { listOf(`` blocks with 3+ entries carrying a title-like string
   and a numeric "turn" field -- the exact shape of the fabricated roster.
4. ``val fallbackEngines`` / ``fallbackAgents``-style hardcoded engine/agent
   lists in ``ui/`` -- the fabricated CLI hierarchy fallback.
5. ``R.drawable.*`` references missing from ``res/drawable`` (the class of bug
   where the emblem list names an icon that was never added).

Usage:
    python3 tools/audit_fabrication.py [--root REPO_ROOT] [--json]

Exit code is 1 when any BLOCK finding remains, 0 otherwise.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

BLOCK = "BLOCK"
WARN = "WARN"

# --- check 1 patterns ---------------------------------------------------
SES_LONG = re.compile(r"ses_[A-Za-z0-9]{10,}")
SES_HSESS_LITERAL = re.compile(r'"[^"\n]*h_sess_')
SES_E5_LITERAL = re.compile(r'"[^"\n]*ses_e5[A-Za-z0-9_]*"')

# A match on a line that parses/derives a session id is not a fabricated
# literal; it is what a real id looks like on its way through the app.
PARSING_HINTS = re.compile(
    r"parse|decode|fromJson|toDto|removePrefix|substring|take\(|split\(|\.id\b|\.sessionId|session_id"
)

# --- check 2 pattern ----------------------------------------------------
RELATIVE_TIME = re.compile(r'"(Just now|\d+\s*[mhd]\s+ago)"')

# --- check 3 patterns ---------------------------------------------------
REMEMBER_LISTOF = re.compile(r"remember\s*\{\s*listOf\s*\(")
TURN_FIELD = re.compile(r"\bturn\w*\s*[:=]\s*\d+")
TITLE_FIELD = re.compile(r"\b(?:title|name)\s*[:=]\s*\"[^\"]{3,}\"")
QUOTED_STRING = re.compile(r'"[^"\n]{3,}"')

# --- check 4 pattern ----------------------------------------------------
FALLBACK_LIST = re.compile(r"\b(?:val|var)\s+fallback(Engines|Agents)\b")

# --- check 5 pattern ----------------------------------------------------
DRAWABLE_REF = re.compile(r"R\.drawable\.([A-Za-z0-9_]+)")

# Window (chars) scanned after a `remember { listOf(` for check 3.
ROSTER_WINDOW = 1500


@dataclass
class Finding:
    path: str
    line: int
    severity: str
    kind: str
    message: str
    code: str = ""

    def to_dict(self) -> dict:
        return {
            "path": self.path,
            "line": self.line,
            "severity": self.severity,
            "kind": self.kind,
            "message": self.message,
            "code": self.code,
        }

    def render(self) -> str:
        rel = self.path
        out = f"{rel}:{self.line}: {self.severity} [{self.kind}] {self.message}"
        if self.code:
            out += f"\n    {self.code}"
        return out


def _strip_comments(text: str) -> str:
    """Blank out // and /* */ comments, preserving newlines (and columns for
    line comments) so line numbers in findings stay accurate."""
    out: list[str] = []
    i, n = 0, len(text)
    in_block = False
    while i < n:
        if in_block:
            if text.startswith("*/", i):
                in_block = False
                i += 2
            else:
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
        elif text.startswith("/*", i):
            in_block = True
            i += 2
        elif text.startswith("//", i):
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def _code_line(raw: str, code_text: str, lineno: int) -> str:
    """Best-effort raw line for display (code-only is safer to match against,
    raw is nicer to read); prefer raw when it exists."""
    lines = raw.splitlines()
    lines_code = code_text.splitlines()
    if lineno - 1 < len(lines):
        return lines[lineno - 1].strip()
    if lineno - 1 < len(lines_code):
        return lines_code[lineno - 1].strip()
    return ""


def audit_source(text: str, filename: str, drawable_names: set | None = None) -> list[Finding]:
    """Audit one Kotlin source string. Pure function -- tests feed synthetic
    sources here directly. ``drawable_names`` enables check 5 when supplied."""
    findings: list[Finding] = []
    code = _strip_comments(text)
    code_lines = code.splitlines()
    raw_lines = text.splitlines()

    def line_no(offset: int) -> int:
        return code.count("\n", 0, offset) + 1

    def show(lineno: int) -> str:
        return raw_lines[lineno - 1].strip() if 1 <= lineno <= len(raw_lines) else ""

    # -- checks 1, 2, 4, 5: line-local ----------------------------------
    for idx, cline in enumerate(code_lines):
        lineno = idx + 1
        disp = show(lineno)

        # 1. session-id-shaped literals
        if not PARSING_HINTS.search(cline):
            m = SES_LONG.search(cline)
            if m:
                findings.append(Finding(
                    filename, lineno, BLOCK, "session-id-literal",
                    f"session-id-shaped literal {m.group(0)!r} is hardcoded, not parsed",
                    disp))
            elif SES_HSESS_LITERAL.search(cline):
                findings.append(Finding(
                    filename, lineno, BLOCK, "session-id-literal",
                    "session-id-shaped literal 'h_sess_...' is hardcoded, not parsed",
                    disp))
            elif SES_E5_LITERAL.search(cline):
                findings.append(Finding(
                    filename, lineno, BLOCK, "session-id-literal",
                    "session-id-shaped literal 'ses_e5...' is hardcoded, not parsed",
                    disp))

        # 2. hardcoded relative-time literals
        m = RELATIVE_TIME.search(cline)
        if m:
            findings.append(Finding(
                filename, lineno, BLOCK, "hardcoded-relative-time",
                f"relative-time literal {m.group(1)!r} is hardcoded; a real age is computed",
                disp))

        # 4. hardcoded fallback engine/agent lists
        m = FALLBACK_LIST.search(cline)
        if m:
            findings.append(Finding(
                filename, lineno, BLOCK, "fallback-engine-list",
                f"hardcoded fallback{m.group(1)} list -- engines must come from discovery",
                disp))

        # 5. dangling drawable references
        if drawable_names is not None:
            for m in DRAWABLE_REF.finditer(cline):
                name = m.group(1)
                if name not in drawable_names:
                    findings.append(Finding(
                        filename, lineno, BLOCK, "dangling-drawable",
                        f"R.drawable.{name} does not exist in res/drawable",
                        disp))

    # -- check 3: remember { listOf( roster blocks -----------------------
    for m in REMEMBER_LISTOF.finditer(code):
        start = m.start()
        window = code[start : start + ROSTER_WINDOW]
        turns = TURN_FIELD.findall(window)
        titles = TITLE_FIELD.findall(window)
        quoted = QUOTED_STRING.findall(window)
        n_turns = len(turns)
        n_titles = len(titles)
        if n_turns >= 3 and n_titles >= 3:
            lineno = line_no(start)
            findings.append(Finding(
                filename, lineno, BLOCK, "fabricated-roster",
                f"remember {{ listOf( block with {n_titles} title-like entries and "
                f"{n_turns} numeric turn fields -- a hardcoded roster, not discovered sessions",
                show(lineno) or window.splitlines()[0].strip()))
        elif n_turns >= 3 and len(quoted) >= 3:
            lineno = line_no(start)
            findings.append(Finding(
                filename, lineno, WARN, "fabricated-roster",
                f"remember {{ listOf( block with 3+ numeric turn fields and {len(quoted)} "
                f"literals -- resembles a hardcoded roster",
                show(lineno) or window.splitlines()[0].strip()))

    return findings


def collect_drawable_names(res_root: Path) -> set[str]:
    """All drawable resource names under res/drawable* (extension stripped)."""
    names: set[str] = set()
    if not res_root.exists():
        return names
    for child in sorted(res_root.iterdir()):
        if not child.is_dir():
            continue
        if not (child.name.startswith("drawable") or child.name.startswith("mipmap")):
            continue
        for f in child.iterdir():
            if f.is_file():
                stem = f.name.rsplit(".", 1)[0] if "." in f.name else f.name
                names.add(stem)
    return names


def iter_java_sources(root: Path):
    java_root = root / "android" / "app" / "src" / "main" / "java"
    if not java_root.exists():
        return
    for path in sorted(java_root.rglob("*.kt")):
        yield path
    for path in sorted(java_root.rglob("*.java")):
        yield path


def audit_tree(root: Path) -> list[Finding]:
    """Audit the whole repo tree at ``root`` (the dir containing android/)."""
    root = Path(root)
    drawable_names = collect_drawable_names(root / "android" / "app" / "src" / "main" / "res")
    findings: list[Finding] = []
    for path in iter_java_sources(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = str(path.relative_to(root)) if path.is_relative_to(root) else str(path)
        findings.extend(audit_source(text, rel, drawable_names))
    return findings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="repo root containing android/")
    parser.add_argument("--json", action="store_true", help="emit findings as JSON")
    args = parser.parse_args(argv)

    findings = audit_tree(Path(args.root))
    blocks = [f for f in findings if f.severity == BLOCK]
    warns = [f for f in findings if f.severity == WARN]

    if args.json:
        print(json.dumps([f.to_dict() for f in findings], indent=2))
    else:
        current: str | None = None
        for f in findings:
            if f.path != current:
                current = f.path
                print(f"\n{f.path}:")
            print(f"  {f.render()}")
        print(f"\n{audit_tree.__module__}: {len(blocks)} BLOCK, {len(warns)} WARN")
    return 1 if blocks else 0


if __name__ == "__main__":
    sys.exit(main())
