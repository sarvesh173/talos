"""Unit tests for tools/check_dashboard.py parsing/classification logic.

Fixture-only: never requires the real dashboard to be running.
The live check is what the operator script is for.
"""

import importlib.util
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "check_dashboard.py"
_spec = importlib.util.spec_from_file_location("check_dashboard", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
sys.modules["check_dashboard"] = mod
_spec.loader.exec_module(mod)

HOST = "100.75.107.79"
PORT = 9119

# /proc/net/tcp fixtures. 100.75.107.79 -> 4F6B4B64, 9119 -> 239F.
PROC_LISTENING = """\
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0100007F:0FFA 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 19315 1 00000000f92202e3 100 0 0 10 0
   1: 4F6B4B64:239F 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5230637 1 00000000352dfd43 100 0 0 10 0
"""

PROC_NOT_LISTENING = """\
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0100007F:0FFA 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 19315 1 00000000f92202e3 100 0 0 10 0
   1: 4F6B4B64:2382 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5230637 1 00000000352dfd43 100 0 0 10 0
"""

# Same address/port but only in a non-LISTEN state (0B = CLOSE/WAIT etc.):
# must not count as listening.
PROC_NOT_IN_LISTEN_STATE = """\
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 4F6B4B64:239F 4F6B4B64:2382 06 00000000:00000000 00:00000000 00000000  1000        0 5230637 1 00000000352dfd43 100 0 0 10 0
"""


# --- /proc/net/tcp parsing -------------------------------------------------

def test_proc_hex_roundtrip():
    assert mod.ip_to_proc_hex(HOST) == "4F6B4B64"
    assert mod.proc_hex_to_ip("4F6B4B64") == HOST
    assert mod.proc_hex_to_ip(mod.ip_to_proc_hex("127.0.0.1")) == "127.0.0.1"


def test_listening_found_in_fixture():
    assert mod.parse_proc_net_tcp(PROC_LISTENING, HOST, PORT) is True


def test_not_listening_when_port_absent():
    assert mod.parse_proc_net_tcp(PROC_NOT_LISTENING, HOST, PORT) is False


def test_not_listening_when_wrong_state():
    assert mod.parse_proc_net_tcp(PROC_NOT_IN_LISTEN_STATE, HOST, PORT) is False


def test_not_listening_on_empty_input():
    assert mod.parse_proc_net_tcp("", HOST, PORT) is False


# --- HTTP response classifier ----------------------------------------------

@pytest.mark.parametrize("status,expected_ok", [
    (200, True),   # correct, subject to the auth check
    (302, True),   # redirect to login -- how a healthy dashboard answers
    (401, False),  # not 200/302
    (500, False),  # server error
    (404, False),
    (None, False),  # connection failure
])
def test_http_classifier(status, expected_ok):
    ok, _reason = mod.classify_http_status(status)
    assert ok is expected_ok


# --- Auth check -------------------------------------------------------------

def test_auth_fails_on_200_without_cookie():
    ok, reason = mod.evaluate_auth(200, has_session_cookie=False)
    assert ok is False
    assert "auth" in reason.lower()


def test_auth_passes_on_302_without_cookie():
    ok, _ = mod.evaluate_auth(302, has_session_cookie=False)
    assert ok is True


def test_auth_passes_on_200_with_session_cookie():
    ok, _ = mod.evaluate_auth(200, has_session_cookie=True)
    assert ok is True


def test_has_session_cookie_detection():
    assert mod.has_session_cookie([("Set-Cookie", "session=abc; Path=/")]) is True
    assert mod.has_session_cookie([("Set-Cookie", "other=abc")]) is False
    assert mod.has_session_cookie([]) is False
    assert mod.has_session_cookie(None) is False


# --- Bound-address / tailscale parsing -------------------------------------

def test_parse_bound_address_from_unit_text():
    unit = "ExecStart=/home/kira/.local/bin/hermes dashboard --host 100.75.107.79 --port 9119 --no-open"
    assert mod.parse_bound_address(unit) == (HOST, PORT)


def test_parse_tailscale_outputs():
    assert mod.parse_tailscale_ip_output("100.75.107.79\n") == HOST
    up, addr = mod.parse_ip_addr_output(
        "4: tailscale0: <POINTOPOINT,MULTICAST,NOARP,UP,LOWER_UP> mtu 1280 state UNKNOWN\n"
        "    inet 100.75.107.79/32 scope global tailscale0\n"
    )
    assert up is True and addr == HOST


# --- Exit code on failure ---------------------------------------------------

def _healthy_kwargs():
    return dict(
        unit_enabled=(True, "enabled"),
        unit_active=(True, "active"),
        listening=(True, "listening"),
        http=(302, "GET / -> 302"),
        cookie=False,
        tailscale=(True, "addr matches"),
    )


def test_exit_code_zero_when_all_checks_pass():
    checks = mod.build_checks(**_healthy_kwargs())
    assert mod.exit_code_for(checks) == 0


def test_exit_code_nonzero_when_a_check_fails():
    kwargs = _healthy_kwargs()
    kwargs["listening"] = (False, "not listening")
    checks = mod.build_checks(**kwargs)
    assert mod.exit_code_for(checks) == 1


def test_exit_code_nonzero_when_auth_bypassed():
    kwargs = _healthy_kwargs()
    kwargs["http"] = (200, "GET / -> 200")
    kwargs["cookie"] = False
    checks = mod.build_checks(**kwargs)
    # 200 passes (d) but must fail (e)
    assert checks[3]["ok"] is True
    assert checks[4]["ok"] is False
    assert mod.exit_code_for(checks) == 1


def test_main_returns_nonzero_when_a_check_fails(monkeypatch):
    monkeypatch.setattr(mod, "check_unit_enabled", lambda: (True, "enabled"))
    monkeypatch.setattr(mod, "check_unit_active", lambda: (True, "active"))
    monkeypatch.setattr(mod, "check_listening", lambda h, p: (False, "not listening"))
    monkeypatch.setattr(mod, "check_http", lambda h, p: (302, False, "GET / -> 302"))
    monkeypatch.setattr(mod, "check_tailscale", lambda h: (True, "addr matches"))
    monkeypatch.setattr(mod, "get_bound_address", lambda: (HOST, PORT))
    assert mod.main() == 1


def test_main_returns_zero_when_all_live_checks_pass(monkeypatch):
    monkeypatch.setattr(mod, "check_unit_enabled", lambda: (True, "enabled"))
    monkeypatch.setattr(mod, "check_unit_active", lambda: (True, "active"))
    monkeypatch.setattr(mod, "check_listening", lambda h, p: (True, "listening"))
    monkeypatch.setattr(mod, "check_http", lambda h, p: (302, False, "GET / -> 302"))
    monkeypatch.setattr(mod, "check_tailscale", lambda h: (True, "addr matches"))
    monkeypatch.setattr(mod, "get_bound_address", lambda: (HOST, PORT))
    assert mod.main() == 0
