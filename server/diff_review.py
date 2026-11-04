"""Per-agent-run file changes extracted from the tool-call ledger.

The existing diff screen (ui/CodeDiffViewer.kt) shows the whole working tree via
``client.gitDiff`` — it cannot answer "what did *this* agent change in *this*
run?". This module answers that question from TALOS's own ledger
(server/tool_ledger.py).

What the ledger actually records (ToolCallRecord.to_payload): ``tool``,
``arguments`` (redacted, arbitrary JSON), ``result_summary`` (truncated to 400
chars), ``started_at``/``finished_at``. It does NOT store content snapshots or
a ToolCallEvent type, so before/after content is recovered from the file-edit
tool arguments (``path``/``old_content``/``content`` and their common aliases).
An edit event with no recoverable content still yields a FileChange with an
empty diff — never a silently dropped file.

Hunk algorithm: ``difflib.SequenceMatcher.get_grouped_opcodes(n=3)`` groups
opcodes into hunks with 3 lines of context; old/new line numbers are attached
while walking each group (same opcodes ``difflib.unified_diff`` uses, rendered
by hand so hunks and counts stay consistent). Added/removed counts are computed
over the FULL content, independent of truncation.

Truncation rule: the rendered diff (file header + hunk headers + content
lines) is capped at ``MAX_DIFF_LINES`` (500) lines per file. When the cap
bites, ``truncated`` is True and the cut is explicit — counts still reflect
the whole file.

Endpoint (insert in server/main.py after ``kanban_stats`` (~line 818), behind
the same ``Depends(require_auth)`` as /api/approvals and /api/kanban/board;
404 for an unknown agent via ``get_agent`` — never an empty 200)::

    @app.get("/api/agents/{agent_id}/review")
    async def agent_review(agent_id: str, since: Optional[int] = None,
                           token: str = Depends(require_auth)):
        from server.tool_ledger import ledger
        from server.diff_review import build_review, UnknownAgentError
        if get_agent(agent_id) is None:
            raise HTTPException(status_code=404, detail="Agent not found")
        events = []
        for room in list_rooms():
            events.extend(ledger.recent(room.id, limit=2000, agent_id=agent_id))
        try:
            return build_review(agent_id, events, since_ms=since)
        except UnknownAgentError:
            raise HTTPException(status_code=404, detail="Agent not found")
"""

from __future__ import annotations

import difflib
import json
import re
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Tuple

#: Hard cap on rendered diff lines per file (header + hunks + content).
MAX_DIFF_LINES = 500
#: Unified-diff context lines around each change.
CONTEXT_LINES = 3

#: Tool names (lower-cased, namespace stripped) classified as create/write.
_CREATE_TOOLS = {
    "write", "write_file", "writefile", "create", "create_file", "createfile",
    "save", "save_file", "touch",
}
#: Tool names classified as in-place edit/patch.
_EDIT_TOOLS = {
    "edit", "edit_file", "editfile", "patch", "apply_patch", "applypatch",
    "update", "update_file", "replace", "str_replace", "overwrite",
}
#: Tool names classified as rename/move (old path + new path).
_RENAME_TOOLS = {
    "rename", "rename_file", "move", "move_file", "mv",
}
#: Tool names classified as delete (whole file goes away).
_DELETE_TOOLS = {
    "delete", "delete_file", "deletefile", "remove", "remove_file",
    "unlink", "rm", "trash",
}

_PATH_KEYS = (
    "path", "file_path", "filepath", "file", "filename", "file_name",
    "name", "target", "target_path", "dest", "destination", "location",
)
_OLD_PATH_KEYS = (
    "old_path", "oldPath", "source", "src", "from", "from_path",
    "previous_path", "old_name", "old_file",
)
_NEW_PATH_KEYS = (
    "new_path", "newPath", "destination", "dest", "to", "to_path",
    "target_path", "new_name", "new_file",
)
_OLD_CONTENT_KEYS = (
    "old_content", "oldContent", "before", "previous_content", "previous",
    "old_text", "oldText", "original", "original_content",
    "existing_content", "content_before", "old", "old_data",
)
_NEW_CONTENT_KEYS = (
    "new_content", "newContent", "after", "content", "new_text", "newText",
    "updated_content", "result", "text", "data", "body", "new_data",
)
_PATCH_KEYS = ("patch", "diff", "unified_diff", "changes")

_HUNK_RE = re.compile(r"^@@\s+-(\d+),?(\d*)\s+\+(\d+),?(\d*)\s+@@")
_EMPTY = object()


