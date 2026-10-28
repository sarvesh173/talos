#!/usr/bin/env python3
"""
TALOS HOST NODE — UNIVERSAL CROSS-PLATFORM AGENT BOOTSTRAP & CREDENTIAL MANAGER
Works natively on Linux, macOS, WSL, and Windows.
Enforces Single Host API Key policy and persists credentials in ~/.config/talos/credentials.json.
"""

import json
import os
import platform
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PORT = int(os.environ.get("PORT", "8010"))
SYSTEM = platform.system().lower()

def get_safe_config_dir() -> Path:
    """Return user's safe config directory with owner-only permissions."""
    if SYSTEM == "windows":
        app_data = os.environ.get("APPDATA")
        base = Path(app_data) if app_data else Path.home() / "AppData" / "Roaming"
    else:
        base = Path.home() / ".config"
    config_dir = base / "talos"
    config_dir.mkdir(parents=True, exist_ok=True)
    try:
        os.chmod(config_dir, 0o700)
    except Exception:
        pass
    return config_dir

def get_safe_credentials_file() -> Path:
    return get_safe_config_dir() / "credentials.json"

def check_daemon_healthy(port: int = PORT) -> bool:
    url = f"http://127.0.0.1:{port}/health"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Talos-Bootstrap"})
        with urllib.request.urlopen(req, timeout=2) as resp:
            return resp.status == 200
    except Exception:
        return False

def verify_token(token: str, port: int = PORT) -> bool:
    if not token or not token.startswith("talos_sk_"):
        return False
    url = f"http://127.0.0.1:{port}/api/auth/pair"
    payload = json.dumps({"token": token}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "Talos-Bootstrap"}
    )
    try:
        with urllib.request.urlopen(req, timeout=3) as resp:
            return resp.status == 200
    except urllib.error.HTTPError as e:
        return e.code == 200
    except Exception:
        return False

def get_tailscale_ip() -> str:
    """Resolve active host Tailscale IPv4 address cross-platform."""
    # 1. Try tailscale CLI
    try:
        out = subprocess.check_output(["tailscale", "ip", "-4"], stderr=subprocess.DEVNULL, timeout=4)
        ip = out.decode("utf-8").strip().splitlines()[0].strip()
        if ip and ip.startswith("100."):
            return ip
    except Exception:
        pass

    # 2. Try parsing `tailscale status --json`
    try:
        out = subprocess.check_output(["tailscale", "status", "--json"], stderr=subprocess.DEVNULL, timeout=4)
        data = json.loads(out.decode("utf-8"))
        for ip in data.get("Self", {}).get("TailscaleIPs", []):
            if ip.startswith("100.") and ":" not in ip:
                return ip
    except Exception:
        pass

    # 3. Fallback: inspect socket/interfaces
    try:
        import socket
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            sockaddr = info[4]
            if isinstance(sockaddr, tuple) and len(sockaddr) > 0:
                ip = str(sockaddr[0])
                parts = [int(p) for p in ip.split(".") if p.isdigit()]
                if len(parts) == 4 and parts[0] == 100 and (64 <= parts[1] <= 127):
                    return ip
    except Exception:
        pass

    return ""

def ensure_daemon_running(project_root: Path, port: int = PORT):
    """Ensure talos-core is built and running in the background."""
    if check_daemon_healthy(port):
        return

    print(">>> talos-core daemon is not running. Checking build...")
    core_dir = project_root / "crates" / "talos-core"
    
    if SYSTEM == "windows":
        target_bin = core_dir / "target" / "release" / "talos-core.exe"
    else:
        target_bin = core_dir / "target" / "release" / "talos-core"

    if not target_bin.exists():
        print(">>> Building release binary (cargo build --release)...")
        if not shutil.which("cargo"):
            print("ERROR: 'cargo' is required to build talos-core. Please install Rust.")
            sys.exit(1)
        res = subprocess.run(["cargo", "build", "--release", "--quiet"], cwd=str(core_dir))
        if res.returncode != 0:
            print("ERROR: Failed to build talos-core.")
            sys.exit(1)

    print(f">>> Launching talos-core daemon in background on port {port}...")
    env = os.environ.copy()
    env["PORT"] = str(port)

    log_file = get_safe_config_dir() / "daemon.log"
    with open(log_file, "a") as log_out:
        if SYSTEM == "windows":
            c_flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "DETACHED_PROCESS", 0)
            subprocess.Popen(
                [str(target_bin)],
                cwd=str(core_dir),
                env=env,
                stdout=log_out,
                stderr=log_out,
                creationflags=c_flags
            )
        else:
            subprocess.Popen(
                [str(target_bin)],
                cwd=str(core_dir),
                env=env,
                stdout=log_out,
                stderr=log_out,
                start_new_session=True
            )

    # Wait for daemon to respond
    for _ in range(25):
        if check_daemon_healthy(port):
            print(">>> Daemon started and healthy.")
            return
        time.sleep(0.4)

    print(f"ERROR: Daemon failed to start. Check log at {log_file}")
    sys.exit(1)

