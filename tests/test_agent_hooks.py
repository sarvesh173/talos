"""Tests for server.agent_hooks — all filesystem writes go to tmp_path."""

import json
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import server.agent_hooks as hooks


def _no_telegram(monkeypatch):
    monkeypatch.delenv("TALOS_TELEGRAM_BOT_TOKEN", raising=False)
    monkeypatch.delenv("TALOS_TELEGRAM_CHAT_ID", raising=False)


def test_notify_writes_one_jsonl_line_and_never_leaks_content(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    events = tmp_path / "events.jsonl"
    secret_summary = "SECRET_SUMMARY_TOKEN=do-not-persist"
    payload = hooks.notify_completion(
        task_id="t-1",
        goal="g" * 500,
        status="completed",
        summary=secret_summary,
        duration_s=12.5,
        artifacts=[{"path": "/tmp/out.txt", "content": "SECRET_FILE_BODY"},
                   "/tmp/report.md"],
        events_path=events,
    )
    lines = events.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 1
    record = json.loads(lines[0])
    assert record["task_id"] == "t-1"
    assert record["goal"] == "g" * 200  # truncated to 200 chars
    assert record["status"] == "completed"
    assert record["duration_s"] == 12.5
    assert record["artifacts"] == ["/tmp/out.txt", "/tmp/report.md"]  # paths only
    assert record["telegram"] == "skipped"
    assert payload["telegram"] == "skipped"
    raw = events.read_text(encoding="utf-8")
    assert "do-not-persist" not in raw          # summary text never persisted
    assert "SECRET_FILE_BODY" not in raw        # artifact content never persisted
    assert record["summary_chars"] == len(secret_summary)


def test_rotation_at_size_cap(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    events = tmp_path / "events.jsonl"
    events.write_text("x" * 500 + "\n", encoding="utf-8")
    monkeypatch.setattr(hooks, "MAX_EVENTS_BYTES", 200)
    hooks.notify_completion("t-2", "goal", "failed", events_path=events)
    rotated = tmp_path / "events.jsonl.1"
    assert rotated.exists()
    assert rotated.stat().st_size >= 200
    lines = events.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 1
    assert json.loads(lines[0])["task_id"] == "t-2"
    assert events.stat().st_size < 200


def test_recent_events_reads_back_newest_first_and_skips_malformed(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    events = tmp_path / "events.jsonl"
    for i in range(3):
        hooks.notify_completion(f"t-{i}", f"goal {i}", "completed", events_path=events)
    events.write_text(events.read_text() + "{not json\n", encoding="utf-8")
    recent = hooks.recent_events(2, events_path=events)
    assert [r["task_id"] for r in recent] == ["t-2", "t-1"]


def test_recent_events_missing_file_is_empty(tmp_path):
    assert hooks.recent_events(events_path=tmp_path / "nope.jsonl") == []


def test_room_bus_emission_with_fake_bus(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    events = tmp_path / "events.jsonl"
    fake_bus = MagicMock()
    fake_bus.broadcast.return_value = None  # sync bus: no awaitable
    payload = hooks.notify_completion(
        "t-3", "goal", "completed", events_path=events, bus=fake_bus
    )
    assert payload["room_bus"] == "emitted"
    fake_bus.broadcast.assert_called_once()
    emitted = fake_bus.broadcast.call_args[0][0]
    # Either the raw payload or a BusEvent carrying the same payload.
    emitted_payload = getattr(emitted, "payload", emitted)
    assert emitted_payload["task_id"] == "t-3"


def test_missing_bus_never_breaks_notification(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    payload = hooks.notify_completion(
        "t-4", "goal", "completed", events_path=tmp_path / "e.jsonl", bus=object()
    )
    assert payload["task_id"] == "t-4"
    assert payload["room_bus"] == "failed"  # no broadcast attr -> swallowed
    assert (tmp_path / "e.jsonl").exists()


def test_telegram_skipped_silently_when_env_missing(tmp_path, monkeypatch):
    _no_telegram(monkeypatch)
    with patch("server.agent_hooks.urllib.request.urlopen") as mock_open:
        payload = hooks.notify_completion(
            "t-5", "goal", "completed", events_path=tmp_path / "e.jsonl"
        )
    mock_open.assert_not_called()
    assert payload["telegram"] == "skipped"


def test_telegram_sent_when_env_present(tmp_path, monkeypatch):
    monkeypatch.setenv("TALOS_TELEGRAM_BOT_TOKEN", "fake-token")
    monkeypatch.setenv("TALOS_TELEGRAM_CHAT_ID", "42")
    fake_response = MagicMock(status=200)
    fake_response.__enter__ = MagicMock(return_value=fake_response)
    fake_response.__exit__ = MagicMock(return_value=False)
    with patch("server.agent_hooks.urllib.request.urlopen", return_value=fake_response) as mock_open:
        payload = hooks.notify_completion(
            "t-6", "goal", "completed", events_path=tmp_path / "e.jsonl"
        )
    mock_open.assert_called_once()
    assert payload["telegram"] == "sent"
    # Token goes in the URL path (Bot API), never in the message text/body log.
    assert "fake-token" not in (tmp_path / "e.jsonl").read_text(encoding="utf-8")
