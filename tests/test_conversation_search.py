"""Conversation search tests.

Temp DB (schema mirrored from server/db.py) with tricky content: FTS5
reserved words, a quote character, Hindi/unicode text, a very long
message, and duplicate content across two rooms.  The properties that
matter: literal queries never raise, snippets carry correct offsets,
and the reported backend is the real one.
"""

import sqlite3
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.conversation_search import ensure_index, index_status, search

SCHEMA = """
CREATE TABLE rooms (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT DEFAULT '',
    mode TEXT NOT NULL, participants_json TEXT DEFAULT '[]',
    agent_ids_json TEXT DEFAULT '[]', created_at REAL, updated_at REAL
);
CREATE TABLE messages (
    id TEXT PRIMARY KEY, room_id TEXT NOT NULL, sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL, sender_role TEXT NOT NULL, content TEXT NOT NULL,
    thought TEXT, tool_calls_json TEXT DEFAULT '[]', attachments_json TEXT DEFAULT '[]',
    reply_to_id TEXT, created_at REAL
);
"""

LONG_FILLER = ("filler text here. " * 40)


def _make_db():
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    conn.executemany(
        "INSERT INTO rooms (id, name, description, mode, created_at, updated_at) "
        "VALUES (?, ?, '', 'group', 1, 1)",
        [("room-a", "Alpha Room"), ("room-b", "Beta Room")],
    )
    messages = [
        # FTS5 reserved words as literal content
        ("m1", "room-a", "hermes", "Hermes", "agent",
         "We should USE the tool AND check NOT NULL OR run it NEAR the cache", 100.0),
        # quote / apostrophe
        ("m2", "room-a", "claude", "Claude", "agent",
         'She said "hello world" and it\'s done', 101.0),
        # unicode / Hindi
        ("m3", "room-a", "user", "User", "human",
         "बिल्कुल सही — यह हिंदी खोज परीक्षण है", 102.0),
        # very long message, needle in the middle
        ("m4", "room-a", "hermes", "Hermes", "agent",
         LONG_FILLER + " needleXYZ " + LONG_FILLER, 103.0),
        # duplicate content across two rooms
        ("m5", "room-a", "codex", "Codex", "agent",
         "duplicate zebra content here", 104.0),
        ("m6", "room-b", "gemini", "Gemini", "agent",
         "duplicate zebra content here", 105.0),
        # repeated term for offset checks
        ("m7", "room-a", "hermes", "Hermes", "agent",
         "apple apple apple pie", 106.0),
    ]
    # paginated common-word messages, distinct timestamps
    for i in range(5):
        messages.append(
            (f"p{i}", "room-a", "hermes", "Hermes", "agent",
             f"commonword message number {i}", 1000.0 + i)
        )
    conn.executemany(
        "INSERT INTO messages (id, room_id, sender_id, sender_name, sender_role, "
        "content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        messages,
    )
    conn.commit()
    return conn


def _offsets_ok(hit, terms):
    for start, end in hit.matches:
        frag = hit.snippet[start:end]
        assert any(frag.casefold() == t.casefold() for t in terms), (frag, terms)


