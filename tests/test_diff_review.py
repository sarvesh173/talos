"""Diff-review tests: ledger events in, per-file unified diffs out.

Covers the review contract: correct +N/-N and hunk line numbers, created and
deleted files, binary flagging, the 500-line truncation rule, 3 lines of
context, the 404 rule for unknown agents, the explicit empty summary, and
renames keeping both paths.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.diff_review import (
    MAX_DIFF_LINES,
    UnknownAgentError,
    build_review,
    extract_file_changes,
    summarise,
)


def _event(tool, arguments, agent_id="a1", started_at=1_700_000_000.0):
    return {
        "id": "tool_x",
        "room_id": "the-council",
        "agent_id": agent_id,
        "agent_name": "Hermes",
        "tool": tool,
        "arguments": arguments,
        "status": "ok",
        "result_summary": "",
        "error": "",
        "started_at": started_at,
        "finished_at": started_at + 1,
    }


class ModifiedFileTest(unittest.TestCase):
    def test_counts_and_hunk_line_numbers(self):
        old = "\n".join(f"line {i}" for i in range(1, 11))
        new = old.replace("line 5", "changed 5")
        changes = extract_file_changes(
            [_event("edit", {"path": "a.py", "old_content": old, "content": new})]
        )
        self.assertEqual(len(changes), 1)
        change = changes[0]
        self.assertEqual(change.path, "a.py")
        self.assertEqual(change.status, "modified")
        self.assertEqual(change.added_lines, 1)
        self.assertEqual(change.removed_lines, 1)
        self.assertFalse(change.truncated)
        self.assertFalse(change.is_binary)
        self.assertEqual(len(change.hunks), 1)
        hunk = change.hunks[0]
        # Change is on line 5; hunk covers lines 2-8 with 3 context lines.
        self.assertEqual(hunk.new_start, 2)
        self.assertEqual(hunk.new_end, 8)
        removed = [ln for ln in hunk.lines if ln.kind == "removed"]
        added = [ln for ln in hunk.lines if ln.kind == "added"]
        self.assertEqual(removed[0].old_num, 5)
        self.assertEqual(added[0].new_num, 5)
        self.assertIn("@@ -2,7 +2,7 @@", change.diff_text)


class CreatedDeletedTest(unittest.TestCase):
    def test_created_file_all_additions(self):
        changes = extract_file_changes(
            [_event("write", {"path": "new.txt", "content": "one\ntwo\nthree"})]
        )
        self.assertEqual(len(changes), 1)
        change = changes[0]
        self.assertEqual(change.status, "created")
        self.assertEqual(change.added_lines, 3)
        self.assertEqual(change.removed_lines, 0)
        self.assertTrue(all(ln.kind == "added" for h in change.hunks for ln in h.lines))
        self.assertIn("+++ b/new.txt", change.diff_text)

    def test_deleted_file_all_removals(self):
        changes = extract_file_changes(
            [
                _event("edit", {"path": "gone.txt", "old_content": "a\nb", "content": "a\nb"}),
                _event("delete", {"path": "gone.txt"}),
            ]
        )
        self.assertEqual(len(changes), 1)
        change = changes[0]
        self.assertEqual(change.status, "deleted")
        self.assertEqual(change.removed_lines, 2)
        self.assertEqual(change.added_lines, 0)
        self.assertTrue(all(ln.kind == "removed" for h in change.hunks for ln in h.lines))


class BinaryTest(unittest.TestCase):
    def test_non_utf8_bytes_flagged_not_diffed(self):
        changes = extract_file_changes(
            [_event("write", {"path": "img.bin", "content": b"\x89PNG\xff\xfe\x00abc"})]
        )
        self.assertEqual(len(changes), 1)
        change = changes[0]
        self.assertTrue(change.is_binary)
        self.assertEqual(change.diff_text, "")
        self.assertEqual(change.hunks, [])

    def test_nul_in_text_flagged_binary(self):
        changes = extract_file_changes(
            [_event("write", {"path": "x.dat", "content": "a\x00b"})]
        )
        self.assertTrue(changes[0].is_binary)


class TruncationTest(unittest.TestCase):
    def test_600_line_file_truncated_at_500(self):
        body = "\n".join(f"row {i}" for i in range(600))
        changes = extract_file_changes(
            [_event("write", {"path": "big.txt", "content": body})]
        )
        change = changes[0]
        self.assertTrue(change.truncated)
        self.assertEqual(len(change.diff_text.splitlines()), MAX_DIFF_LINES)
        # Counts still reflect the whole file, not the capped render.
        self.assertEqual(change.added_lines, 600)
        self.assertGreater(change.total_diff_lines, MAX_DIFF_LINES)
        summary = summarise(changes)
        self.assertTrue(summary.truncated_any)


class ContextTest(unittest.TestCase):
    def test_context_is_three_lines_not_more(self):
        old_lines = [f"line {i}" for i in range(1, 31)]
        new_lines = list(old_lines)
        new_lines[0] = "changed first"
        new_lines[29] = "changed last"
        changes = extract_file_changes(
            [
                _event(
                    "edit",
                    {
                        "path": "c.txt",
                        "old_content": "\n".join(old_lines),
                        "content": "\n".join(new_lines),
                    },
                )
            ]
        )
        change = changes[0]
        self.assertEqual(len(change.hunks), 2)
        for hunk in change.hunks:
            context = [ln for ln in hunk.lines if ln.kind == "context"]
            self.assertLessEqual(len(context), 6)  # 3 before + 3 after
        first, last = change.hunks
        self.assertEqual(first.new_start, 1)
        self.assertEqual(first.new_end, 4)  # line 1 change + 3 context
        self.assertEqual(last.new_start, 27)
        self.assertEqual(last.new_end, 30)


class UnknownAgentTest(unittest.TestCase):
    def test_unknown_agent_raises_404(self):
        with self.assertRaises(UnknownAgentError) as caught:
            build_review("ghost", [_event("write", {"path": "a", "content": "x"})],
                         known_agent_ids=["a1", "a2"])
        self.assertEqual(caught.exception.status_code, 404)

    def test_known_agent_builds_payload(self):
        payload = build_review(
            "a1",
            [_event("write", {"path": "a.txt", "content": "hi"})],
            known_agent_ids=["a1"],
        )
        self.assertEqual(payload["agent_id"], "a1")
        self.assertEqual(payload["summary"]["total_files"], 1)
        self.assertEqual(payload["changes"][0]["path"], "a.txt")


class EmptyLedgerTest(unittest.TestCase):
    def test_empty_ledger_explicit_empty_summary(self):
        changes = extract_file_changes([])
        self.assertEqual(changes, [])
        summary = summarise(changes)
        self.assertTrue(summary.is_empty)
        self.assertEqual(summary.total_files, 0)
        self.assertEqual(summary.total_added, 0)
        self.assertEqual(summary.total_removed, 0)
        self.assertIsNone(summary.largest_file)
        self.assertFalse(summary.truncated_any)
        payload = build_review("a1", [])
        self.assertTrue(payload["summary"]["is_empty"])
        self.assertEqual(payload["changes"], [])

    def test_non_file_tools_ignored(self):
        changes = extract_file_changes(
            [_event("shell", {"cmd": "ls"}), _event("read_file", {"path": "a"})]
        )
        self.assertEqual(changes, [])


class RenameTest(unittest.TestCase):
    def test_rename_keeps_both_paths(self):
        changes = extract_file_changes(
            [
                _event(
                    "rename",
                    {
                        "old_path": "old/name.txt",
                        "new_path": "new/name.txt",
                        "old_content": "same\nbody",
                        "content": "same\nbody",
                    },
                )
            ]
        )
        self.assertEqual(len(changes), 1)
        change = changes[0]
        self.assertEqual(change.old_path, "old/name.txt")
        self.assertEqual(change.path, "new/name.txt")
        self.assertEqual(change.status, "renamed")
        summary = summarise(changes)
        self.assertEqual(summary.files_renamed, 1)


class SummaryTest(unittest.TestCase):
    def test_totals_and_largest_file(self):
        events = [
            _event("write", {"path": "small.txt", "content": "one"}),
            _event("write", {"path": "big.txt", "content": "a\nb\nc\nd"}),
        ]
        summary = summarise(extract_file_changes(events))
        self.assertEqual(summary.total_files, 2)
        self.assertEqual(summary.total_added, 5)
        self.assertEqual(summary.files_created, 2)
        self.assertEqual(summary.largest_file, "big.txt")
        self.assertEqual(summary.largest_file_lines, 4)

    def test_since_ms_filters_old_events(self):
        events = [
            _event("write", {"path": "old.txt", "content": "x"}, started_at=100.0),
            _event("write", {"path": "new.txt", "content": "y"}, started_at=200.0),
        ]
        changes = extract_file_changes(events, since_ms=150_000)
        self.assertEqual([c.path for c in changes], ["new.txt"])

    def test_multiple_edits_coalesce_to_one_change(self):
        events = [
            _event("edit", {"path": "f.txt", "old_content": "v1", "content": "v2"}),
            _event("edit", {"path": "f.txt", "old_content": "v2", "content": "v3"}),
        ]
        changes = extract_file_changes(events)
        self.assertEqual(len(changes), 1)
        self.assertEqual(changes[0].removed_lines, 1)
        self.assertEqual(changes[0].added_lines, 1)
        self.assertIn("v3", changes[0].diff_text)


if __name__ == "__main__":
    unittest.main()
