#!/usr/bin/env python3
"""Single repo-wide health checker for TALOS.

Runs, in order, and prints one PASS/FAIL/WARN line each with evidence:

1. Python test suite (subprocess pytest, count captured).
2. Fabrication audit (tools.audit_fabrication.audit_tree).
3. Integration verifier (server.verify_integration.verify_all).
4. Plugin tests (cd plugins/opencode-talos-bridge && node --test);
   SKIP cleanly with a reason when node is absent.
5. Kotlin static sanity: brace/paren/bracket balance for every .kt file
   under android/app/src/main/java, ignoring strings and comments.
6. Declaration sanity (WARN-only heuristic): every
   ``import com.sarvesh173.talos.`` line resolves to a package path or
   a declared symbol somewhere in the tree.
7. Duplicate top-level declarations in the same package (FAIL).

Exit non-zero on any FAIL. Final line: ``N checks, X pass, Y fail, Z warn``.

Usage:
    python3 tools/check_all.py [--root /home/kira/talos]

No Gradle, no network.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

PASS = "PASS"
FAIL = "FAIL"
WARN = "WARN"
SKIP = "SKIP"

KOTLIN_REL = "android/app/src/main/java"
PLUGIN_REL = "plugins/opencode-talos-bridge"
TALOS_IMPORT_PREFIX = "com.sarvesh173.talos."


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def rel(root: Path, path: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


# ---------------------------------------------------------------------------
# Kotlin brace / paren / bracket balance (strings + comments ignored)
# ---------------------------------------------------------------------------

_OPEN_TO_CLOSE = {")": "(", "}": "{", "]": "["}


def check_brace_balance(text: str) -> tuple[bool, int | None]:
    """Check balance of (), {}, [] in a Kotlin source string.

    Returns ``(True, None)`` when balanced, otherwise
    ``(False, line)`` where ``line`` is 1-indexed:

    * for a closing bracket that goes negative / mismatches the stack,
      the line of that closing bracket;
    * at EOF with unclosed openers, the line of the earliest
      unclosed opener.

    Handles: double-quoted strings, triple-quoted raw strings,
    char literals, ``//`` line comments, ``/* */`` block comments
    (nested, as Kotlin allows). Escapes (``\\``) inside strings and
    char literals are honoured. An unterminated single-line string or
    char literal ends at the newline so one bad line cannot swallow
    the rest of the file.
    """
    stack: list[tuple[str, int]] = []
    i, n, line = 0, len(text), 1
    # states: normal, string, triple, char, line_comment, block_comment
    state = "normal"
    block_depth = 0

    def _newline() -> None:
        nonlocal line, state
        line += 1
        if state in ("line_comment", "string", "char"):
            state = "normal"
        # triple strings and block comments span lines; stay in state

    while i < n:
        c = text[i]
        if c == "\n":
            _newline()
            i += 1
            continue

        if state == "normal":
            if text.startswith("//", i):
                state = "line_comment"
                i += 2
            elif text.startswith("/*", i):
                state = "block_comment"
                block_depth = 1
                i += 2
            elif text.startswith('"""', i):
                state = "triple"
                i += 3
            elif c == '"':
                state = "string"
                i += 1
            elif c == "'":
                state = "char"
                i += 1
            elif c in "([{":
                stack.append((c, line))
                i += 1
            elif c in ")]}":
                if not stack or stack[-1][0] != _OPEN_TO_CLOSE[c]:
                    return False, line
                stack.pop()
                i += 1
            else:
                i += 1
        elif state == "string":
            if c == "\\":
                i += 2  # skip escaped char (may be an escaped quote)
            elif c == '"':
                state = "normal"
                i += 1
            else:
                i += 1
        elif state == "triple":
            if text.startswith('"""', i):
                state = "normal"
                i += 3
            else:
                i += 1
        elif state == "char":
            if c == "\\":
                i += 2
            elif c == "'":
                state = "normal"
                i += 1
            else:
                i += 1
        elif state == "line_comment":
            i += 1  # newline (handled above) returns to normal
        elif state == "block_comment":
            if text.startswith("/*", i):
                block_depth += 1
                i += 2
            elif text.startswith("*/", i):
                block_depth -= 1
                i += 2
                if block_depth == 0:
                    state = "normal"
            else:
                i += 1
        else:  # pragma: no cover
            i += 1

    if stack:
        return False, stack[0][1]
    return True, None


