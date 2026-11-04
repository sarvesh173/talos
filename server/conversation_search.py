"""Full-text search over TALOS conversation messages.

SQLite FTS5 (with a LIKE fallback) over the ``messages`` table in
``server/db.py``.  Call :func:`ensure_index` at startup -- it is
idempotent -- then :func:`search` for hits and :func:`index_status`
to tell the UI which backend is really active.

Tokenizer choice: ``porter unicode61`` (see :data:`TOKENIZER`).
``unicode61`` tokenizes by Unicode character categories, so
Devanagari/Hindi text (e.g. बिल्कुल) is kept as searchable tokens
rather than being split or dropped by an ASCII-only tokenizer.
Wrapping it in ``porter`` adds English stem matching on top
(``run`` matches ``running``) without breaking those non-Latin
tokens -- verified against both Hindi and English-stem queries.
Plain ``unicode61`` alone would give up stemming; ``porter``
alone is the wrong default for a multilingual corpus.

Query handling: user text is never passed to FTS5 raw.  The
characters ``" * ( ) :`` are stripped, the remainder is split
into terms, and each term is sent as a quoted FTS5 string, so
reserved words such as ``USE``/``AND``/``NOT`` are searched
literally instead of raising a syntax error.
"""

from __future__ import annotations

import re
import sqlite3
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Sequence, Tuple

__all__ = [
    "TOKENIZER",
    "FTS_TABLE",
    "SNIPPET_LEN",
    "SearchHit",
    "SearchResponse",
    "ensure_index",
    "search",
    "index_status",
]

TOKENIZER = "porter unicode61"
FTS_TABLE = "messages_fts"
SNIPPET_LEN = 160

#: Characters that are FTS5 syntax / would make a literal user query
#: throw or change meaning.  They are removed before term splitting.
_FTS_SYNTAX_CHARS = '"*():'
_FTS_SYNTAX_RE = re.compile(r'["*():]')

_CREATE_FTS = f"""
CREATE VIRTUAL TABLE IF NOT EXISTS {FTS_TABLE} USING fts5(
    content,
    message_id UNINDEXED,
    room_id UNINDEXED,
    tokenize='{TOKENIZER}'
)
"""

_TRIGGERS = (
    f"""
CREATE TRIGGER IF NOT EXISTS {FTS_TABLE}_ai
AFTER INSERT ON messages BEGIN
    INSERT INTO {FTS_TABLE}(content, message_id, room_id)
    VALUES (NEW.content, NEW.id, NEW.room_id);
END
""",
    f"""
CREATE TRIGGER IF NOT EXISTS {FTS_TABLE}_ad
AFTER DELETE ON messages BEGIN
    DELETE FROM {FTS_TABLE} WHERE message_id = OLD.id;
END
""",
    f"""
CREATE TRIGGER IF NOT EXISTS {FTS_TABLE}_au
AFTER UPDATE ON messages BEGIN
    DELETE FROM {FTS_TABLE} WHERE message_id = OLD.id;
    INSERT INTO {FTS_TABLE}(content, message_id, room_id)
    VALUES (NEW.content, NEW.id, NEW.room_id);
END
""",
)


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def _table_exists(conn: sqlite3.Connection, name: str) -> bool:
    try:
        row = conn.execute(
            "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name = ?",
            (name,),
        ).fetchone()
        return row is not None
    except sqlite3.Error:
        return False


def _fts5_available(conn: sqlite3.Connection) -> bool:
    """Probe FTS5 support without touching the real schema."""
    try:
        conn.execute("CREATE VIRTUAL TABLE temp.__fts5_probe USING fts5(x)")
        conn.execute("DROP TABLE temp.__fts5_probe")
        return True
    except sqlite3.Error:
        try:
            conn.execute("DROP TABLE IF EXISTS temp.__fts5_probe")
        except sqlite3.Error:
            pass
        return False


def _rows_to_dicts(cur: sqlite3.Cursor) -> List[Dict[str, Any]]:
    cols = [d[0] for d in cur.description] if cur.description else []
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def _count(conn: sqlite3.Connection, sql: str, params: Sequence[Any] = ()) -> int:
    try:
        row = conn.execute(sql, tuple(params)).fetchone()
        return int(row[0]) if row and row[0] is not None else 0
    except sqlite3.Error:
        return 0


