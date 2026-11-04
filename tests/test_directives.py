"""Tests for the SYSTEM.md :::talos directive parser (core/directives.py)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.directives import (
    ChartDirective,
    ConfirmDirective,
    EmojiDirective,
    PermissionDirective,
    SelectDirective,
    extract_directives,
    parse_directives,
    validate_directive,
)

CHART = (
    ':::talos\n{"type":"chart","kind":"bar","title":"tok/s",'
    '"x":["a","b"],"series":[{"name":"tok/s","values":[1.5,2.5]}]}\n:::'
)
SELECT = (
    ':::talos\n{"type":"select","multi":true,"title":"Which?",'
    '"options":[{"id":"orb","label":"Thinking orb","checked":true},'
    '{"id":"glass","label":"Glass"}]}\n:::'
)
CONFIRM = (
    ':::talos\n{"type":"confirm","title":"Delete?","deadline_seconds":60,'
    '"default_on_timeout":"deny"}\n:::'
)
PERMISSION = (
    ':::talos\n{"type":"permission","tool":"shell","command":"echo hi",'
    '"scopes":["once","session"]}\n:::'
)
EMOJI = ':::talos\n{"type":"emoji","title":"How did that land?","options":["🔥","😐","💀","👀"]}\n:::'


class DirectiveParserTest(unittest.TestCase):
    def test_plain_prose_no_blocks(self):
        parsed = parse_directives("Just a plain reply, nothing to render.")
        self.assertEqual(parsed.directives, [])
        self.assertEqual(parsed.prose, "Just a plain reply, nothing to render.")
        self.assertEqual(parsed.segments[0].kind, "prose")

    def test_single_chart_block(self):
        parsed = parse_directives(f"Here you go:\n\n{CHART}")
        self.assertEqual(len(parsed.directives), 1)
        directive = parsed.directives[0]
        self.assertIsInstance(directive, ChartDirective)
        self.assertEqual(directive.kind, "bar")
        self.assertEqual(directive.series[0].values, [1.5, 2.5])
        self.assertIn("Here you go:", parsed.prose)
        self.assertNotIn(":::talos", parsed.prose)

    def test_malformed_json_dropped_prose_preserved(self):
        text = 'Before.\n:::talos\n{"type":"chart", nope}\n:::\nAfter.'
        parsed = parse_directives(text)
        self.assertEqual(parsed.directives, [])
        self.assertIn("Before.", parsed.prose)
        self.assertIn("After.", parsed.prose)
        self.assertNotIn("nope", parsed.prose)

    def test_trailing_comma_dropped(self):
        text = ':::talos\n{"type":"emoji","options":["a","b","c","d",]}\n:::'
        self.assertEqual(parse_directives(text).directives, [])

    def test_chart_length_mismatch_dropped(self):
        bad = (
            ':::talos\n{"type":"chart","kind":"bar","x":["a","b","c"],'
            '"series":[{"name":"s","values":[1,2]}]}\n:::'
        )
        self.assertEqual(parse_directives(bad).directives, [])

    def test_chart_null_gap_and_bool_rejected(self):
        ok = (
            ':::talos\n{"type":"chart","kind":"line","x":["a","b"],'
            '"series":[{"name":"s","values":[1,null]}]}\n:::'
        )
        self.assertEqual(len(parse_directives(ok).directives), 1)
        bad = (
            ':::talos\n{"type":"chart","kind":"line","x":["a","b"],'
            '"series":[{"name":"s","values":[1,true]}]}\n:::'
        )
        self.assertEqual(parse_directives(bad).directives, [])

    def test_multiple_blocks_preserve_order(self):
        text = f"First.\n\n{CHART}\n\nMiddle.\n\n{SELECT}\n\nLast."
        parsed = parse_directives(text)
        self.assertEqual(
            [type(d).__name__ for d in parsed.directives],
            ["ChartDirective", "SelectDirective"],
        )
        kinds = [s.kind for s in parsed.segments]
        self.assertEqual(
            kinds, ["prose", "directive", "prose", "directive", "prose"]
        )
        for chunk in ("First.", "Middle.", "Last."):
            self.assertIn(chunk, parsed.prose)

    def test_three_block_cap(self):
        text = "\n".join([CHART, SELECT, CONFIRM, EMOJI])
        parsed = parse_directives(text)
        self.assertEqual(len(parsed.directives), 3)
        self.assertNotIn("emoji", [d.type for d in parsed.directives])

    def test_permission_capped_at_one(self):
        text = f"{PERMISSION}\n\n{PERMISSION}"
        parsed = parse_directives(text)
        self.assertEqual(len(parsed.directives), 1)
        self.assertIsInstance(parsed.directives[0], PermissionDirective)

    def test_unknown_type_dropped(self):
        text = ':::talos\n{"type":"hologram","title":"x"}\n:::'
        self.assertEqual(parse_directives(text).directives, [])

    def test_non_object_body_dropped(self):
        for body in ('[1,2,3]', '"hello"', "42"):
            text = f":::talos\n{body}\n:::"
            self.assertEqual(parse_directives(text).directives, [], body)

    def test_unclosed_fence_stays_prose(self):
        text = 'Oops :::talos\n{"type":"emoji","options":["a","b","c","d"]}'
        parsed = parse_directives(text)
        self.assertEqual(parsed.directives, [])
        self.assertIn("Oops", parsed.prose)

    def test_confirm_requires_default_on_timeout(self):
        missing = ':::talos\n{"type":"confirm","title":"Delete?","deadline_seconds":60}\n:::'
        self.assertEqual(parse_directives(missing).directives, [])
        ok = parse_directives(CONFIRM)
        self.assertIsInstance(ok.directives[0], ConfirmDirective)
        self.assertEqual(ok.directives[0].default_on_timeout, "deny")
        self.assertEqual(ok.directives[0].deadline_seconds, 60)

    def test_confirm_deadline_bounds(self):
        for seconds in (5, 601):
            text = (
                f':::talos\n{{"type":"confirm","title":"T?",'
                f'"deadline_seconds":{seconds},"default_on_timeout":"deny"}}\n:::'
            )
            self.assertEqual(parse_directives(text).directives, [], seconds)

    def test_select_checked_and_multi(self):
        directive = parse_directives(SELECT).directives[0]
        self.assertIsInstance(directive, SelectDirective)
        self.assertTrue(directive.multi)
        self.assertTrue(directive.options[0].checked)
        self.assertFalse(directive.options[1].checked)

    def test_select_option_bounds(self):
        one = ':::talos\n{"type":"select","options":[{"id":"a","label":"A"}]}\n:::'
        self.assertEqual(parse_directives(one).directives, [])

    def test_emoji_and_permission_shapes(self):
        emoji = parse_directives(EMOJI).directives[0]
        self.assertIsInstance(emoji, EmojiDirective)
        self.assertEqual(len(emoji.options), 4)
        permission = parse_directives(PERMISSION).directives[0]
        self.assertEqual(permission.tool, "shell")
        self.assertEqual(permission.scopes, ["once", "session"])

    def test_empty_and_none_input(self):
        self.assertEqual(parse_directives("").directives, [])
        self.assertEqual(parse_directives(None).directives, [])
        self.assertEqual(extract_directives(""), [])

    def test_validate_directive_direct(self):
        self.assertIsNone(validate_directive({"type": "chart"}))
        self.assertIsNone(validate_directive("nope"))
        good = validate_directive(
            {
                "type": "game",
                "game": "tic-tac-toe",
                "title": "Your move",
                "state": {"board": ["", "X", ""]},
            }
        )
        self.assertIsNotNone(good)
        self.assertEqual(good.game, "tic-tac-toe")

    def test_to_dict_round_trip(self):
        parsed = parse_directives(f"Hi\n\n{CHART}")
        data = parsed.to_dict()
        self.assertEqual(data["directives"][0]["type"], "chart")
        self.assertEqual(data["directives"][0]["series"][0]["values"], [1.5, 2.5])
        self.assertEqual(data["segments"][0]["kind"], "prose")


if __name__ == "__main__":
    unittest.main()
