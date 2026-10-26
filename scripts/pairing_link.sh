#!/usr/bin/env bash
# Print a one-time pairing link for the TALOS Android app.
#
# The agent on the host runs this and hands the output to the phone (as a
# link, or rendered as a QR). The phone opens it and pairs itself - no
# address is ever typed into the app.
#
# Usage:
#   scripts/pairing_link.sh [core-url]
#
# Default core URL is the local core; override when the core runs elsewhere:
#   scripts/pairing_link.sh http://127.0.0.1:8010
set -euo pipefail

CORE_URL="${1:-http://127.0.0.1:8010}"
if [[ "$CORE_URL" != http*://* ]]; then
  CORE_URL="http://$CORE_URL"
fi

INVITE=$(curl -fsS --max-time 8 "$CORE_URL/api/pairing/invite")
URI=$(printf '%s' "$INVITE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["uri"])')
MASKED=$(printf '%s' "$INVITE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["masked_token"])')

cat <<EOF

TALOS pairing link (one-time, valid until the core restarts):

$URI

Token: $MASKED

Open the link on the phone (or scan it as a QR) to pair. The app stores the
endpoint itself - the address is never shown in the app UI.

EOF

# Optional QR rendering - only if a QR tool is already present.
if command -v qrencode >/dev/null 2>&1; then
  qrencode -t ANSIUTF8 "$URI"
  echo
elif python3 -c 'import qrcode' >/dev/null 2>&1; then
  python3 - "$URI" <<'PY'
import sys, qrcode
qr = qrcode.QRCode(border=1)
qr.add_data(sys.argv[1])
qr.make(fit=True)
qr.print_ascii(invert=True)
PY
else
  echo "(no QR renderer installed - open the link directly, or install qrencode)"
fi