def sanitize_terms(query: Any) -> List[str]:
    """Strip FTS5 syntax characters and split *query* into literal terms."""
    if query is None:
        return []
    text = _FTS_SYNTAX_RE.sub(" ", str(query))
    return [t for t in text.split() if t]


def _needles(terms: Sequence[str]) -> List[str]:
    """Search strings used for snippet/offset scanning.

    Includes each term as-is. Sub-token splitting is intentionally NOT
    done: Devanagari and other combining-mark scripts carry their
    meaning in matras (vowel signs) that Python's ``\w`` class does not
    cover, so splitting ``बिल्कुल`` into ``[ब, ल्क, ल]`` produced
    nonsense matches (``बि`` matched inside ``बिल्कुल`` and single
    letters matched everywhere). hyphenated Latin queries still work,
    because the full term is always included.
    """
    out: List[str] = []
    for term in terms:
        if term not in out:
            out.append(term)
    out.sort(key=len, reverse=True)
    return out


def _find_all(haystack_cf: str, needle_cf: str) -> List[Tuple[int, int]]:
    spans: List[Tuple[int, int]] = []
    if not needle_cf:
        return spans
    start = 0
    while True:
        idx = haystack_cf.find(needle_cf, start)
        if idx < 0:
            break
        spans.append((idx, idx + len(needle_cf)))
        start = idx + len(needle_cf)
    return spans


def _merge_spans(spans: List[Tuple[int, int]]) -> List[List[int]]:
    spans = sorted(spans)
    merged: List[List[int]] = []
    for s, e in spans:
        if merged and s < merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], e)
        else:
            merged.append([s, e])
    return merged