# ---------------------------------------------------------------------------
# Duplicate top-level declarations
# ---------------------------------------------------------------------------

_PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
_DECL_RE = re.compile(
    r"^\s*(?:(?:public|internal|private|protected|open|abstract|final|data|sealed|"
    r"enum|annotation|actual|expect|inline|inner)\s+)*"
    r"(class|object|interface|typealias)\s+(\w+)",
    re.MULTILINE,
)


def _package_of(text: str, fallback: str = "") -> str:
    m = _PACKAGE_RE.search(text)
    return m.group(1) if m else fallback


def _top_level_decls(text: str) -> list[str]:
    return [m.group(2) for m in _DECL_RE.finditer(text)]


def find_duplicate_declarations(sources: dict[str, str]) -> list[str]:
    """Duplicate top-level type declarations within each package.

    ``sources`` maps a display path to Kotlin source text. Top-level
    functions are deliberately excluded: same-name overloads are legal
    in Kotlin, but duplicate class/object/interface/typealias names in
    one package are a compile error.

    Returns sorted evidence strings, one per duplicate.
    """
    seen: dict[tuple[str, str], list[str]] = {}
    for path, text in sorted(sources.items()):
        pkg = _package_of(text)
        for name in _top_level_decls(text):
            seen.setdefault((pkg, name), []).append(path)
    return sorted(
        f"duplicate {name} in package {pkg or '(no package)'}: {', '.join(files)}"
        for (pkg, name), files in seen.items()
        if len(files) > 1
    )


# ---------------------------------------------------------------------------
# Import heuristic (WARN only)
# ---------------------------------------------------------------------------

_IMPORT_RE = re.compile(
    r"^\s*import\s+(com\.sarvesh173\.talos\.[^\s;]+)", re.MULTILINE
)


def _known_packages(sources: dict[str, str]) -> set[str]:
    pkgs: set[str] = set()
    for path, text in sources.items():
        pkg = _package_of(text)
        if pkg:
            pkgs.add(pkg)
        # Directory-derived package for any java/ path.
        p = path.replace("\\", "/")
        if "/java/" in p:
            dir_part = p.split("/java/", 1)[1].rsplit("/", 1)[0]
            if dir_part:
                pkgs.add(dir_part.replace("/", "."))
            pkgs.add("")  # harmless; filtered by prefix checks below
    # add all parent packages of known ones (importing a parent package dir
    # that contains subdirs is still a real directory)
    expanded: set[str] = set()
    for pkg in pkgs:
        parts = pkg.split(".")
        for k in range(1, len(parts) + 1):
            expanded.add(".".join(parts[:k]))
    return expanded


def find_unresolved_imports(sources: dict[str, str]) -> list[str]:
    """Heuristic unresolved-import warnings.

    For each ``import com.sarvesh173.talos...``: the parent package path
    must exist among the sources, and for a symbol import the simple name
    must appear as a top-level declaration somewhere (or a file of that
    name), or name an outer type that is declared (nested-type import).
    Best-effort by design; failures are WARNs, never FAILs.
    """
    declared: set[str] = set()
    for text in sources.values():
        declared.update(_top_level_decls(text))
    files_by_name = {
        p.replace("\\", "/").rsplit("/", 1)[-1][:-3]
        for p in sources
        if p.replace("\\", "/").endswith(".kt")
    }
    known_pkgs = _known_packages(sources)

    warnings: list[str] = []
    for path, text in sorted(sources.items()):
        for lineno, line in enumerate(text.splitlines(), start=1):
            m = _IMPORT_RE.match(line)
            if not m:
                continue
            fqn = m.group(1).strip()
            if fqn.endswith(".*"):
                pkg = fqn[:-2]
                if pkg not in known_pkgs:
                    warnings.append(
                        f"{path}:{lineno}: unresolved import {fqn} (package path missing)"
                    )
                continue
            parent, _, simple = fqn.rpartition(".")
            problems = []
            if parent not in known_pkgs:
                problems.append("package path missing")
            if simple not in declared and simple not in files_by_name:
                outer = parent.rpartition(".")[2]
                if outer not in declared:
                    problems.append(f"symbol {simple} not declared")
            if problems:
                warnings.append(
                    f"{path}:{lineno}: unresolved import {fqn} ({'; '.join(problems)})"
                )
    return sorted(warnings)