class UnknownAgentError(Exception):
    """Raised by build_review for an agent id outside the known roster."""

    status_code = 404

    def __init__(self, agent_id: str) -> None:
        super().__init__(f"Agent not found: {agent_id}")
        self.agent_id = agent_id


@dataclass
class DiffLine:
    kind: str  # "context" | "added" | "removed"
    text: str
    old_num: Optional[int] = None
    new_num: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "kind": self.kind,
            "text": self.text,
            "old_num": self.old_num,
            "new_num": self.new_num,
        }


@dataclass
class Hunk:
    old_start: int
    old_end: int
    new_start: int
    new_end: int
    lines: List[DiffLine] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "old_start": self.old_start,
            "old_end": self.old_end,
            "new_start": self.new_start,
            "new_end": self.new_end,
            # The header the reference review screen shows per hunk.
            "range_label": f"Lines {self.new_start}-{self.new_end}",
            "lines": [line.to_dict() for line in self.lines],
        }


@dataclass
class FileChange:
    path: str
    old_path: Optional[str] = None
    status: str = "modified"  # created | modified | deleted | renamed
    added_lines: int = 0
    removed_lines: int = 0
    diff_text: str = ""
    hunks: List[Hunk] = field(default_factory=list)
    is_binary: bool = False
    truncated: bool = False
    #: Total rendered diff lines before the 500-line cap.
    total_diff_lines: int = 0

    @property
    def is_empty(self) -> bool:
        return self.added_lines == 0 and self.removed_lines == 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "path": self.path,
            "old_path": self.old_path,
            "status": self.status,
            "added_lines": self.added_lines,
            "removed_lines": self.removed_lines,
            "diff_text": self.diff_text,
            "hunks": [hunk.to_dict() for hunk in self.hunks],
            "is_binary": self.is_binary,
            "truncated": self.truncated,
            "total_diff_lines": self.total_diff_lines,
        }


@dataclass
class ReviewSummary:
    total_files: int = 0
    total_added: int = 0
    total_removed: int = 0
    files_created: int = 0
    files_deleted: int = 0
    files_renamed: int = 0
    #: Path of the file with the most changed lines (None when empty).
    largest_file: Optional[str] = None
    largest_file_lines: int = 0
    truncated_any: bool = False

    @property
    def is_empty(self) -> bool:
        return self.total_files == 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_files": self.total_files,
            "total_added": self.total_added,
            "total_removed": self.total_removed,
            "files_created": self.files_created,
            "files_deleted": self.files_deleted,
            "files_renamed": self.files_renamed,
            "largest_file": self.largest_file,
            "largest_file_lines": self.largest_file_lines,
            "truncated_any": self.truncated_any,
            "is_empty": self.is_empty,
        }


# ---------------------------------------------------------------------------
# Ledger event access (dicts from to_payload(), or attribute objects)
# ---------------------------------------------------------------------------

def _get(obj: Any, *names: str) -> Any:
    for name in names:
        if isinstance(obj, dict):
            if name in obj and obj[name] is not None:
                return obj[name]
        elif hasattr(obj, name):
            value = getattr(obj, name)
            if value is not None:
                return value
    return None


def _get_first(obj: Any, keys: Tuple[str, ...]) -> Any:
    value = _get(obj, *keys)
    return _EMPTY if value is None else value


def _tool_kind(tool: str) -> Optional[str]:
    name = str(tool or "").strip().lower()
    name = name.split(".")[-1].split("/")[-1].split(":")[-1].strip()
    name = name.replace("-", "_")
    if name in _RENAME_TOOLS:
        return "rename"
    if name in _DELETE_TOOLS:
        return "delete"
    if name in _CREATE_TOOLS:
        return "create"
    if name in _EDIT_TOOLS:
        return "edit"
    return None


def _as_text(content: Any) -> Tuple[Optional[str], bool]:
    """Decode content to text. Returns (text, is_binary); text None = absent.

    Non-UTF-8 bytes and NUL-containing text are binary: reported as such, and
    their bytes are never diffed.
    """
    if content is None:
        return None, False
    if isinstance(content, (bytes, bytearray)):
        try:
            return bytes(content).decode("utf-8"), False
        except UnicodeDecodeError:
            return None, True
    if isinstance(content, str):
        if "\x00" in content:
            return None, True
        return content, False
    if isinstance(content, (dict, list)):
        return json.dumps(content, ensure_ascii=False, default=str), False
    return str(content), False


