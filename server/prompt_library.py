"""Reusable saved-prompt library with time-of-render variable substitution.

A saved prompt stores its raw ``{{var}}`` placeholders intact; substitution
happens only in :func:`render_prompt`, never at save time, so one saved
prompt stays reusable for many values.

Missing variables are *reported* in the result, never guessed or silently
emitted as a raw placeholder into the model's context: callers check
``result.missing`` and prompt the user for those values first.

Usage counting (``use_count`` / ``last_used_at``) increments only on a
fully successful render (no missing variables).
"""

from __future__ import annotations

import json
import re
import sqlite3
import time
import uuid
from dataclasses import dataclass, field
from typing import Dict, List, Optional

# Valid placeholder name: identifier-like (e.g. topic, style_notes, v2).
_NAME_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
# A well-formed placeholder, allowing whitespace inside the braces.
_PLACEHOLDER_RE = re.compile(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")

_UNSET = object()


class MalformedPlaceholderError(ValueError):
    """Raised when a prompt body contains a malformed ``{{...}}`` placeholder."""


# Alias with the other natural name.
MalformedPromptError = MalformedPlaceholderError


def extract_variables(body: str) -> List[str]:
    """Return placeholder names in first-appearance order, deduplicated.

    Single braces that are not part of a ``{{...}}`` placeholder (e.g. JSON
    ``{"a": 1}``) are ignored and are not placeholders.

    :raises MalformedPlaceholderError: if a ``{{`` is not closed by ``}}``
        with a valid variable name inside (e.g. ``{{topic``, ``{{}}``,
        ``{{a b}}``).
    """
    if not isinstance(body, str):
        raise ValueError("body must be a string")
    variables: List[str] = []
    pos = 0
    while True:
        start = body.find("{{", pos)
        if start == -1:
            break
        end = body.find("}}", start + 2)
        if end == -1:
            snippet = body[start : start + 40]
            raise MalformedPlaceholderError(
                f"Malformed placeholder {snippet!r}: missing closing '}}' "
                f"for placeholder starting at index {start}"
            )
        inner = body[start + 2 : end].strip()
        if not inner:
            raise MalformedPlaceholderError(
                f"Malformed placeholder at index {start}: empty variable name "
                f"in {body[start:end + 2]!r}"
            )
        if not _NAME_RE.match(inner):
            raise MalformedPlaceholderError(
                f"Malformed placeholder at index {start}: {inner!r} is not a "
                f"valid variable name (expected an identifier like 'topic')"
            )
        if inner not in variables:
            variables.append(inner)
        pos = end + 2
    return variables


def _validate_variables_arg(variables) -> List[str]:
    """Validate an explicitly supplied variable list, deduplicated in order."""
    if variables is None:
        return []
    # Accept a single string as one variable name.
    if isinstance(variables, (str, bytes)):
        variables = [variables]
    out: List[str] = []
    for name in variables:
        if not isinstance(name, str) or not _NAME_RE.match(name.strip()):
            raise ValueError(f"Invalid variable name: {name!r}")
        name = name.strip()
        if name not in out:
            out.append(name)
    return out


@dataclass
class Prompt:
    """One saved prompt. ``body`` always keeps its placeholders intact."""

    id: str
    title: str
    body: str
    category: Optional[str] = None
    variables: List[str] = field(default_factory=list)
    use_count: int = 0
    last_used_at: Optional[float] = None
    created_at: float = 0.0
    updated_at: float = 0.0

    def to_dict(self) -> Dict:
        return {
            "id": self.id,
            "title": self.title,
            "body": self.body,
            "category": self.category,
            "variables": list(self.variables),
            "use_count": self.use_count,
            "last_used_at": self.last_used_at,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        }

    model_dump = to_dict


@dataclass
class RenderResult:
    """Result of rendering a prompt: text plus any missing variables."""

    text: str
    missing: List[str] = field(default_factory=list)

    @property
    def rendered(self) -> str:
        return self.text

    @property
    def rendered_text(self) -> str:
        return self.text

    @property
    def missing_variables(self) -> List[str]:
        return self.missing

    @property
    def is_complete(self) -> bool:
        return not self.missing

    @property
    def ok(self) -> bool:
        return not self.missing

    def __getitem__(self, key):
        if key in ("text", "rendered", "rendered_text"):
            return self.text
        if key in ("missing", "missing_variables"):
            return self.missing
        raise KeyError(key)


def _ensure_table(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS prompts (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            category TEXT,
            variables_json TEXT DEFAULT '[]',
            use_count INTEGER DEFAULT 0,
            last_used_at REAL,
            created_at REAL,
            updated_at REAL
        )
        """
    )


def _row_to_prompt(row: sqlite3.Row) -> Prompt:
    try:
        variables = json.loads(row["variables_json"] or "[]")
        if not isinstance(variables, list):
            variables = []
    except (TypeError, ValueError):
        variables = []
    return Prompt(
        id=row["id"],
        title=row["title"],
        body=row["body"],
        category=row["category"],
        variables=[str(v) for v in variables],
        use_count=int(row["use_count"] or 0),
        last_used_at=row["last_used_at"],
        created_at=row["created_at"] or 0.0,
        updated_at=row["updated_at"] or 0.0,
    )


def create_prompt(conn, title, body, category=None, variables=None, **kwargs) -> Prompt:
    """Save a new prompt, storing its body with ``{{var}}`` placeholders intact.

    ``variables`` may be an explicit list of variable names; when omitted it
    defaults to :func:`extract_variables` of the body. A malformed
    placeholder raises before anything is stored.
    """
    if "prompt_id" in kwargs and kwargs["prompt_id"]:
        prompt_id = str(kwargs["prompt_id"])
    else:
        prompt_id = uuid.uuid4().hex[:12]
    if not isinstance(title, str) or not title.strip():
        raise ValueError("title must be a non-empty string")
    if not isinstance(body, str) or not body:
        raise ValueError("body must be a non-empty string")
    # Raises MalformedPlaceholderError on an unclosed/invalid placeholder.
    extracted = extract_variables(body)
    stored_variables = (
        _validate_variables_arg(variables) if variables is not None else extracted
    )
    if category is not None and not isinstance(category, str):
        raise ValueError("category must be a string or None")

    _ensure_table(conn)
    now = time.time()
    conn.execute(
        """
        INSERT INTO prompts (id, title, body, category, variables_json,
                             use_count, last_used_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 0, NULL, ?, ?)
        """,
        (prompt_id, title.strip(), body, category, json.dumps(stored_variables), now, now),
    )
    conn.commit()
    prompt = get_prompt(conn, prompt_id)
    assert prompt is not None  # just inserted
    return prompt


def get_prompt(conn, prompt_id=None, **kwargs) -> Optional[Prompt]:
    """Return the prompt with this id, or ``None`` if it does not exist."""
    if prompt_id is None:
        prompt_id = kwargs.get("id") or kwargs.get("prompt_id")
    _ensure_table(conn)
    row = conn.execute("SELECT * FROM prompts WHERE id = ?", (prompt_id,)).fetchone()
    if row is None:
        return None
    return _row_to_prompt(row)


def list_prompts(conn, category=None, query=None, **kwargs) -> List[Prompt]:
    """List prompts, most-used first (``use_count`` then ``last_used_at``).

    ``category`` filters by exact category; ``query`` filters by a
    case-insensitive substring match on title or body.
    """
    if category is None and "cat" in kwargs:
        category = kwargs["cat"]
    _ensure_table(conn)
    clauses = []
    params: list = []
    if category is not None:
        clauses.append("category = ?")
        params.append(category)
    if query:
        escaped = (
            str(query).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        )
        clauses.append(
            "(LOWER(title) LIKE '%' || LOWER(?) || '%' ESCAPE '\\' "
            "OR LOWER(body) LIKE '%' || LOWER(?) || '%' ESCAPE '\\')"
        )
        params.extend([escaped, escaped])
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    rows = conn.execute(
        f"SELECT * FROM prompts {where} "
        "ORDER BY use_count DESC, (last_used_at IS NULL) ASC, "
        "last_used_at DESC, updated_at DESC",
        params,
    ).fetchall()
    return [_row_to_prompt(r) for r in rows]


def update_prompt(conn, prompt_id=None, title=None, body=None, category=_UNSET,
                  variables=None, **kwargs) -> Optional[Prompt]:
    """Update a prompt, preserving its id and bumping ``updated_at``.

    Only fields explicitly given are changed. If ``body`` changes, its
    variables are re-extracted (and a malformed placeholder is rejected
    without modifying the stored prompt) unless ``variables`` is given.
    Returns the updated prompt, or ``None`` if the id does not exist.
    """
    if prompt_id is None:
        prompt_id = kwargs.get("id") or kwargs.get("prompt_id")
    if category is _UNSET and "category" in kwargs:
        category = kwargs["category"]
    existing = get_prompt(conn, prompt_id)
    if existing is None:
        return None

    new_title = existing.title
    if title is not None:
        if not isinstance(title, str) or not title.strip():
            raise ValueError("title must be a non-empty string")
        new_title = title.strip()

    new_body = existing.body
    if body is not None:
        if not isinstance(body, str) or not body:
            raise ValueError("body must be a non-empty string")
        # Validate before writing anything.
        extract_variables(body)
        new_body = body

    if variables is not None:
        new_variables = _validate_variables_arg(variables)
    elif body is not None:
        new_variables = extract_variables(new_body)
    else:
        new_variables = list(existing.variables)

    new_category = existing.category if category is _UNSET else category
    if new_category is not None and not isinstance(new_category, str):
        raise ValueError("category must be a string or None")

    # Guarantee the modified timestamp actually moves forward.
    now = max(time.time(), (existing.updated_at or 0.0) + 0.001)
    conn.execute(
        """
        UPDATE prompts
           SET title = ?, body = ?, category = ?, variables_json = ?, updated_at = ?
         WHERE id = ?
        """,
        (new_title, new_body, new_category, json.dumps(new_variables), now, prompt_id),
    )
    conn.commit()
    return get_prompt(conn, prompt_id)


def delete_prompt(conn, prompt_id=None, **kwargs) -> bool:
    """Delete a prompt. Returns ``True`` if a row was removed."""
    if prompt_id is None:
        prompt_id = kwargs.get("id") or kwargs.get("prompt_id")
    _ensure_table(conn)
    cur = conn.execute("DELETE FROM prompts WHERE id = ?", (prompt_id,))
    conn.commit()
    return cur.rowcount > 0


def _is_conn(obj) -> bool:
    return hasattr(obj, "execute") and hasattr(obj, "commit")


def render_prompt(*args, conn=None, values=None, prompt=None, **kwargs) -> RenderResult:
    """Render a prompt by substituting its ``{{var}}`` placeholders.

    Accepts ``render_prompt(prompt, values)``,
    ``render_prompt(prompt, values, conn)`` or
    ``render_prompt(conn, prompt, values)``.

    Returns a :class:`RenderResult` with the rendered text and ``missing`` —
    the variables that had no value supplied (in first-appearance order,
    deduplicated). Missing placeholders are left intact in the text and are
    reported, never guessed. On a fully successful render (no missing
    variables) the prompt's ``use_count`` is incremented and
    ``last_used_at`` is set — in the database when ``conn`` is given, and on
    the prompt object itself either way.
    """
    # Flexible positional parsing.
    if kwargs.get("values") is not None and values is None:
        values = kwargs.pop("values")
    if kwargs.get("prompt") is not None and prompt is None:
        prompt = kwargs.pop("prompt")
    if kwargs.get("conn") is not None and conn is None:
        conn = kwargs.pop("conn")

    positional = list(args)
    if positional:
        if _is_conn(positional[0]):
            # (conn, prompt, values)
            conn = positional.pop(0)
            if positional:
                prompt = positional.pop(0)
            if positional:
                values = positional.pop(0)
        else:
            if prompt is None and positional:
                prompt = positional.pop(0)
            if values is None and positional:
                values = positional.pop(0)
            if conn is None and positional and _is_conn(positional[0]):
                conn = positional.pop(0)

    if prompt is None:
        raise ValueError("render_prompt requires a prompt")
    if values is None:
        values = {}
    if not isinstance(values, dict):
        raise ValueError("values must be a dict of variable names to strings")

    body = prompt.body
    # Raises if the (already-saved) body is somehow malformed.
    variables = extract_variables(body)
    missing = [
        name for name in variables
        if name not in values or values[name] is None
    ]

    def _sub(match):
        name = match.group(1)
        if name in values and values[name] is not None:
            return str(values[name])
        return match.group(0)  # leave the placeholder; it is reported in missing

    text = _PLACEHOLDER_RE.sub(_sub, body)

    if not missing:
        now = time.time()
        # Reflect usage on the in-memory object either way.
        try:
            prompt.use_count = int(getattr(prompt, "use_count", 0) or 0) + 1
            prompt.last_used_at = now
        except Exception:
            pass
        if conn is not None and getattr(prompt, "id", None):
            try:
                _ensure_table(conn)
                conn.execute(
                    "UPDATE prompts SET use_count = use_count + 1, "
                    "last_used_at = ? WHERE id = ?",
                    (now, prompt.id),
                )
                conn.commit()
            except Exception:
                # Rendering must not fail because usage persistence did; the
                # in-memory prompt already reflects the use.
                try:
                    conn.rollback()
                except Exception:
                    pass
    return RenderResult(text=text, missing=missing)
