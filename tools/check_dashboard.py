#!/usr/bin/env python3
"""Operator health checker for the Tailscale-bound Hermes dashboard.

Verifies, in order:
  (a) systemd unit hermes-dashboard.service exists and is enabled
  (b) it is active
  (c) something is listening on <host>:<port> (parsed from /proc/net/tcp,
      no root / no ss binary needed)
  (d) HTTP GET / returns 302 or 200 (both are correct; 302 = redirect to
      the login page, which is how this dashboard behaves when healthy)
  (e) / does NOT return 200 without a session cookie (that would mean
      auth is off and the dashboard is exposed on the tailnet)
  (f) the Tailscale interface is up and its address matches the address
      the unit binds to

Prints one PASS/FAIL line per check with the evidence value and exits
non-zero if any check fails. Read-only: never starts/stops the service.
"""

from __future__ import annotations

import http.client
import re
import socket
import subprocess
import sys
from pathlib import Path

DEFAULT_HOST = "100.75.107.79"
DEFAULT_PORT = 9119
UNIT = "hermes-dashboard.service"
PROC_NET_TCP = Path("/proc/net/tcp")
LISTEN_STATE = "0A"  # TCP_LISTEN in /proc/net/tcp `st` column

Check = dict  # {"name": str, "ok": bool, "evidence": str}


# --------------------------------------------------------------------------
# Pure parsing / classification helpers (unit-tested, no live system needed)
# --------------------------------------------------------------------------

def ip_to_proc_hex(ip: str) -> str:
    """Encode a dotted-quad IPv4 address as /proc/net/tcp stores it.

    /proc/net/tcp stores the address as a little-endian 32-bit hex word,
    e.g. 127.0.0.1 -> "0100007F" and 100.75.107.79 -> "4F6B4B64".
    """
    packed = socket.inet_aton(ip)
    return packed[::-1].hex().upper()


def proc_hex_to_ip(hex_str: str) -> str:
    """Decode a /proc/net/tcp hex address back to dotted-quad."""
    raw = bytes.fromhex(hex_str.strip())
    if len(raw) != 4:
        raise ValueError(f"bad /proc/net/tcp address: {hex_str!r}")
    return socket.inet_ntoa(raw[::-1])


def parse_proc_net_tcp(text: str, host: str, port: int) -> bool:
    """Return True iff *text* (/proc/net/tcp contents) shows a LISTEN
    socket on host:port. Pure function over fixture strings."""
    target_ip = ip_to_proc_hex(host)
    target_port = f"{port:04X}"
    for line in text.splitlines():
        parts = line.split()
        if len(parts) < 4:
            continue
        local = parts[1]  # e.g. "4F6B4B64:239F"
        state = parts[3]
        if state.upper() != LISTEN_STATE:
            continue
        if ":" not in local:
            continue
        addr_hex, port_hex = local.split(":", 1)
        if addr_hex.upper() == target_ip and port_hex.upper() == target_port:
            return True
    return False


def classify_http_status(status: int | None) -> tuple[bool, str]:
    """Classify an HTTP status for check (d).

    302 (redirect to login) and 200 are both correct. Everything else
    (401, 500, connection errors represented as None, ...) is a failure.
    """
    if status is None:
        return False, "no HTTP response (connection failed)"
    if status == 302:
        return True, "302 (redirect to login page)"
    if status == 200:
        return True, "200 OK"
    return False, f"unexpected status {status}"


def evaluate_auth(status: int | None, has_session_cookie: bool) -> tuple[bool, str]:
    """Evaluate check (e): auth must not be bypassed.

    A 200 response with no session cookie means the dashboard served
    content to an unauthenticated caller -- a security failure. A 302
    (redirect to login) or a 401 challenge both keep auth in place.
    """
    if status == 200 and not has_session_cookie:
        return False, "200 without session cookie (auth appears OFF)"
    if status == 200 and has_session_cookie:
        return True, "200 with session cookie"
    if status == 302:
        return True, "302 redirect to login (auth enforced)"
    if status == 401:
        return True, "401 auth challenge (auth enforced)"
    if status is None:
        return False, "no HTTP response; cannot verify auth"
    # Other statuses are already failures of check (d); they do not by
    # themselves expose content, but do not claim auth is verified.
    return False, f"status {status}; auth not verified as enforced"


def has_session_cookie(headers) -> bool:
    """True if response headers carry a Set-Cookie with 'session' in it.

    *headers* may be a dict, an http.client message, or a list of
    (name, value) pairs."""
    if headers is None:
        return False
    items = []
    if hasattr(headers, "items"):
        items = list(headers.items())
    else:
        items = list(headers)
    for name, value in items:
        if str(name).lower() == "set-cookie" and "session" in str(value).lower():
            return True
    return False


def parse_bound_address(unit_text: str) -> tuple[str, int]:
    """Extract --host/--port from the unit's ExecStart line."""
    host, port = DEFAULT_HOST, DEFAULT_PORT
    m = re.search(r"--host\s+(\S+)", unit_text)
    if m:
        host = m.group(1)
    m = re.search(r"--port\s+(\d+)", unit_text)
    if m:
        port = int(m.group(1))
    return host, port


def parse_ip_addr_output(text: str) -> tuple[bool, str | None]:
    """Parse `ip -4 addr show tailscale0` output.

    Returns (interface_up, ipv4_address_or_None)."""
    if not text:
        return False, None
    first_line = text.splitlines()[0] if text.splitlines() else ""
    up = "UP" in first_line.split(">")[0] if ">" in first_line else ("state UP" in text or "<UP" in text)
    # tailscale0 reports e.g. <POINTOPOINT,...,UP,LOWER_UP> with state
    # UNKNOWN, so key off the flag word, not the state field.
    m = re.search(r"inet\s+(\d+\.\d+\.\d+\.\d+)/", text)
    addr = m.group(1) if m else None
    return bool(up), addr


