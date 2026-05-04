#!/usr/bin/env bash
set -euo pipefail

# ─── Traffic Generator ───────────────────────────────────
# Sends requests to the order-service /checkout endpoint.
#
# Usage:
#   ./generate-traffic.sh                        # defaults
#   TARGET_URL=http://localhost:18081/checkout ./generate-traffic.sh
#   RPS=5 ./generate-traffic.sh

TARGET_URL="${TARGET_URL:-http://localhost:18081/checkout}"
RPS="${RPS:-1}"
INTERVAL=$(python3 -c "print(1.0 / $RPS)" 2>/dev/null || echo "1")

echo "=== Traffic Generator ==="
echo "  Target: $TARGET_URL"
echo "  Rate:   $RPS req/s (interval: ${INTERVAL}s)"
echo "  Press Ctrl+C to stop."
echo ""

COUNT=0
ERRORS=0

cleanup() {
  echo ""
  echo "=== Stopped ==="
  echo "  Total requests: $COUNT"
  echo "  Errors: $ERRORS"
  exit 0
}
trap cleanup EXIT INT TERM

while true; do
  RESPONSE=$(curl -s -w '\n%{http_code}' --max-time 5 "$TARGET_URL" 2>/dev/null || echo -e "\n000")
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  COUNT=$((COUNT + 1))

  if [ "$HTTP_CODE" = "000" ] || [ "$HTTP_CODE" -ge 500 ]; then
    ERRORS=$((ERRORS + 1))
    echo "[$(date '+%H:%M:%S')] #$COUNT  HTTP $HTTP_CODE  ✗  $BODY"
  else
    echo "[$(date '+%H:%M:%S')] #$COUNT  HTTP $HTTP_CODE  ✓"
  fi

  sleep "$INTERVAL"
done
