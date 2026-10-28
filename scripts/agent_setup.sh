#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TALOS HOST NODE — UNIVERSAL AGENT BOOTSTRAP WRAPPER
# Automatically manages a SINGLE Host API key and persists credentials safely
# in ~/.config/talos/credentials.json (chmod 600).
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN=""

if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
fi

if [ -n "$PYTHON_BIN" ] && [ -f "$SCRIPT_DIR/agent_setup.py" ]; then
    exec "$PYTHON_BIN" "$SCRIPT_DIR/agent_setup.py" "$@"
fi

# ------------------------------------------------------------------------------
# Portable POSIX Fallback if Python is unavailable
# ------------------------------------------------------------------------------
PORT="${PORT:-8010}"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_DIR="$HOME/.config/talos"
CRED_FILE="$CONFIG_DIR/credentials.json"

mkdir -p "$CONFIG_DIR"
chmod 700 "$CONFIG_DIR" 2>/dev/null || true

echo ">>> [1/4] Checking host environment (Shell Fallback)..."
for dep in cargo tailscale curl; do
    if ! command -v "$dep" >/dev/null 2>&1; then
        echo "ERROR: Missing required dependency: $dep"
        exit 1
    fi
done

echo ">>> [2/4] Ensuring talos-core is built and running on port $PORT..."
if ! curl -s -f "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
    echo "Building talos-core release binary..."
    cd "$PROJECT_ROOT/crates/talos-core"
    cargo build --release --quiet
    
    echo "Starting talos-core in background on port $PORT..."
    nohup env PORT="$PORT" "$PROJECT_ROOT/crates/talos-core/target/release/talos-core" >"$CONFIG_DIR/daemon.log" 2>&1 &
    
    for i in {1..25}; do
        if curl -s -f "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
            break
        fi
        sleep 0.4
    done
fi

echo ">>> [3/4] Resolving host Tailscale IPv4..."
TS_IP=$(tailscale ip -4 2>/dev/null || ip -4 addr show tailscale0 2>/dev/null | grep -oE '100\.[0-9]+\.[0-9]+\.[0-9]+' || echo "")
if [ -z "$TS_IP" ]; then
    echo "ERROR: Tailscale is not running or has no IPv4 address assigned."
    exit 1
fi

echo ">>> [4/4] Managing Single Host API Key at $CRED_FILE..."
TOKEN=""

# Check if existing key in credentials.json is valid
if [ -f "$CRED_FILE" ]; then
    EXISTING_TOKEN=$(grep -oE 'talos_sk_[a-zA-Z0-9]+' "$CRED_FILE" | head -n 1 || echo "")
    if [ -n "$EXISTING_TOKEN" ]; then
        STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$PORT/api/auth/pair" \
            -H "Content-Type: application/json" \
            -d "{\"token\": \"$EXISTING_TOKEN\"}" || echo "000")
        if [ "$STATUS_CODE" = "200" ]; then
            TOKEN="$EXISTING_TOKEN"
            echo ">>> Existing verified Host API Key found in safe storage. Reusing single key."
        fi
    fi
fi

# Mint single key if needed
if [ -z "$TOKEN" ]; then
    echo ">>> Minting single persistent Host API Key..."
    INVITE_JSON=$(curl -s -f "http://127.0.0.1:$PORT/api/pairing/invite" 2>/dev/null || echo "")
    TOKEN=$(echo "$INVITE_JSON" | grep -oE 'talos_sk_[a-zA-Z0-9]+' | head -n 1 || echo "")
    
    if [ -z "$TOKEN" ]; then
        MINT_JSON=$(curl -s -f -X POST "http://127.0.0.1:$PORT/api/tokens" \
            -H "Content-Type: application/json" \
            -d '{"name": "Primary Host Key", "role": "admin"}' 2>/dev/null || echo "")
        TOKEN=$(echo "$MINT_JSON" | grep -oE 'talos_sk_[a-zA-Z0-9]+' | head -n 1 || echo "")
    fi
    
    cat > "$CRED_FILE" <<EOF
{
  "tailscale_ip": "$TS_IP",
  "port": $PORT,
  "endpoint": "http://$TS_IP:$PORT",
  "api_key": "$TOKEN",
  "environment": "$(uname -s 2>/dev/null || echo 'Unknown')"
}
EOF
    chmod 600 "$CRED_FILE" 2>/dev/null || true
    echo ">>> Saved single persistent key to $CRED_FILE (chmod 600)."
fi

ENDPOINT="http://${TS_IP}:${PORT}"
echo ""
echo "=========================================================================="
echo "TALOS HOST NODE — SECURE OVERLAY CREDENTIALS (SINGLE API KEY)"
echo "=========================================================================="
echo "Tailscale Host Address: ${ENDPOINT}"
echo "Single Host API Key:    ${TOKEN}"
echo "Safe Storage Path:      ${CRED_FILE} (chmod 600)"
echo "=========================================================================="
echo "User please enter this in your Tailscale IP section: ${ENDPOINT}"
echo "User please enter this in your Host API Key section: ${TOKEN}"
echo "=========================================================================="