def make_snippet(content: str, terms: Sequence[str], length: int = SNIPPET_LEN):
    """Return ``(snippet, [[start, end], ...])``.

    The snippet is at most *length* characters, centred on the first
    match; offsets are character offsets of *all* matches inside the
    returned snippet.
    """
    content = content or ""
    needles = _needles(terms)
    content_cf = content.casefold()

    first: Optional[int] = None
    for needle in needles:
        idx = content_cf.find(needle.casefold())
        if idx >= 0 and (first is None or idx < first):
            first = idx

    if first is None or not content:
        start, end = 0, min(len(content), length)
    else:
        start = max(0, first - max(0, (length // 2)))
        end = start + length
        if end > len(content):
            end = len(content)
            start = max(0, end - length)
    snippet = content[start:end]

    spans: List[Tuple[int, int]] = []
    snippet_cf = snippet.casefold()
    for needle in needles:
        for s, e in _find_all(snippet_cf, needle.casefold()):
            spans.append((s, e))
    return snippet, _merge_spans(spans)


# ---------------------------------------------------------------------------
# result types (dict-friendly for direct use in JSON responses)
# ---------------------------------------------------------------------------

@dataclass
class SearchHit:
    room_id: str = ""
    room_title: str = ""
    message_id: str = ""
    sender_id: str = ""
    sender_name: str = ""
    sender_role: str = ""
    snippet: str = ""
    matches: List[List[int]] = field(default_factory=list)
    created_at: Optional[float] = None
    rank: Optional[float] = None

    # --- aliases / dict-style access -------------------------------------
    @property
    def room_name(self) -> str:
        return self.room_title

    @property
    def agent_id(self) -> str:
        return self.sender_id

    @property
    def agent_name(self) -> str:
        return self.sender_name

    @property
    def offsets(self) -> List[List[int]]:
        return self.matches

    @property
    def match_offsets(self) -> List[List[int]]:
        return self.matches

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["room_name"] = self.room_title
        d["agent_id"] = self.sender_id
        d["agent_name"] = self.sender_name
        d["offsets"] = self.matches
        d["match_offsets"] = self.matches
        return d

    def __getitem__(self, key: str) -> Any:
        d = self.to_dict()
        if key in d:
            return d[key]
        raise KeyError(key)

    def get(self, key: str, default: Any = None) -> Any:
        return self.to_dict().get(key, default)

    def keys(self):
        return self.to_dict().keys()


@dataclass
class SearchResponse:
    query: str = ""
    hits: List[SearchHit] = field(default_factory=list)
    total: int = 0
    limit: int = 20
    offset: int = 0
    backend: str = "fts5"

    @property
    def results(self) -> List[SearchHit]:
        return self.hits

    def to_dict(self) -> Dict[str, Any]:
        return {
            "query": self.query,
            "hits": [h.to_dict() for h in self.hits],
            "results": [h.to_dict() for h in self.hits],
            "total": self.total,
            "limit": self.limit,
            "offset": self.offset,
            "backend": self.backend,
        }

    def __getitem__(self, key: str) -> Any:
        return self.to_dict()[key]

    def get(self, key: str, default: Any = None) -> Any:
        return self.to_dict().get(key, default)

    def __iter__(self):
        return iter(self.hits)

    def __len__(self) -> int:
        return len(self.hits)


# ---------------------------------------------------------------------------
# index management
# ---------------------------------------------------------------------------

def ensure_index(conn: sqlite3.Connection) -> Dict[str, Any]:
    """Create the FTS5 index (idempotent) and keep it in sync.

    Safe to call on every startup.  If the SQLite build has no FTS5
    module the failure is *not* raised: the function simply leaves
    no FTS table behind and :func:`index_status` / :func:`search`
    report the LIKE fallback honestly.
    """
    if not _table_exists(conn, "messages"):
        # Nothing to index yet; still report status without raising.
        return index_status(conn)

    try:
        conn.execute(_CREATE_FTS)
        for stmt in _TRIGGERS:
            conn.execute(stmt)
        # Reconcile: drop orphans (e.g. rows deleted before triggers
        # existed) and backfill messages that are not indexed yet.
        # The NOT IN guard is what makes a second call a no-op --
        # no duplicate FTS rows.
        conn.execute(
            f"DELETE FROM {FTS_TABLE} WHERE message_id NOT IN (SELECT id FROM messages)"
        )
        conn.execute(
            f"INSERT INTO {FTS_TABLE}(content, message_id, room_id) "
            f"SELECT content, id, room_id FROM messages "
            f"WHERE id NOT IN (SELECT message_id FROM {FTS_TABLE})"
        )
        conn.commit()
    except sqlite3.OperationalError:
        # No FTS5 module in this build -> LIKE fallback in search().
        try:
            conn.rollback()
        except sqlite3.Error:
            pass
    return index_status(conn)


def index_status(conn: sqlite3.Connection) -> Dict[str, Any]:
    """Report the active backend and how many rows are indexed.

    ``backend`` is ``"fts5"`` only when the FTS table actually
    exists; otherwise ``"like"`` (the LIKE fallback).  This is the
    value the app should show so a degraded build is never
    presented as full-text search.
    """
    fts_present = _table_exists(conn, FTS_TABLE)
    backend = "fts5" if fts_present else "like"
    indexed = _count(conn, f"SELECT COUNT(*) FROM {FTS_TABLE}") if fts_present else 0
    total_messages = (
        _count(conn, "SELECT COUNT(*) FROM messages")
        if _table_exists(conn, "messages")
        else 0
    )
    available = _fts5_available(conn) if not fts_present else True
    return {
        "backend": backend,
        "active_backend": backend,
        "fts5": backend == "fts5",
        "fts5_available": bool(available),
        "fallback": backend != "fts5",
        "degraded": backend != "fts5",
        "tokenizer": TOKENIZER if backend == "fts5" else None,
        "indexed_rows": indexed,
        "indexed_row_count": indexed,
        "total_messages": total_messages,
    }


# ---------------------------------------------------------------------------
# search
# ---------------------------------------------------------------------------

def _fts_query(terms: Sequence[str]) -> str:
    # Each term as a quoted literal, OR-ed: "foo" OR "bar".
    # Quoting makes USE/AND/NOT/NEAR literal rather than operators.
    return " OR ".join('"' + t.replace('"', '""') + '"' for t in terms)


def _hit_from_row(row: Dict[str, Any], terms: Sequence[str], rank=None) -> SearchHit:
    snippet, spans = make_snippet(row.get("content") or "", terms)
    return SearchHit(
        room_id=row.get("room_id") or "",
        room_title=row.get("room_title") or row.get("room_id") or "",
        message_id=row.get("id") or row.get("message_id") or "",
        sender_id=row.get("sender_id") or "",
        sender_name=row.get("sender_name") or "",
        sender_role=row.get("sender_role") or "",
        snippet=snippet,
        matches=spans,
        created_at=row.get("created_at"),
        rank=rank,
    )


def _search_fts(conn, terms, room_id, limit, offset, query_text):
    join_rooms = _table_exists(conn, "rooms")
    room_title_expr = "r.name" if join_rooms else "m.room_id"
    join_sql = "LEFT JOIN rooms r ON r.id = m.room_id" if join_rooms else ""
    match = _fts_query(terms)
    where = [f"{FTS_TABLE} MATCH ?"]
    params: List[Any] = [match]
    if room_id is not None:
        where.append("m.room_id = ?")
        params.append(room_id)
    where_sql = " AND ".join(where)

    total = _count(
        conn,
        f"SELECT COUNT(*) FROM {FTS_TABLE} f "
        f"JOIN messages m ON m.id = f.message_id {join_sql} WHERE {where_sql}",
        params,
    )
    cur = conn.execute(
        f"SELECT m.id, m.room_id, m.sender_id, m.sender_name, m.sender_role, "
        f"m.content, m.created_at, {room_title_expr} AS room_title, "
        f"bm25({FTS_TABLE}) AS rank "
        f"FROM {FTS_TABLE} f JOIN messages m ON m.id = f.message_id {join_sql} "
        f"WHERE {where_sql} "
        f"ORDER BY bm25({FTS_TABLE}) ASC, m.created_at DESC "
        f"LIMIT ? OFFSET ?",
        (*params, limit, offset),
    )
    hits = [_hit_from_row(r, terms, rank=r.get("rank")) for r in _rows_to_dicts(cur)]
    return SearchResponse(
        query=query_text, hits=hits, total=total, limit=limit, offset=offset, backend="fts5"
    )


def _search_like(conn, terms, room_id, limit, offset, query_text):
    join_rooms = _table_exists(conn, "rooms")
    room_title_expr = "r.name" if join_rooms else "m.room_id"
    join_sql = "LEFT JOIN rooms r ON r.id = m.room_id" if join_rooms else ""

    def esc(term: str) -> str:
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    clauses = " OR ".join("m.content LIKE ? ESCAPE '\\'" for _ in terms)
    params: List[Any] = [esc(t) for t in terms]
    where = f"({clauses})"
    if room_id is not None:
        where += " AND m.room_id = ?"
        params.append(room_id)

    cur = conn.execute(
        f"SELECT m.id, m.room_id, m.sender_id, m.sender_name, m.sender_role, "
        f"m.content, m.created_at, {room_title_expr} AS room_title "
        f"FROM messages m {join_sql} WHERE {where} ORDER BY m.created_at DESC",
        params,
    )
    rows = _rows_to_dicts(cur)
    # Relevance for the fallback: number of (non-overlapping, casefold)
    # term occurrences, then recency.
    needles = [n.casefold() for n in _needles(terms)]

    def score(row) -> int:
        hay = (row.get("content") or "").casefold()
        return sum(hay.count(n) for n in needles if n)

    rows.sort(key=lambda r: (score(r), r.get("created_at") or 0), reverse=True)
    total = len(rows)
    page = rows[offset : offset + limit]
    hits = [_hit_from_row(r, terms, rank=None) for r in page]
    return SearchResponse(
        query=query_text, hits=hits, total=total, limit=limit, offset=offset, backend="like"
    )


def search(
    conn: sqlite3.Connection,
    query: Any,
    room_id: Optional[str] = None,
    limit: int = 20,
    offset: int = 0,
) -> SearchResponse:
    """Full-text search across every message.

    Returns a :class:`SearchResponse` whose hits carry the room,
    message, agent attribution, a ~160-character snippet and the
    character offsets of all matches inside that snippet.
    Empty/whitespace (or syntax-only) queries return no results.
    """
    query_text = "" if query is None else str(query)
    terms = sanitize_terms(query_text)
    try:
        limit = int(limit)
    except (TypeError, ValueError):
        limit = 20
    try:
        offset = int(offset)
    except (TypeError, ValueError):
        offset = 0
    limit = max(0, limit)
    offset = max(0, offset)

    if not terms:
        backend = "fts5" if _table_exists(conn, FTS_TABLE) else "like"
        return SearchResponse(
            query=query_text, hits=[], total=0, limit=limit, offset=offset, backend=backend
        )

    # Ensure the index exists (and is backfilled) so search never
    # silently misses messages inserted before the first ensure.
    ensure_index(conn)
    if _table_exists(conn, FTS_TABLE):
        try:
            return _search_fts(conn, terms, room_id, limit, offset, query_text)
        except sqlite3.OperationalError:
            pass  # fall through to LIKE, reported via backend="like"
    return _search_like(conn, terms, room_id, limit, offset, query_text)
