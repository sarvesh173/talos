"""Tests for tools/check_all.py: pure helpers + result mapping.

Covers the brace balancer (balanced / negative / EOF-unclosed / strings /
comments / char literals / raw strings), the duplicate-declaration
detector, the import heuristic, and exit-code mapping from simulated
CheckResult lists. None of these run the full repo check (that would
recurse into pytest); check_all.run_checks is exercised only through
its building blocks.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from tools.check_all import (  # noqa: E402
    FAIL,
    PASS,
    WARN,
    CheckResult,
    check_brace_balance,
    find_duplicate_declarations,
    find_unresolved_imports,
    overall_exit,
    summarize,
)


# --- brace balancer -------------------------------------------------------

def test_balanced_braces_pass():
    src = "package a\nfun f() {\n  val x = listOf(1, 2, 3)\n  if (x.isNotEmpty()) { println(x[0]) }\n}\n"
    assert check_brace_balance(src) == (True, None)


def test_balanced_nested_brackets_pass():
    src = "fun f(): Int { return { 1 }[0] }\n"
    assert check_brace_balance(src)[0] is True


def test_unbalanced_negative_reports_line():
    src = "val a = { 1 }\nval b = )\n"
    ok, line = check_brace_balance(src)
    assert ok is False
    assert line == 2


def test_unbalanced_unclosed_reports_opener_line():
    src = "fun f() {\n  val x = 1\n"
    ok, line = check_brace_balance(src)
    assert ok is False
    assert line == 1


def test_braces_inside_double_quoted_string_ignored():
    src = 'val s = "}{(( " \nfun f() {}\n'
    assert check_brace_balance(src) == (True, None)


def test_braces_inside_raw_string_ignored():
    src = 'val s = """ }{ (( """\nfun f() {}\n'
    assert check_brace_balance(src) == (True, None)


def test_braces_inside_line_comment_ignored():
    src = "// }{ ((\nfun f() { // also ) }\n}\n"
    assert check_brace_balance(src) == (True, None)


def test_braces_inside_block_comment_ignored():
    src = "/* }{ (( */\nfun f() {}\n/* outer /* nested } */ still comment ) */\n"
    assert check_brace_balance(src) == (True, None)


def test_char_literal_braces_ignored():
    src = "val a = '{'\nval b = '}'\nfun f() {}\n"
    assert check_brace_balance(src) == (True, None)


def test_escaped_quote_in_string_does_not_break_scan():
    src = 'val s = "a \\" } ( "\nfun f() {}\n'
    assert check_brace_balance(src) == (True, None)


def test_mismatched_close_is_unbalanced():
    src = "fun f() { val x = (1 } )\n"
    ok, line = check_brace_balance(src)
    assert ok is False
    assert line == 1


# --- duplicate declarations ----------------------------------------------

def test_duplicate_same_package_same_name_flagged():
    sources = {
        "a/Foo.kt": "package com.example\nclass Foo\n",
        "a/Bar.kt": "package com.example\nclass Foo\n",
    }
    dups = find_duplicate_declarations(sources)
    assert len(dups) == 1
    assert "Foo" in dups[0] and "com.example" in dups[0]


def test_same_name_different_packages_ok():
    sources = {
        "a/Foo.kt": "package com.example.a\nclass Foo\n",
        "b/Foo.kt": "package com.example.b\nclass Foo\n",
    }
    assert find_duplicate_declarations(sources) == []


def test_different_names_same_package_ok():
    sources = {
        "a/A.kt": "package com.example\nclass Alpha\n",
        "b/B.kt": "package com.example\nclass Beta\n",
    }
    assert find_duplicate_declarations(sources) == []


def test_duplicate_object_and_interface_flagged():
    sources = {
        "a/A.kt": "package com.example\nobject Thing\n",
        "b/B.kt": "package com.example\ninterface Thing\n",
    }
    dups = find_duplicate_declarations(sources)
    assert len(dups) == 1


def test_top_level_fun_overload_not_flagged():
    # Kotlin allows same-name top-level function overloads.
    sources = {
        "a/A.kt": "package com.example\nfun go(x: Int) = x\n",
        "b/B.kt": "package com.example\nfun go(s: String) = s\n",
    }
    assert find_duplicate_declarations(sources) == []


# --- import heuristic -----------------------------------------------------

def test_resolved_import_no_warning():
    sources = {
        "com/sarvesh173/talos/Foo.kt": "package com.sarvesh173.talos\nclass Foo\n",
        "com/sarvesh173/talos/Bar.kt": (
            "package com.sarvesh173.talos\nimport com.sarvesh173.talos.Foo\nclass Bar\n"
        ),
    }
    assert find_unresolved_imports(sources) == []


def test_unresolved_symbol_warns():
    sources = {
        "com/sarvesh173/talos/Bar.kt": (
            "package com.sarvesh173.talos\nimport com.sarvesh173.talos.Ghost\nclass Bar\n"
        ),
    }
    warns = find_unresolved_imports(sources)
    assert len(warns) == 1
    assert "Ghost" in warns[0]


def test_unresolved_package_warns():
    sources = {
        "com/sarvesh173/talos/Bar.kt": (
            "package com.sarvesh173.talos\nimport com.sarvesh173.talos.nope.Missing\nclass Bar\n"
        ),
    }
    warns = find_unresolved_imports(sources)
    assert len(warns) == 1
    assert "nope.Missing" in warns[0]


def test_non_talos_imports_ignored():
    sources = {
        "com/sarvesh173/talos/Bar.kt": (
            "package com.sarvesh173.talos\nimport kotlinx.coroutines.flow.Flow\nclass Bar\n"
        ),
    }
    assert find_unresolved_imports(sources) == []


def test_wildcard_import_with_known_package_ok():
    sources = {
        "com/sarvesh173/talos/ui/Foo.kt": "package com.sarvesh173.talos.ui\nclass Foo\n",
        "com/sarvesh173/talos/ui/Bar.kt": (
            "package com.sarvesh173.talos.ui\nimport com.sarvesh173.talos.ui.*\nclass Bar\n"
        ),
    }
    assert find_unresolved_imports(sources) == []


# --- exit-code mapping from simulated results ----------------------------

def test_exit_zero_when_all_pass():
    results = [CheckResult("a", PASS), CheckResult("b", PASS)]
    assert overall_exit(results) == 0


def test_exit_nonzero_on_any_fail():
    results = [CheckResult("a", PASS), CheckResult("b", FAIL, "x")]
    assert overall_exit(results) == 1


def test_warn_only_still_exits_zero():
    results = [CheckResult("a", PASS), CheckResult("b", WARN, "heuristic")]
    assert overall_exit(results) == 0


def test_summary_format_counts():
    results = [CheckResult("a", PASS), CheckResult("b", FAIL), CheckResult("c", WARN)]
    assert summarize(results) == "3 checks, 1 pass, 1 fail, 1 warn"


def test_checkresult_render_contains_status_and_evidence():
    r = CheckResult("plugin tests", FAIL, "exit=1")
    assert "FAIL" in r.render() and "plugin tests" in r.render()
    assert "exit=1" in r.render()
