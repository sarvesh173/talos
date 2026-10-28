#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TALOS HOST NODE — AUTOMATED AGENT BOOTSTRAP & PAIRING GENERATOR
# ==============================================================================

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8010}"

echo ">>> [1/4] Checking host environment..."
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
    nohup env PORT="$PORT" "$PROJECT_ROOT/crates/talos-core/target/release/talos-core" >/tmp/talos-core.log 2>&1 &
    
    # Wait for daemon to respond
    for i in {1..20}; do
        if curl -s -f "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
            break
        fi
        sleep 0.5
    done
fi

echo ">>> [3/4] Resolving host Tailscale IPv4..."
TS_IP=$(tailscale ip -4 2>/dev/null || ip -4 addr show tailscale0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' || echo "")
if [ -z "$TS_IP" ]; then
    echo "ERROR: Tailscale is not running or has no IPv4 address assigned."
    echo "Please ensure Tailscale is active: tailscale up"
    exit 1
fi

echo ">>> [4/4] Minting authorized host pairing token..."
INVITE_JSON=$(curl -s -f "http://127.0.0.1:$PORT/api/pairing/invite" 2>/dev/null || echo "")
TOKEN=$(echo "$INVITE_JSON" | grep -oP '(?<="token":")[^"]*' || echo "")

if [ -z "$TOKEN" ]; then
    # Fallback: direct token mint via API
    MINT_JSON=$(curl -s -f -X POST "http://127.0.0.1:$PORT/api/tokens" \
        -H "Content-Type: application/json" \
        -d '{"name": "Agent Pairing Node", "role": "admin"}' 2>/dev/null || echo "")
    TOKEN=$(echo "$MINT_JSON" | grep -oP '(?<="plain_token":")[^"]*' || echo "")
fi

if [ -z "$TOKEN" ]; then
    echo "ERROR: Failed to mint token from talos-core daemon."
    exit 1
fi

echo ""
echo "=========================================================================="
echo "TALOS HOST NODE READY FOR MOBILE PAIRING"
echo "=========================================================================="
echo "User please enter this in your Tailscale IP section: http://${TS_IP}:${PORT}"
echo "User please enter this in your Host API Key section: ${TOKEN}"
echo "=========================================================================="
