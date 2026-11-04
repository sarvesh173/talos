"""Tests for server.service_control — subprocess is always mocked."""

import json
import subprocess
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import server.service_control as sc


def _completed(stdout="", stderr="", returncode=0):
    return subprocess.CompletedProcess(
        args=[], returncode=returncode, stdout=stdout, stderr=stderr
    )


def test_allowlist_is_exactly_the_four_units():
    assert list(sc.ALLOWLIST) == [
        "hermes-dashboard",
        "opencode-free-bridge",
        "commandcode-proxy",
        "talos-core",
    ]


def test_allowlist_rejects_arbitrary_unit():
    with patch("server.service_control.subprocess.run") as mock_run:
        for bad in ["ssh.service", "ssh", "hermes-dashboard.service",
                    "hermes-dashboard; rm -rf ~", "../evil", "", "talos-core\n"]:
            result = sc.status(bad)
            assert result["ok"] is False
            assert "allowlist" in result["error"]
            result2 = sc.set_enabled(bad, True)
            assert result2["ok"] is False
            assert "allowlist" in result2["error"]
        mock_run.assert_not_called()  # rejected before any subprocess runs


def test_enable_builds_exact_argv_without_shell():
    with patch("server.service_control.subprocess.run",
               return_value=_completed()) as mock_run:
        result = sc.set_enabled("hermes-dashboard", True)
    mock_run.assert_called_once_with(
        ["systemctl", "--user", "enable", "--now", "hermes-dashboard"],
        capture_output=True, text=True, timeout=sc._SYSTEMCTL_TIMEOUT, shell=False,
    )
    assert result["ok"] is True
    assert result["enabled"] is True


def test_disable_builds_exact_argv_without_shell():
    with patch("server.service_control.subprocess.run",
               return_value=_completed()) as mock_run:
        result = sc.set_enabled("talos-core", False)
    mock_run.assert_called_once_with(
        ["systemctl", "--user", "disable", "--now", "talos-core"],
        capture_output=True, text=True, timeout=sc._SYSTEMCTL_TIMEOUT, shell=False,
    )
    assert result["ok"] is True
    assert result["enabled"] is False


def test_set_enabled_reports_systemctl_failure():
    with patch("server.service_control.subprocess.run",
               return_value=_completed(stderr="denied", returncode=1)):
        result = sc.set_enabled("commandcode-proxy", True)
    assert result["ok"] is False
    assert "denied" in result["error"]


def test_status_parses_active_and_enabled():
    with patch("server.service_control.subprocess.run",
               side_effect=[_completed("enabled\n"), _completed("active\n")]) as mock_run:
        result = sc.status("opencode-free-bridge")
    assert result["ok"] is True
    assert result["id"] == "opencode-free-bridge"
    assert result["enabled"] is True
    assert result["active"] is True
    assert mock_run.call_args_list[0][0][0] == [
        "systemctl", "--user", "is-enabled", "opencode-free-bridge"]
    assert mock_run.call_args_list[1][0][0] == [
        "systemctl", "--user", "is-active", "opencode-free-bridge"]


def test_status_parses_disabled_and_inactive():
    with patch("server.service_control.subprocess.run",
               side_effect=[_completed("disabled\n", returncode=1),
                            _completed("inactive\n", returncode=3)]):
        result = sc.status("hermes-dashboard")
    assert result["ok"] is True
    assert result["enabled"] is False
    assert result["active"] is False


def test_status_no_exceptions_escape_when_systemctl_missing():
    with patch("server.service_control.subprocess.run",
               side_effect=FileNotFoundError("no systemctl")):
        result = sc.status("hermes-dashboard")
    assert result["ok"] is False
    assert result["enabled"] is False and result["active"] is False
    with patch("server.service_control.subprocess.run",
               side_effect=FileNotFoundError("no systemctl")):
        result2 = sc.set_enabled("hermes-dashboard", True)
    assert result2["ok"] is False


def test_tailnet_status_parses_valid_json():
    data = {
        "BackendState": "Running",
        "Self": {"TailscaleIPs": ["100.75.107.79"]},
        "Peer": {
            "a": {"Online": True},
            "b": {"Online": False},
            "c": {"Online": True},
        },
    }
    with patch("server.service_control.subprocess.run",
               return_value=_completed(json.dumps(data))):
        result = sc.tailnet_status()
    assert result["ok"] is True
    assert result["up"] is True
    assert result["address"] == "100.75.107.79"
    assert result["peers_online"] == 2


def test_tailnet_status_handles_malformed_json_without_throwing():
    with patch("server.service_control.subprocess.run",
               return_value=_completed("{this is not json")):
        result = sc.tailnet_status()  # must not raise
    assert result["ok"] is True
    assert set(result) >= {"up", "address", "peers_online"}
    assert isinstance(result["up"], bool)
    assert isinstance(result["peers_online"], int)


def test_tailnet_status_handles_missing_binary_and_nonzero_exit():
    with patch("server.service_control.subprocess.run",
               side_effect=FileNotFoundError("no tailscale")):
        result = sc.tailnet_status()
    assert result["ok"] is True and isinstance(result["up"], bool)
    with patch("server.service_control.subprocess.run",
               return_value=_completed(stderr="down", returncode=1)):
        result2 = sc.tailnet_status()
    assert result2["ok"] is True and isinstance(result2["up"], bool)