# ---------------------------------------------------------------------------
# Check result plumbing
# ---------------------------------------------------------------------------

@dataclass
class CheckResult:
    name: str
    status: str  # PASS | FAIL | WARN | SKIP
    evidence: str = ""

    def render(self) -> str:
        head = f"{self.status:4} {self.name}"
        return f"{head} -- {self.evidence}" if self.evidence else head


def overall_exit(results: list[CheckResult]) -> int:
    return 1 if any(r.status == FAIL for r in results) else 0


def summarize(results: list[CheckResult]) -> str:
    n = len(results)
    x = sum(1 for r in results if r.status == PASS)
    y = sum(1 for r in results if r.status == FAIL)
    z = sum(1 for r in results if r.status in (WARN, SKIP))
    return f"{n} checks, {x} pass, {y} fail, {z} warn"


def _tail(text: str, lines: int = 3) -> str:
    stripped = [ln.rstrip() for ln in text.strip().splitlines() if ln.strip()]
    return " | ".join(stripped[-lines:]) if stripped else "(no output)"


def _pytest_count(output: str) -> str:
    m = re.search(r"(\d+\s+(?:passed|failed|error|errors|skipped)[\w\s,]*)$", output.strip())
    if m:
        return m.group(1)
    m2 = re.search(r"(\d+ failed[^\n]*|[\d, ]+passed[^\n]*)", output)
    return m2.group(1) if m2 else _tail(output, 1)


# ---------------------------------------------------------------------------
# Individual checks
# ---------------------------------------------------------------------------

def check_python_tests(root: Path) -> CheckResult:
    name = "python test suite"
    cmd = [sys.executable, "-m", "pytest", "tests/", "-q"]
    try:
        proc = subprocess.run(
            cmd, cwd=str(root), capture_output=True, text=True, timeout=600
        )
    except subprocess.TimeoutExpired:
        return CheckResult(name, FAIL, "timeout after 600s")
    except OSError as exc:
        return CheckResult(name, FAIL, f"could not run pytest: {exc}")
    out = proc.stdout + "\n" + proc.stderr
    evidence = f"{_pytest_count(out)}; exit={proc.returncode}"
    return CheckResult(name, PASS if proc.returncode == 0 else FAIL, evidence)


def check_fabrication_audit(root: Path) -> CheckResult:
    name = "fabrication audit"
    try:
        sys.path.insert(0, str(root))
        from tools import audit_fabrication as AF  # noqa: PLC0415

        findings = AF.audit_tree(root)
        blocks = [f for f in findings if f.severity == "BLOCK"]
        warns = [f for f in findings if f.severity == "WARN"]
        if blocks:
            sample = "; ".join(f.render().replace("\n", " ") for f in blocks[:3])
            return CheckResult(
                name, FAIL, f"{len(blocks)} BLOCK, {len(warns)} WARN; {sample}"
            )
        evidence = f"0 BLOCK, {len(warns)} WARN"
        status = WARN if warns else PASS
        return CheckResult(name, status, evidence)
    except Exception as exc:  # fall back to running the script
        try:
            proc = subprocess.run(
                [sys.executable, str(root / "tools" / "audit_fabrication.py"),
                 "--root", str(root)],
                capture_output=True, text=True, timeout=300,
            )
            out = proc.stdout + proc.stderr
            status = PASS if proc.returncode == 0 else FAIL
            return CheckResult(name, status, f"{_tail(out)}; import failed: {exc}")
        except Exception as exc2:
            return CheckResult(name, FAIL, f"import ({exc}) and script run ({exc2}) both failed")


def check_integration_verifier(root: Path) -> CheckResult:
    name = "integration verifier"
    try:
        sys.path.insert(0, str(root))
        from server import verify_integration as V  # noqa: PLC0415

        result = V.verify_all(root)
        wired = len(result["wired"])
        dead = result["dead_code"]
        fake = result["still_fake"]
        evidence = f"{wired} wired, {len(dead)} dead-code, {len(fake)} still-fake"
        if not result["ok"]:
            bits = []
            if dead:
                bits.append("DEAD: " + "; ".join(dead))
            if fake:
                bits.append("FAKE: " + "; ".join(fake))
            evidence += "; " + " | ".join(bits)
        return CheckResult(name, PASS if result["ok"] else FAIL, evidence)
    except Exception as exc:
        return CheckResult(name, FAIL, f"verifier import/run failed: {exc}")


