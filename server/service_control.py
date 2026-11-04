"""Allowlisted systemd *user* service control + tailnet diagnosis.

Backing API for the settings toggle: the app may start/stop only the units in
:data:`ALLOWLIST` — ``hermes-dashboard``, ``opencode-free-bridge``,
``commandcode-proxy`` and ``talos-core``. Anything else (``ssh.service``, a
shell fragment, a path) is rejected before any subprocess runs.

Safety rules:

* Service names are validated by exact allowlist membership first; the
  validated name is then passed as a single argv element to ``systemctl``
  (list-form ``subprocess.run``, never ``shell=True``, never interpolated
  into a command string).
* Every public function returns a typed result dict with ``ok``/``error``
  keys; no exception escapes a public function.
* :func:`tailnet_status` distinguishes "Tailscale is off" from a generic
  connection failure, which is what the app shows in that case.
"""

from __future__ import annotations

import json
import logging
import socket
import subprocess
from typing import Any, Dict, List

logger = logging.getLogger(__name__)

#: The only units the app is ever allowed to touch.
ALLOWLIST = (
    "hermes-dashboard",
    "opencode-free-bridge",
    "commandcode-proxy",
    "talos-core",
)
ALLOWED_SERVICES = ALLOWLIST
ALLOWLISTED_SERVICES = ALLOWLIST

_SYSTEMCTL_TIMEOUT = 5
_TAILSCALE_TIMEOUT = 3


def _result(*, ok: bool, error: str = "", **fields: Any) -> Dict[str, Any]:
    out: Dict[str, Any] = {"ok": ok, "error": error}
    out.update(fields)
    return out


def _not_allowed(service_id: Any) -> Dict[str, Any]:
    return _result(
        ok=False,
        error=f"service not in allowlist: {service_id!r}",
        id=str(service_id),
        service_id=str(service_id),
        detail="rejected: not an allowlisted service",
    )


def _run(argv: List[str], timeout: int) -> subprocess.CompletedProcess:
    """Run argv without a shell. Raises on timeout/OS errors (callers catch)."""
    return subprocess.run(
        argv, capture_output=True, text=True, timeout=timeout, shell=False
    )


def status(service_id: str) -> Dict[str, Any]:
    """``{id, enabled, active, detail}`` for one allowlisted unit."""
    if service_id not in ALLOWLIST:
        out = _not_allowed(service_id)
        out.update({"enabled": False, "active": False})
        return out
    try:
        enabled_res = _run(["systemctl", "--user", "is-enabled", service_id], _SYSTEMCTL_TIMEOUT)
        active_res = _run(["systemctl", "--user", "is-active", service_id], _SYSTEMCTL_TIMEOUT)
    except Exception as exc:
        return _result(
            ok=False,
            error=str(exc) or exc.__class__.__name__,
            id=service_id,
            service_id=service_id,
            enabled=False,
            active=False,
            detail="",
        )
    enabled_out = (enabled_res.stdout or "").strip()
    active_out = (active_res.stdout or "").strip()
    detail = " ".join(
        part
        for part in [
            enabled_out,
            active_out,
            (enabled_res.stderr or "").strip(),
            (active_res.stderr or "").strip(),
        ]
        if part
    )
    if not enabled_out and not active_out:
        return _result(
            ok=False,
            error=detail or "systemctl returned no state",
            id=service_id,
            service_id=service_id,
            enabled=False,
            active=False,
            detail=detail,
        )
    return _result(
        ok=True,
        id=service_id,
        service_id=service_id,
        enabled=enabled_out == "enabled",
        active=active_out == "active",
        detail=detail,
    )