def parse_tailscale_ip_output(text: str) -> str | None:
    """Parse `tailscale ip -4` output (one address per line)."""
    for line in (text or "").splitlines():
        line = line.strip()
        if re.fullmatch(r"\d+\.\d+\.\d+\.\d+", line):
            return line
    return None


def build_checks(
    *,
    unit_enabled: tuple[bool, str],
    unit_active: tuple[bool, str],
    listening: tuple[bool, str],
    http: tuple[int | None, str],  # (status, raw evidence)
    cookie: bool,
    tailscale: tuple[bool, str],
) -> list[Check]:
    """Assemble the six ordered checks from already-gathered evidence.

    Pure: lets tests drive pass/fail behaviour without the live system.
    """
    status, http_evidence = http
    http_ok, http_reason = classify_http_status(status)
    auth_ok, auth_reason = evaluate_auth(status, cookie)
    checks: list[Check] = [
        {"name": "(a) systemd unit exists and is enabled", "ok": unit_enabled[0], "evidence": unit_enabled[1]},
        {"name": "(b) systemd unit is active", "ok": unit_active[0], "evidence": unit_active[1]},
        {"name": "(c) listening on bound address", "ok": listening[0], "evidence": listening[1]},
        {"name": "(d) HTTP / returns 302 or 200", "ok": http_ok, "evidence": http_reason if status is not None else http_evidence},
        {"name": "(e) auth enforced (no unauthenticated 200)", "ok": auth_ok, "evidence": auth_reason},
        {"name": "(f) Tailscale interface up, address matches unit bind", "ok": tailscale[0], "evidence": tailscale[1]},
    ]
    return checks


def exit_code_for(checks: list[Check]) -> int:
    return 0 if all(c["ok"] for c in checks) else 1


# --------------------------------------------------------------------------
# Live (read-only) probes
# --------------------------------------------------------------------------

def _run(cmd: list[str]) -> tuple[int, str]:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        return proc.returncode, (proc.stdout or "").strip()
    except (OSError, subprocess.SubprocessError) as exc:
        return 127, f"{exc}"


def check_unit_enabled() -> tuple[bool, str]:
    rc, out = _run(["systemctl", "--user", "is-enabled", UNIT])
    return (rc == 0 and out == "enabled"), f"is-enabled rc={rc} value={out!r}"


def check_unit_active() -> tuple[bool, str]:
    rc, out = _run(["systemctl", "--user", "is-active", UNIT])
    return (rc == 0 and out == "active"), f"is-active rc={rc} value={out!r}"


def get_bound_address() -> tuple[str, int]:
    _rc, out = _run(["systemctl", "--user", "cat", UNIT])
    if out:
        return parse_bound_address(out)
    unit_file = Path.home() / ".config/systemd/user" / UNIT
    try:
        return parse_bound_address(unit_file.read_text())
    except OSError:
        return DEFAULT_HOST, DEFAULT_PORT


def check_listening(host: str, port: int) -> tuple[bool, str]:
    try:
        text = PROC_NET_TCP.read_text()
    except OSError as exc:
        return False, f"cannot read {PROC_NET_TCP}: {exc}"
    ok = parse_proc_net_tcp(text, host, port)
    return ok, f"{host}:{port} LISTEN in /proc/net/tcp: {ok}"


def check_http(host: str, port: int) -> tuple[int | None, bool, str]:
    """GET / without following redirects. Returns (status, cookie, evidence)."""
    try:
        conn = http.client.HTTPConnection(host, port, timeout=5)
        conn.request("GET", "/")
        resp = conn.getresponse()
        status = resp.status
        cookie = has_session_cookie(resp.getheaders())
        evidence = f"GET http://{host}:{port}/ -> {status}"
        conn.close()
        return status, cookie, evidence
    except OSError as exc:
        return None, False, f"GET http://{host}:{port}/ failed: {exc}"


def check_tailscale(bound_host: str) -> tuple[bool, str]:
    # Preferred: `tailscale ip -4`
    rc, out = _run(["tailscale", "ip", "-4"])
    ts_addr = parse_tailscale_ip_output(out) if rc == 0 else None
    if ts_addr is None:
        rc2, out2 = _run(["ip", "-4", "addr", "show", "tailscale0"])
        up, ts_addr = parse_ip_addr_output(out2) if rc2 == 0 else (False, None)
        evidence = f"tailscale0 up={up} addr={ts_addr} bound={bound_host}"
        return bool(up and ts_addr == bound_host), evidence
    evidence = f"tailscale ip -4={ts_addr} bound={bound_host}"
    return ts_addr == bound_host, evidence


def collect_checks() -> list[Check]:
    host, port = get_bound_address()
    unit_enabled = check_unit_enabled()
    unit_active = check_unit_active()
    listening = check_listening(host, port)
    status, cookie, http_evidence = check_http(host, port)
    tailscale = check_tailscale(host)
    return build_checks(
        unit_enabled=unit_enabled,
        unit_active=unit_active,
        listening=listening,
        http=(status, http_evidence),
        cookie=cookie,
        tailscale=tailscale,
    )


def main() -> int:
    checks = collect_checks()
    for c in checks:
        verdict = "PASS" if c["ok"] else "FAIL"
        print(f"{verdict}: {c['name']} -- {c['evidence']}")
    code = exit_code_for(checks)
    print(f"RESULT: {'healthy' if code == 0 else 'UNHEALTHY'} "
          f"({sum(c['ok'] for c in checks)}/{len(checks)} checks passed)")
    return code


if __name__ == "__main__":
    sys.exit(main())