def _event_timestamp_ms(event: Any) -> Optional[float]:
    for key in ("started_at_ms", "timestamp_ms", "created_at_ms"):
        value = _get(event, key)
        if value is not None:
            return float(value)
    for key in ("started_at", "timestamp", "created_at", "finished_at"):
        value = _get(event, key)
        if value is not None:
            value = float(value)
            # Ledger timestamps are epoch seconds; treat values that already
            # look like milliseconds (>= 1e12) as milliseconds.
            return value if value >= 1e12 else value * 1000.0
    return None


def _parse_patch(patch_text: str) -> Tuple[List[Hunk], int, int]:
    """Parse a supplied unified diff into hunks + added/removed counts."""
    hunks: List[Hunk] = []
    current: Optional[Hunk] = None
    added = removed = 0
    old_cursor = new_cursor = 0
    for raw in patch_text.splitlines():
        match = _HUNK_RE.match(raw)
        if match:
            old_start = int(match.group(1))
            old_len = int(match.group(2)) if match.group(2) else 1
            new_start = int(match.group(3))
            new_len = int(match.group(4)) if match.group(4) else 1
            current = Hunk(
                old_start=old_start,
                old_end=old_start + old_len - 1 if old_len else old_start,
                new_start=new_start,
                new_end=new_start + new_len - 1 if new_len else new_start,
            )
            hunks.append(current)
            old_cursor, new_cursor = old_start, new_start
            continue
        if current is None or raw.startswith(("---", "+++", "diff ", "index ")):
            continue
        if raw.startswith("+"):
            current.lines.append(DiffLine("added", raw[1:], None, new_cursor))
            new_cursor += 1
            added += 1
        elif raw.startswith("-"):
            current.lines.append(DiffLine("removed", raw[1:], old_cursor, None))
            old_cursor += 1
            removed += 1
        elif raw.startswith(" "):
            current.lines.append(
                DiffLine("context", raw[1:], old_cursor, new_cursor)
            )
            old_cursor += 1
            new_cursor += 1
    return hunks, added, removed


def _render_unified(
    hunks: List[Hunk], old_path: Optional[str], path: str
) -> List[str]:
    out = [f"--- a/{old_path or path}", f"+++ b/{path}"]
    for hunk in hunks:
        old_len = hunk.old_end - hunk.old_start + 1 if hunk.old_end >= hunk.old_start else 0
        new_len = hunk.new_end - hunk.new_start + 1 if hunk.new_end >= hunk.new_start else 0
        out.append(
            f"@@ -{hunk.old_start},{old_len} +{hunk.new_start},{new_len} @@"
        )
        for line in hunk.lines:
            prefix = {"added": "+", "removed": "-", "context": " "}[line.kind]
            out.append(f"{prefix}{line.text}")
    return out


def _diff_contents(
    old_text: str, new_text: str, old_path: Optional[str], path: str
) -> Tuple[List[Hunk], str, int, int, bool, int]:
    """Returns (hunks, diff_text, added, removed, truncated, total_lines)."""
    old_lines = old_text.splitlines()
    new_lines = new_text.splitlines()
    matcher = difflib.SequenceMatcher(None, old_lines, new_lines, autojunk=False)
    opcodes = list(matcher.get_opcodes())
    added = sum(j2 - j1 for tag, _i1, _i2, j1, j2 in opcodes if tag in ("insert", "replace"))
    removed = sum(i2 - i1 for tag, i1, i2, _j1, _j2 in opcodes if tag in ("delete", "replace"))

    hunks: List[Hunk] = []
    for group in matcher.get_grouped_opcodes(CONTEXT_LINES):
        first, last = group[0], group[-1]
        old_len = last[2] - first[1]
        new_len = last[4] - first[3]
        # Unified convention: a zero-length range starts at the line before.
        old_start = first[1] + 1 if old_len else first[1]
        new_start = first[3] + 1 if new_len else first[3]
        hunk = Hunk(
            old_start=old_start,
            old_end=old_start + old_len - 1 if old_len else old_start,
            new_start=new_start,
            new_end=new_start + new_len - 1 if new_len else new_start,
        )
        for tag, i1, i2, j1, j2 in group:
            if tag == "equal":
                for offset in range(i2 - i1):
                    hunk.lines.append(
                        DiffLine("context", old_lines[i1 + offset], i1 + offset + 1, j1 + offset + 1)
                    )
            if tag in ("replace", "delete"):
                for index in range(i1, i2):
                    hunk.lines.append(DiffLine("removed", old_lines[index], index + 1, None))
            if tag in ("replace", "insert"):
                for index in range(j1, j2):
                    hunk.lines.append(DiffLine("added", new_lines[index], None, index + 1))
        hunks.append(hunk)

    rendered = _render_unified(hunks, old_path, path)
    total = len(rendered)
    truncated = total > MAX_DIFF_LINES
    if truncated:
        rendered = rendered[:MAX_DIFF_LINES]
        # Drop hunk lines that fell past the cap so hunks match diff_text.
        budget = MAX_DIFF_LINES - 2  # minus the two file-header lines
        kept: List[Hunk] = []
        for hunk in hunks:
            header_cost = 1
            if budget < header_cost:
                break
            budget -= header_cost
            take = min(len(hunk.lines), budget)
            hunk.lines = hunk.lines[:take]
            budget -= take
            kept.append(hunk)
            if budget <= 0:
                break
        hunks = kept
    else:
        hunks = hunks
    return hunks, "\n".join(rendered), added, removed, truncated, total


