#!/usr/bin/env bash
set -euo pipefail

echo "=== Port-forwarding Demo Services ==="
echo "  order-service:    localhost:18081 → 8081"
echo "  payment-service:  localhost:18082 → 8082"
echo "  inventory-service: localhost:18083 → 8083"
echo ""
echo "Press Ctrl+C to stop all port-forwards."
echo ""

# Cleanup background processes on exit
cleanup() {
  echo ""
  echo "Stopping port-forwards..."
  kill $(jobs -p) 2>/dev/null || true
  exit 0
}
trap cleanup EXIT INT TERM

kubectl -n demo port-forward svc/order-service 18081:8081 &
kubectl -n demo port-forward svc/payment-service 18082:8082 &
kubectl -n demo port-forward svc/inventory-service 18083:8083 &

wait