class ConversationSearchTest(unittest.TestCase):
    def setUp(self):
        self.conn = _make_db()
        ensure_index(self.conn)

    def tearDown(self):
        self.conn.close()

    def test_unicode_search_finds_hindi(self):
        resp = search(self.conn, "बिल्कुल")
        self.assertEqual(resp.total, 1)
        self.assertEqual(resp.hits[0].message_id, "m3")
        self.assertIn("बिल्कुल", resp.hits[0].snippet)
        _offsets_ok(resp.hits[0], ["बिल्कुल"])
        self.assertGreaterEqual(len(resp.hits[0].matches), 1)

    def test_reserved_words_do_not_raise(self):
        for word in ("USE", "AND", "NOT", "OR", "NEAR"):
            resp = search(self.conn, word)
            self.assertGreaterEqual(resp.total, 1, word)
            ids = [h.message_id for h in resp.hits]
            self.assertIn("m1", ids, word)

    def test_fts_syntax_characters_do_not_raise(self):
        for q in ('"hello', "hello*", "(hello)", "content:hello", '"USE" * (AND):'):
            resp = search(self.conn, q)  # must not raise
            self.assertIsInstance(resp.total, int)
        resp = search(self.conn, '"hello')
        self.assertIn("m2", [h.message_id for h in resp.hits])

    def test_snippet_offsets_are_correct_and_complete(self):
        resp = search(self.conn, "apple")
        self.assertEqual(resp.total, 1)
        hit = resp.hits[0]
        self.assertGreaterEqual(len(hit.matches), 3)
        for start, end in hit.matches:
            self.assertEqual(hit.snippet[start:end].casefold(), "apple")
        # dict-style access agrees
        self.assertEqual(hit["matches"], hit.matches)
        self.assertEqual(resp["total"], 1)

    def test_hit_carries_room_message_and_agent(self):
        resp = search(self.conn, "zebra")
        self.assertEqual(resp.total, 2)
        by_id = {h.message_id: h for h in resp.hits}
        self.assertEqual(by_id["m5"].room_id, "room-a")
        self.assertEqual(by_id["m5"].room_title, "Alpha Room")
        self.assertEqual(by_id["m6"].room_id, "room-b")
        self.assertEqual(by_id["m6"].room_title, "Beta Room")
        self.assertEqual(by_id["m5"].sender_name, "Codex")
        self.assertEqual(by_id["m6"].agent_name, "Gemini")
        self.assertEqual(by_id["m5"].agent_id, "codex")

    def test_room_filter(self):
        resp = search(self.conn, "zebra", room_id="room-b")
        self.assertEqual(resp.total, 1)
        self.assertEqual(resp.hits[0].message_id, "m6")
        self.assertEqual(resp.hits[0].room_id, "room-b")

    def test_limit_offset_paginate_by_recency(self):
        page1 = search(self.conn, "commonword", limit=2, offset=0)
        page2 = search(self.conn, "commonword", limit=2, offset=2)
        page3 = search(self.conn, "commonword", limit=2, offset=4)
        self.assertEqual(page1.total, 5)
        self.assertEqual([h.message_id for h in page1.hits], ["p4", "p3"])
        self.assertEqual([h.message_id for h in page2.hits], ["p2", "p1"])
        self.assertEqual([h.message_id for h in page3.hits], ["p0"])
        self.assertEqual(page1.results, page1.hits)

    def test_long_message_snippet_is_bounded_and_centred(self):
        resp = search(self.conn, "needleXYZ")
        self.assertEqual(resp.total, 1)
        hit = resp.hits[0]
        self.assertLessEqual(len(hit.snippet), 160)
        self.assertIn("needleXYZ", hit.snippet)
        for start, end in hit.matches:
            self.assertEqual(hit.snippet[start:end], "needleXYZ")

    def test_empty_query_returns_nothing(self):
        for q in ("", "   ", '"***():'):
            resp = search(self.conn, q)
            self.assertEqual(resp.total, 0, repr(q))
            self.assertEqual(resp.hits, [])

    def test_index_status_reports_real_backend(self):
        status = index_status(self.conn)
        self.assertIn(status["backend"], ("fts5", "like"))
        self.assertEqual(status["indexed_rows"], status["indexed_row_count"])
        self.assertEqual(status["total_messages"], 12)
        if status["backend"] == "fts5":
            self.assertTrue(status["fts5_available"])
            self.assertEqual(status["indexed_rows"], 12)
            self.assertFalse(status["fallback"])
        else:
            # Honest degradation: never claim FTS5 when on LIKE.
            self.assertTrue(status["fallback"])
            self.assertEqual(status["indexed_rows"], 0)

    def test_ensure_index_twice_does_not_duplicate(self):
        before = index_status(self.conn)
        ensure_index(self.conn)
        ensure_index(self.conn)
        after = index_status(self.conn)
        self.assertEqual(before["indexed_rows"], after["indexed_rows"])
        self.assertEqual(search(self.conn, "zebra").total, 2)
        self.assertEqual(search(self.conn, "commonword").total, 5)

    def test_deleted_message_disappears_from_results(self):
        self.assertEqual(search(self.conn, "needleXYZ").total, 1)
        self.conn.execute("DELETE FROM messages WHERE id = 'm4'")
        self.conn.commit()
        self.assertEqual(search(self.conn, "needleXYZ").total, 0)
        status = index_status(self.conn)
        self.assertEqual(status["total_messages"], 11)
        if status["backend"] == "fts5":
            self.assertEqual(status["indexed_rows"], 11)

    def test_insert_after_index_is_searchable_via_trigger(self):
        self.conn.execute(
            "INSERT INTO messages (id, room_id, sender_id, sender_name, sender_role, "
            "content, created_at) VALUES ('m99', 'room-b', 'hermes', 'Hermes', 'agent', "
            "'brand new quokka appears', 2000.0)"
        )
        self.conn.commit()
        resp = search(self.conn, "quokka")
        self.assertEqual(resp.total, 1)
        self.assertEqual(resp.hits[0].room_title, "Beta Room")


if __name__ == "__main__":
    unittest.main()
