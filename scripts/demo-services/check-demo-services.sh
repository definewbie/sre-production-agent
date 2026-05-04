#!/usr/bin/env bash
set -euo pipefail

echo "=== Demo Services Health Check ==="

echo ""
echo "--- Pod Status ---"
kubectl -n demo get pods -o wide -l scenario=demo-services

echo ""
echo "--- Service Endpoints ---"
kubectl -n demo get svc -l scenario=demo-services

echo ""
echo "--- Health Probes (requires port-forward) ---"

check_health() {
  local name="$1"
  local port="$2"
  local url="http://localhost:${port}/health"
  if curl -sf --max-time 3 "$url" >/dev/null 2>&1; then
    echo "  ✓ $name ($url) — OK"
  else
    echo "  ✗ $name ($url) — UNREACHABLE (run 'make demo-services-port-forward' first)"
  fi
}

check_health "order-service"    18081
check_health "payment-service"  18082
check_health "inventory-service" 18083

echo ""
echo "--- Recent Logs (last 5 lines each) ---"
for svc in order-service payment-service inventory-service; do
  echo ""
  echo "[$svc]"
  kubectl -n demo logs "deployment/$svc" --tail=5 2>/dev/null || echo "  (no pods found)"
done

echo ""