@dataclass
class _Accumulator:
    path: str
    old_path: Optional[str] = None
    first_old: Any = _EMPTY
    last_new: Any = _EMPTY
    seen_create: bool = False
    seen_delete: bool = False
    seen_rename: bool = False
    patch_text: Optional[str] = None
    is_binary: bool = False


def _coalesce(acc: _Accumulator, kind: str, old_content: Any, new_content: Any) -> None:
    if kind == "rename":
        acc.seen_rename = True
    if kind == "delete":
        acc.seen_delete = True
    if kind == "create":
        acc.seen_create = True
    if acc.first_old is _EMPTY and old_content is not _EMPTY:
        acc.first_old = old_content
    if new_content is not _EMPTY:
        acc.last_new = new_content


def extract_file_changes(
    ledger_events: Iterable[Any], since_ms: Optional[float] = None
) -> List[FileChange]:
    """One FileChange per path touched by file-edit ledger events.

    ``ledger_events`` are dicts as returned by ``ToolLedger.recent`` /
    ``to_payload`` (attribute objects with the same fields also work).
    ``since_ms`` filters to events at/after that epoch-millisecond instant;
    events without a timestamp cannot be filtered and are kept.
    """
    accumulators: Dict[str, _Accumulator] = {}
    order: List[str] = []

    for event in ledger_events:
        if not isinstance(event, dict) and not hasattr(event, "tool"):
            continue
        kind = _tool_kind(str(_get(event, "tool") or ""))
        if kind is None:
            continue
        if since_ms is not None:
            stamp = _event_timestamp_ms(event)
            if stamp is not None and stamp < float(since_ms):
                continue

        arguments = _get(event, "arguments")
        if isinstance(arguments, str):
            try:
                parsed = json.loads(arguments)
                arguments = parsed if isinstance(parsed, dict) else {"content": arguments}
            except (ValueError, TypeError):
                arguments = {"content": arguments}
        if not isinstance(arguments, dict):
            arguments = {}

        path_value = _get_first(arguments, _PATH_KEYS)
        old_path_value = _get_first(arguments, _OLD_PATH_KEYS)
        new_path_value = _get_first(arguments, _NEW_PATH_KEYS)
        old_content = _get_first(arguments, _OLD_CONTENT_KEYS)
        new_content = _get_first(arguments, _NEW_CONTENT_KEYS)
        patch_value = _get_first(arguments, _PATCH_KEYS)

        old_path = str(old_path_value) if old_path_value is not _EMPTY else None
        new_path = str(new_path_value) if new_path_value is not _EMPTY else None

        if kind == "rename":
            path = new_path or (str(path_value) if path_value is not _EMPTY else None)
            if not path:
                continue
            acc_path = path
            if old_path is None and path_value is not _EMPTY and str(path_value) != path:
                old_path = str(path_value)
        elif kind == "delete":
            if path_value is _EMPTY:
                continue
            acc_path = str(path_value)
            path = acc_path
            if new_content is _EMPTY:
                new_content = ""
        else:  # create / edit
            if path_value is _EMPTY:
                continue
            path = str(path_value)
            acc_path = path
            # A write/create event carries only the new content; an edit
            # event may carry both. Absent old content on a create means the
            # file did not exist before this run (unless a later edit in the
            # same run supplies it — the FIRST old content wins either way).
            if kind == "create" and old_content is _EMPTY:
                old_content = None

        acc = accumulators.get(acc_path)
        if acc is None:
            acc = _Accumulator(path=acc_path, old_path=old_path)
            accumulators[acc_path] = acc
            order.append(acc_path)
        if acc.old_path is None and old_path is not None:
            acc.old_path = old_path
        _coalesce(acc, kind, old_content, new_content)
        if isinstance(patch_value, str) and ("@@" in patch_value or patch_value.startswith(("---", "diff "))):
            acc.patch_text = patch_value

    changes: List[FileChange] = []
    for key in order:
        acc = accumulators[key]
        old_raw = None if acc.first_old is _EMPTY else acc.first_old
        new_raw = "" if acc.last_new is _EMPTY else acc.last_new

        old_text, old_binary = _as_text(old_raw)
        new_text, new_binary = _as_text(new_raw)
        is_binary = old_binary or new_binary
        if is_binary:
            # Binary-looking content is reported, never diffed byte-by-byte.
            status = "deleted" if acc.seen_delete else (
                "created" if acc.seen_create or old_raw is None else "modified"
            )
            if acc.seen_rename:
                status = "renamed"
            changes.append(
                FileChange(
                    path=acc.path,
                    old_path=acc.old_path,
                    status=status,
                    added_lines=0,
                    removed_lines=0,
                    diff_text="",
                    hunks=[],
                    is_binary=True,
                    truncated=False,
                    total_diff_lines=0,
                )
            )
            continue

        if acc.patch_text is not None and old_text in (None, "") and new_text == "":
            hunks, added, removed = _parse_patch(acc.patch_text)
            rendered = _render_unified(hunks, acc.old_path, acc.path)
            total = len(rendered)
            truncated = total > MAX_DIFF_LINES
            if truncated:
                rendered = rendered[:MAX_DIFF_LINES]
            changes.append(
                FileChange(
                    path=acc.path,
                    old_path=acc.old_path,
                    status="modified",
                    added_lines=added,
                    removed_lines=removed,
                    diff_text="\n".join(rendered),
                    hunks=hunks,
                    is_binary=False,
                    truncated=truncated,
                    total_diff_lines=total,
                )
            )
            continue

        old_str = old_text or ""
        new_str = new_text or ""
        hunks, diff_text, added, removed, truncated, total = _diff_contents(
            old_str, new_str, acc.old_path, acc.path
        )
        if acc.seen_rename:
            status = "renamed"
        elif acc.seen_delete:
            status = "deleted"
        elif acc.seen_create or old_raw is None:
            status = "created"
        else:
            status = "modified"
        changes.append(
            FileChange(
                path=acc.path,
                old_path=acc.old_path,
                status=status,
                added_lines=added,
                removed_lines=removed,
                diff_text=diff_text,
                hunks=hunks,
                is_binary=False,
                truncated=truncated,
                total_diff_lines=total,
            )
        )
    return changes


