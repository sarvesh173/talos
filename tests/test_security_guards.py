"""Guards on the two routes that answer without a token.

`generate-quick` bootstraps the first token and `custom_providers` writes keys
into ~/.hermes/.env. Both used to be open to anyone who could reach the port, so
these tests pin down what the guard now refuses.

Run: pytest tests/test_security_guards.py -q
"""

import sys
from pathlib import Path

import pytest
from fastapi import HTTPException

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from server.custom_providers import _public_base_url_or_400  # noqa: E402
from server.main import _is_local_client  # noqa: E402


@pytest.mark.parametrize(
    "host,expected",
    [
        ("127.0.0.1", True),          # loopback
        ("192.168.31.7", True),       # RFC1918
        ("10.0.0.5", True),           # RFC1918
        ("100.75.107.79", True),      # Tailscale CGNAT — not "private" to ipaddress
        ("8.8.8.8", False),           # public
        ("example.com", False),       # name, not an address
        (None, False),
        ("", False),
    ],
)
def test_generate_quick_only_answers_local_peers(host, expected):
    assert _is_local_client(host) is expected


@pytest.mark.parametrize(
    "url",
    [
        "http://does-not-exist.invalid/v1",  # unreachable, not hostile
    ],
)
def test_unresolvable_host_is_not_a_hard_refusal(url):
    """Saving a provider while offline must still work."""
    assert _public_base_url_or_400(url) == url


@pytest.mark.parametrize(
    "url",
    [
        "https://api.openai.com/v1",
        "https://integrate.api.nvidia.com/v1",
        "http://127.0.0.1:4090/v1",      # local OpenCode bridge
        "http://localhost:4090/v1",      # resolves to ::1 — must not be rejected
        "http://192.168.31.5:11434/v1",  # LAN Ollama
        "http://100.75.107.79:8000/v1",  # Tailscale peer
    ],
)
def test_public_or_local_endpoints_are_allowed(url):
    assert _public_base_url_or_400(url) == url


@pytest.mark.parametrize(
    "url",
    [
        "http://169.254.169.254/latest",  # cloud metadata — the classic SSRF target
        "http://[fe80::1]/v1",            # IPv6 link-local
        "http://224.0.0.1/v1",            # multicast
        "http://0.0.0.0/v1",              # unspecified
        "not-a-url",                      # no host at all
    ],
)
def test_metadata_class_addresses_are_refused(url):
    with pytest.raises(HTTPException) as exc:
        _public_base_url_or_400(url)
    assert exc.value.status_code == 400