"""Mirror of ServiceStatusProbe.kt classify() (pure, JVM-testable in Kotlin).

Kotlin order (first match wins):
  200 -> Online; 302/401 -> AuthRequired; 403 -> Unauthorized;
  !hasTailnetAddress -> TailscaleOff; portRefused -> ServiceStopped;
  threw -> HostOffline; else -> Unknown.
Invariant: TailscaleOff never returned when hasTailnetAddress is True.
"""

import itertools

import pytest

TAILSCALE_OFF = "TailscaleOff"
HOST_OFFLINE = "HostOffline"
SERVICE_STOPPED = "ServiceStopped"
AUTH_REQUIRED = "AuthRequired"
UNAUTHORIZED = "Unauthorized"
ONLINE = "Online"
UNKNOWN = "Unknown"
ALL_STATES = {
    TAILSCALE_OFF, HOST_OFFLINE, SERVICE_STOPPED, AUTH_REQUIRED,
    UNAUTHORIZED, ONLINE, UNKNOWN,
}


class Timeout(Exception):
    pass


class ConnectRefused(Exception):
    pass


def classify(http_code, threw, has_tailnet_address, port_refused):
    if http_code == 200:
        return ONLINE
    if http_code in (302, 401):
        return AUTH_REQUIRED
    if http_code == 403:
        return UNAUTHORIZED
    if not has_tailnet_address:
        return TAILSCALE_OFF
    if port_refused:
        return SERVICE_STOPPED
    if threw is not None:
        return HOST_OFFLINE
    return UNKNOWN


def dot_tone(state):
    if state == ONLINE:
        return "GREEN"
    if state == AUTH_REQUIRED:
        return "NEUTRAL"
    if state in (UNAUTHORIZED, UNKNOWN):
        return "RED"
    return "NEUTRAL"


# Every (httpCode, threw, hasTailnet, portRefused) combination in the table.
CODES = [None, 200, 302, 401, 403, 404, 500]
THREW = [None, Timeout("timed out"), ConnectRefused("refused"), RuntimeError("boom")]
ALL_COMBOS = list(itertools.product(CODES, THREW, [True, False], [True, False]))

# Explicit classification table rows (also covered exhaustively below).
TABLE = [
    # http_code, threw, has_tailnet, port_refused, expected
    (200, None, True, False, ONLINE),
    (200, None, False, False, ONLINE),          # host answered: tailnet check trumps
    (302, None, True, False, AUTH_REQUIRED),    # dashboard 302 is healthy
    (302, None, False, False, AUTH_REQUIRED),
    (401, None, True, False, AUTH_REQUIRED),
    (401, None, False, True, AUTH_REQUIRED),
    (403, None, True, False, UNAUTHORIZED),     # token present and rejected
    (403, None, False, False, UNAUTHORIZED),
    (None, Timeout("t"), False, False, TAILSCALE_OFF),  # timeout + no tailnet
    (None, Timeout("t"), False, True, TAILSCALE_OFF),   # no tailnet beats refused
    (None, None, False, False, TAILSCALE_OFF),          # no 100.x address at all
    (500, None, False, False, TAILSCALE_OFF),           # no tailnet beats other codes
    (None, ConnectRefused("r"), True, True, SERVICE_STOPPED),
    (None, None, True, True, SERVICE_STOPPED),
    (None, Timeout("t"), True, False, HOST_OFFLINE),    # tailnet up, peer silent
    (None, RuntimeError("x"), True, False, HOST_OFFLINE),
    (500, None, True, False, UNKNOWN),
    (404, None, True, False, UNKNOWN),
    (None, None, True, False, UNKNOWN),         # no code, no throw: preserved as Unknown
]


@pytest.mark.parametrize("code,threw,has_tailnet,refused,expected", TABLE)
def test_classification_table(code, threw, has_tailnet, refused, expected):
    assert classify(code, threw, has_tailnet, refused) == expected


def test_exhaustive_combos_map_to_exactly_one_state():
    for code, threw, has_tailnet, refused in ALL_COMBOS:
        result = classify(code, threw, has_tailnet, refused)
        assert result in ALL_STATES
        # TailscaleOff is never returned when hasTailnetAddress is true.
        if has_tailnet:
            assert result != TAILSCALE_OFF
        else:
            # With no tailnet address, every non-answered response is TailscaleOff.
            if code not in (200, 302, 401, 403):
                assert result == TAILSCALE_OFF


def test_every_state_is_reachable():
    seen = {classify(*c) for c in ALL_COMBOS}
    assert seen == ALL_STATES


def test_dot_tones():
    assert dot_tone(ONLINE) == "GREEN"
    assert dot_tone(AUTH_REQUIRED) == "NEUTRAL"
    assert dot_tone(UNAUTHORIZED) == "RED"
    assert dot_tone(UNKNOWN) == "RED"
    # Red ONLY for Unauthorized and Unknown.
    red = {s for s in ALL_STATES if dot_tone(s) == "RED"}
    assert red == {UNAUTHORIZED, UNKNOWN}