def summarise(changes: Iterable[FileChange]) -> ReviewSummary:
    """Aggregate file changes into the header summary for the review screen."""
    items = list(changes)
    largest: Optional[FileChange] = None
    for change in items:
        changed = change.added_lines + change.removed_lines
        if largest is None or changed > largest.added_lines + largest.removed_lines:
            largest = change
    return ReviewSummary(
        total_files=len(items),
        total_added=sum(c.added_lines for c in items),
        total_removed=sum(c.removed_lines for c in items),
        files_created=sum(1 for c in items if c.status == "created"),
        files_deleted=sum(1 for c in items if c.status == "deleted"),
        files_renamed=sum(1 for c in items if c.status == "renamed"),
        largest_file=largest.path if largest else None,
        largest_file_lines=(
            largest.added_lines + largest.removed_lines if largest else 0
        ),
        truncated_any=any(c.truncated for c in items),
    )


def build_review(
    agent_id: str,
    ledger_events: Iterable[Any],
    known_agent_ids: Optional[Iterable[str]] = None,
    since_ms: Optional[float] = None,
) -> Dict[str, Any]:
    """Full review payload for one agent.

    Raises UnknownAgentError (status_code 404) when ``known_agent_ids`` is
    given and the agent is not in it — the endpoint translates that into a
    plain 404 body, never an empty 200 that looks like "nothing changed".
    """
    if known_agent_ids is not None and agent_id not in set(known_agent_ids):
        raise UnknownAgentError(agent_id)
    changes = extract_file_changes(ledger_events, since_ms=since_ms)
    summary = summarise(changes)
    return {
        "agent_id": agent_id,
        "since_ms": since_ms,
        "summary": summary.to_dict(),
        "changes": [change.to_dict() for change in changes],
    }