def mint_single_api_key(port: int = PORT) -> str:
    """Mint a single host API key from the local daemon."""
    url = f"http://127.0.0.1:{port}/api/tokens"
    payload = json.dumps({"name": "Primary Host Key", "role": "admin"}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "Talos-Bootstrap"}
    )
    try:
        with urllib.request.urlopen(req, timeout=4) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("plain_token")
            if token:
                return token
    except Exception as e:
        print(f"Token mint error via /api/tokens: {e}")

    # Fallback to /api/pairing/invite
    try:
        invite_url = f"http://127.0.0.1:{port}/api/pairing/invite"
        req = urllib.request.Request(invite_url, headers={"User-Agent": "Talos-Bootstrap"})
        with urllib.request.urlopen(req, timeout=4) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("token")
            if token:
                return token
    except Exception as e:
        print(f"Invite mint error via /api/pairing/invite: {e}")

    return ""

def main():
    project_root = Path(__file__).resolve().parent.parent
    cred_file = get_safe_credentials_file()

    print(f">>> [1/4] Environment: {platform.system()} ({platform.machine()})")
    print(f">>> [2/4] Ensuring talos-core daemon is running on port {PORT}...")
    ensure_daemon_running(project_root, PORT)

    print(">>> [3/4] Resolving host Tailscale IPv4 address...")
    ts_ip = get_tailscale_ip()
    if not ts_ip:
        print("WARNING: Could not detect active Tailscale IPv4.")
        print("Please ensure Tailscale is installed and running ('tailscale up').")
        ts_ip = "<YOUR_TAILSCALE_IP>"

    print(f">>> [4/4] Managing Single Host API Key at {cred_file}...")
    active_key = None

    # Check if a single key is already stored and still valid
    if cred_file.exists():
        try:
            with open(cred_file, "r") as f:
                saved = json.load(f)
                candidate = saved.get("api_key")
                if candidate and verify_token(candidate, PORT):
                    active_key = candidate
                    print(">>> Found existing verified Host API Key in safe storage. Reusing single key.")
        except Exception:
            pass

    # If no valid key was found, mint exactly ONE key and store it
    if not active_key:
        print(">>> Minting single persistent Host API Key...")
        active_key = mint_single_api_key(PORT)
        if not active_key:
            print("ERROR: Failed to obtain API key from talos-core daemon.")
            sys.exit(1)

        cred_data = {
            "tailscale_ip": ts_ip,
            "port": PORT,
            "endpoint": f"http://{ts_ip}:{PORT}" if ts_ip != "<YOUR_TAILSCALE_IP>" else "",
            "api_key": active_key,
            "created_at": time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()),
            "environment": f"{platform.system()} {platform.release()}"
        }

        with open(cred_file, "w") as f:
            json.dump(cred_data, f, indent=2)

        try:
            os.chmod(cred_file, 0o600)
        except Exception:
            pass
        print(f">>> Saved single persistent key to {cred_file} (chmod 600).")

    # Final Output formatting strictly as specified
    endpoint_str = f"http://{ts_ip}:{PORT}"
    print("")
    print("=" * 74)
    print("TALOS HOST NODE — SECURE OVERLAY CREDENTIALS (SINGLE API KEY)")
    print("=" * 74)
    print(f"Tailscale Host Address: {endpoint_str}")
    print(f"Single Host API Key:    {active_key}")
    print(f"Safe Storage Path:      {cred_file} (chmod 600)")
    print("=" * 74)
    print(f"User please enter this in your Tailscale IP section: {endpoint_str}")
    print(f"User please enter this in your Host API Key section: {active_key}")
    print("=" * 74)

if __name__ == "__main__":
    main()