def set_enabled(service_id: str, enabled: bool) -> Dict[str, Any]:
    """``enable --now`` / ``disable --now`` for one allowlisted unit only."""
    enabled = bool(enabled)
    if service_id not in ALLOWLIST:
        out = _not_allowed(service_id)
        out["enabled"] = enabled
        return out
    verb = "enable" if enabled else "disable"
    try:
        res = _run(
            ["systemctl", "--user", verb, "--now", service_id],
            _SYSTEMCTL_TIMEOUT,
        )
    except Exception as exc:
        return _result(
            ok=False,
            error=str(exc) or exc.__class__.__name__,
            id=service_id,
            service_id=service_id,
            enabled=enabled,
            detail="",
        )
    detail = " ".join(p for p in [(res.stdout or "").strip(), (res.stderr or "").strip()] if p)
    if res.returncode == 0:
        return _result(
            ok=True,
            id=service_id,
            service_id=service_id,
            enabled=enabled,
            detail=detail,
        )
    return _result(
        ok=False,
        error=detail or f"systemctl exited with code {res.returncode}",
        id=service_id,
        service_id=service_id,
        enabled=enabled,
        detail=detail,
    )


def list_services() -> List[Dict[str, Any]]:
    """Status of every allowlisted service (helper for the settings screen)."""
    return [status(service_id) for service_id in ALLOWLIST]


def _fallback_tailnet() -> Dict[str, Any]:
    """Detect a 100.x tailnet address without the tailscale CLI.

    Uses the SIOCGIFADDR ioctl per interface (stdlib only, Linux). Any
    failure simply means "not detected" — it never raises.
    """
    address = ""
    try:
        import fcntl
        import struct

        SIOCGIFADDR = 0x8915
        for iface in socket.if_nameindex():
            name = iface[1]
            try:
                packed = fcntl.ioctl(
                    socket.AF_INET,
                    SIOCGIFADDR,
                    struct.pack("256s", name.encode()[:15]),
                )
                ip = socket.inet_ntoa(packed[20:24])
            except OSError:
                continue
            if ip.startswith("100."):
                address = ip
                break
    except Exception as exc:  # pragma: no cover - platform without fcntl
        logger.debug("Tailnet interface fallback unavailable: %s", exc)
    return {"up": bool(address), "address": address, "peers_online": 0}


def tailnet_status() -> Dict[str, Any]:
    """``{up, address, peers_online}`` — 'Tailscale is off' vs generic failure.

    Parses ``tailscale status --json`` when it works; falls back to a local
    100.x interface address. Malformed JSON, a missing binary, and timeouts
    all degrade to the fallback instead of raising.
    """
    try:
        res = _run(["tailscale", "status", "--json"], _TAILSCALE_TIMEOUT)
    except Exception as exc:
        logger.debug("tailscale status unavailable: %s", exc)
        res = None

    if res is not None and res.returncode == 0 and (res.stdout or "").strip():
        try:
            data = json.loads(res.stdout)
            if not isinstance(data, dict):
                raise ValueError("tailscale status JSON is not an object")
            self_node = data.get("Self") or {}
            ips = self_node.get("TailscaleIPs") or []
            address = str(ips[0]) if ips else ""
            peers = data.get("Peer") or {}
            peer_values = peers.values() if isinstance(peers, dict) else peers
            peers_online = sum(1 for p in peer_values if isinstance(p, dict) and p.get("Online"))
            backend = str(data.get("BackendState") or self_node.get("BackendState") or "")
            up = bool(address) and (not backend or backend == "Running")
            return _result(
                ok=True,
                up=up,
                address=address,
                peers_online=int(peers_online),
                detail=f"BackendState={backend}" if backend else "tailscale status --json",
            )
        except (ValueError, TypeError) as exc:
            # Malformed JSON must not throw: degrade to the interface fallback.
            logger.debug("Malformed tailscale status JSON: %s", exc)

    fallback = _fallback_tailnet()
    return _result(
        ok=True,
        up=fallback["up"],
        address=fallback["address"],
        peers_online=fallback["peers_online"],
        detail="interface fallback (tailscale status --json unavailable)",
    )


__all__ = [
    "ALLOWLIST",
    "ALLOWED_SERVICES",
    "ALLOWLISTED_SERVICES",
    "status",
    "set_enabled",
    "list_services",
    "tailnet_status",
]
