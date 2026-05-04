#!/usr/bin/env bash
set -euo pipefail

echo "═══════════════════════════════════════════════════"
echo "  SRE Agent — Observability Stack Health Check"
echo "═══════════════════════════════════════════════════"
echo ""

check_endpoint() {
    local name="$1"
    local url="$2"
    local status=""
    local latency=""

    result=$(curl -s -o /dev/null -w "%{http_code} %{time_total}" --max-time 3 "$url" 2>/dev/null || echo "000 0.000")
    http_code=$(echo "$result" | awk '{print $1}')
    time_total=$(echo "$result" | awk '{print $2}')

    if [ "$http_code" = "000" ]; then
        printf "  %-15s %-35s ✗ UNREACHABLE\n" "$name" "$url"
    elif [ "$http_code" -ge 200 ] && [ "$http_code" -lt 500 ]; then
        printf "  %-15s %-35s ✓ OK (%sms)\n" "$name" "$url" "$(echo "$time_total * 1000" | bc | cut -c1-6)"
    else
        printf "  %-15s %-35s ✗ HTTP %s\n" "$name" "$url" "$http_code"
    fi
}

echo "Endpoint checks:"
check_endpoint "Kubernetes"   "http://localhost:8080/api/v1/nodes" 2>/dev/null || true
check_endpoint "Prometheus"   "http://localhost:9090/-/ready"
check_endpoint "Alertmanager" "http://localhost:9093/-/ready"
check_endpoint "Loki"         "http://localhost:3100/ready"
check_endpoint "Jaeger"       "http://localhost:16686/api/services"
check_endpoint "Grafana"      "http://localhost:3000/api/health"

echo ""
echo "Cluster pods:"
kubectl -n observability get pods -o wide 2>/dev/null || echo "  (kind cluster not reachable)"
echo ""
echo "Helm releases:"
helm list -n observability 2>/dev/null || echo "  (no releases found)"