def check_plugin_tests(root: Path) -> CheckResult:
    name = "plugin tests"
    plugin_dir = root / PLUGIN_REL
    if shutil.which("node") is None:
        return CheckResult(name, SKIP, "node not found on PATH; skipping node --test")
    if not plugin_dir.is_dir():
        return CheckResult(name, SKIP, f"{PLUGIN_REL} missing; skipping")
    try:
        proc = subprocess.run(
            ["node", "--test"], cwd=str(plugin_dir),
            capture_output=True, text=True, timeout=300,
        )
    except subprocess.TimeoutExpired:
        return CheckResult(name, FAIL, "timeout after 300s")
    except OSError as exc:
        return CheckResult(name, SKIP, f"could not run node: {exc}")
    out = proc.stdout + "\n" + proc.stderr
    evidence = f"exit={proc.returncode}; {_tail(out)}"
    return CheckResult(name, PASS if proc.returncode == 0 else FAIL, evidence)


def iter_kt_sources(root: Path) -> dict[str, str]:
    base = root / KOTLIN_REL
    sources: dict[str, str] = {}
    if not base.exists():
        return sources
    for path in sorted(base.rglob("*.kt")):
        try:
            sources[rel(root, path)] = path.read_text(
                encoding="utf-8", errors="replace"
            )
        except OSError:
            sources[rel(root, path)] = ""
    return sources


def check_brace_balance_tree(root: Path) -> CheckResult:
    name = "kotlin brace balance"
    sources = iter_kt_sources(root)
    if not sources:
        return CheckResult(name, WARN, f"no .kt files under {KOTLIN_REL}")
    bad: list[str] = []
    for path, text in sorted(sources.items()):
        ok, line = check_brace_balance(text)
        if not ok:
            bad.append(f"{path}:{line}: unbalanced (depth negative or unclosed opener at line {line})")
    if bad:
        return CheckResult(
            name, FAIL, f"{len(bad)} unbalanced of {len(sources)} files; " + "; ".join(bad[:5])
        )
    return CheckResult(name, PASS, f"{len(sources)} files balanced")


def check_imports_tree(root: Path) -> CheckResult:
    name = "kotlin import sanity"
    sources = iter_kt_sources(root)
    if not sources:
        return CheckResult(name, WARN, f"no .kt files under {KOTLIN_REL}")
    warnings = find_unresolved_imports(sources)
    if warnings:
        return CheckResult(
            name, WARN, f"{len(warnings)} unresolved; " + "; ".join(warnings[:5])
        )
    return CheckResult(name, PASS, "all imports resolve (heuristic)")


def check_duplicates_tree(root: Path) -> CheckResult:
    name = "kotlin duplicate declarations"
    sources = iter_kt_sources(root)
    if not sources:
        return CheckResult(name, WARN, f"no .kt files under {KOTLIN_REL}")
    dups = find_duplicate_declarations(sources)
    if dups:
        return CheckResult(name, FAIL, f"{len(dups)} duplicates; " + "; ".join(dups[:5]))
    return CheckResult(name, PASS, f"no duplicates across {len(sources)} files")


CHECKS = [
    ("python test suite", check_python_tests),
    ("fabrication audit", check_fabrication_audit),
    ("integration verifier", check_integration_verifier),
    ("plugin tests", check_plugin_tests),
    ("kotlin brace balance", check_brace_balance_tree),
    ("kotlin import sanity", check_imports_tree),
    ("kotlin duplicate declarations", check_duplicates_tree),
]


def run_checks(root: Path) -> list[CheckResult]:
    return [fn(root) for _, fn in CHECKS]


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Repo-wide health check for TALOS")
    ap.add_argument("--root", default=str(repo_root()))
    ns = ap.parse_args(argv)
    root = Path(ns.root)
    results = run_checks(root)
    for r in results:
        print(r.render())
    print(summarize(results))
    return overall_exit(results)


if __name__ == "__main__":
    raise SystemExit(main())
