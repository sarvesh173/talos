"""Prompt library tests: raw storage, render-time substitution, missing
variables reported (never guessed), usage counting, filtering, round-trip.

The library takes an explicit sqlite connection, so tests use a fresh
in-memory DB per test — no shared file, no seeded content.
"""

import sqlite3
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest

from server.prompt_library import (
    create_prompt,
    delete_prompt,
    extract_variables,
    get_prompt,
    list_prompts,
    render_prompt,
    update_prompt,
)


@pytest.fixture()
def conn():
    c = sqlite3.connect(":memory:")
    c.row_factory = sqlite3.Row
    yield c
    c.close()


def test_empty_library_is_correct(conn):
    assert list_prompts(conn) == []


def test_create_with_variables_stores_body_intact(conn):
    p = create_prompt(conn, "Explain", "Explain {{topic}} in simple terms", category="study")
    assert p.id
    assert p.body == "Explain {{topic}} in simple terms"
    assert p.variables == ["topic"]
    assert p.category == "study"
    assert p.use_count == 0
    # Round-trip from the DB: placeholders untouched by save.
    fetched = get_prompt(conn, p.id)
    assert fetched.body == "Explain {{topic}} in simple terms"
    assert fetched.variables == ["topic"]


def test_extract_variables_dedup_first_appearance_order():
    assert extract_variables("{{b}} then {{a}} then {{b}} again") == ["b", "a"]
    assert extract_variables("no placeholders") == []
    assert extract_variables('JSON {"a":1} is not a placeholder') == []


def test_render_with_all_values(conn):
    p = create_prompt(conn, "Explain", "Explain {{topic}} for {{audience}}")
    result = render_prompt(p, {"topic": "photosynthesis", "audience": "kids"})
    assert result.text == "Explain photosynthesis for kids"
    assert result.missing == []


def test_render_missing_reports_and_does_not_fabricate(conn):
    p = create_prompt(conn, "Explain", "Explain {{topic}} simply")
    result = render_prompt(p, {})
    assert result.missing == ["topic"]
    assert result.missing_variables == ["topic"]
    # Nothing invented: the placeholder is left for the UI to prompt about.
    assert "{{topic}}" in result.text
    assert "photosynthesis" not in result.text


def test_render_partial_missing_reports_only_missing(conn):
    p = create_prompt(conn, "T", "Explain {{topic}} for {{audience}}")
    result = render_prompt(p, {"topic": "gravity"})
    assert result.missing == ["audience"]
    assert "gravity" in result.text


def test_repeated_variable_renders_both(conn):
    p = create_prompt(conn, "T", "Tell me about {{topic}}. Why does {{topic}} matter?")
    result = render_prompt(p, {"topic": "evolution"})
    assert result.text == "Tell me about evolution. Why does evolution matter?"
    assert result.missing == []


def test_malformed_placeholder_rejected_on_create(conn):
    with pytest.raises(ValueError, match="Malformed placeholder"):
        create_prompt(conn, "Bad", "Explain {{topic")
    # Nothing was stored.
    assert list_prompts(conn) == []


def test_malformed_placeholder_rejected_on_update(conn):
    p = create_prompt(conn, "Good", "Explain {{topic}}")
    with pytest.raises(ValueError, match="Malformed placeholder"):
        update_prompt(conn, p.id, body="Explain {{topic")
    # Stored prompt unchanged.
    assert get_prompt(conn, p.id).body == "Explain {{topic}}"


def test_update_preserves_id_and_bumps_modified_timestamp(conn):
    p = create_prompt(conn, "Old", "Explain {{topic}}")
    updated = update_prompt(conn, p.id, title="New", body="Describe {{topic}} briefly")
    assert updated.id == p.id
    assert updated.title == "New"
    assert updated.body == "Describe {{topic}} briefly"
    assert updated.variables == ["topic"]
    assert updated.updated_at > p.updated_at
    assert updated.created_at == p.created_at


def test_delete_removes_and_get_returns_none(conn):
    p = create_prompt(conn, "T", "Hello {{name}}")
    assert delete_prompt(conn, p.id) is True
    assert get_prompt(conn, p.id) is None
    assert delete_prompt(conn, p.id) is False


def test_get_unknown_returns_none(conn):
    assert get_prompt(conn, "does-not-exist") is None


def test_usage_counter_increments_only_on_successful_render(conn):
    p = create_prompt(conn, "T", "Explain {{topic}}")
    assert p.use_count == 0
    assert p.last_used_at is None

    # Missing value: no increment, no timestamp.
    render_prompt(p, {}, conn=conn)
    after_missing = get_prompt(conn, p.id)
    assert after_missing.use_count == 0
    assert after_missing.last_used_at is None
    assert p.use_count == 0

    # Successful renders increment.
    render_prompt(p, {"topic": "gravity"}, conn=conn)
    render_prompt(p, {"topic": "gravity"}, conn=conn)
    after = get_prompt(conn, p.id)
    assert after.use_count == 2
    assert after.last_used_at is not None
    assert p.use_count == 2


def test_list_filters_by_category(conn):
    create_prompt(conn, "A", "Body {{x}}", category="writing")
    create_prompt(conn, "B", "Body {{x}}", category="study")
    create_prompt(conn, "C", "Body {{x}}")
    writing = list_prompts(conn, category="writing")
    assert [p.title for p in writing] == ["A"]
    assert len(list_prompts(conn)) == 3


def test_list_filters_by_substring_query(conn):
    create_prompt(conn, "Photosynthesis explainer", "Explain {{topic}}")
    create_prompt(conn, "Email draft", "Write about {{topic}}")
    hits = list_prompts(conn, query="photo")
    assert [p.title for p in hits] == ["Photosynthesis explainer"]
    # Query also matches body text, case-insensitively.
    assert len(list_prompts(conn, query="WRITE ABOUT")) == 1
    assert list_prompts(conn, query="nonexistent") == []


def test_list_sorts_by_use_count(conn):
    a = create_prompt(conn, "A", "Body {{x}}")
    b = create_prompt(conn, "B", "Body {{x}}")
    render_prompt(b, {"x": "1"}, conn=conn)
    assert [p.title for p in list_prompts(conn)] == ["B", "A"]


def test_json_body_round_trip_untouched(conn):
    body = 'Summarize this JSON: {"a":1} with {{topic}}'
    p = create_prompt(conn, "JSON", body)
    fetched = get_prompt(conn, p.id)
    assert fetched.body == body
    assert fetched.variables == ["topic"]
    result = render_prompt(fetched, {"topic": "data"})
    assert result.text == 'Summarize this JSON: {"a":1} with data'
    # Pure JSON with no placeholders renders identically.
    plain = create_prompt(conn, "Plain", '{"a":1}')
    assert plain.variables == []
    assert render_prompt(plain, {}).text == '{"a":1}'


def test_explicit_variables_arg(conn):
    p = create_prompt(conn, "T", "Hi {{name}}", variables=["name"])
    assert p.variables == ["name"]
    with pytest.raises(ValueError):
        create_prompt(conn, "Bad", "Hi {{name}}", variables=["not a name"])
