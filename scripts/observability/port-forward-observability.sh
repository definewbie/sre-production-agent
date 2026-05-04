#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="observability"

echo "═══════════════════════════════════════════════════"
echo "  SRE Agent — Port Forward Observability Stack"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Starting port-forward for all observability endpoints..."
echo "Press Ctrl+C to stop all port-forwards."
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo "Stopping port-forwards..."
    jobs -p | xargs -r kill 2>/dev/null
    echo "Done."
}
trap cleanup EXIT

# Prometheus
kubectl -n "$NAMESPACE" port-forward svc/prometheus-operated 9090:9090 &
echo "✓ Prometheus   → http://localhost:9090 (PID: $!)"

# Alertmanager
kubectl -n "$NAMESPACE" port-forward svc/prometheus-operated 9093:9093 &
alertmanager_svc=$(kubectl -n "$NAMESPACE" get svc -o name 2>/dev/null | grep alertmanager | head -1 || echo "")
if [ -n "$alertmanager_svc" ]; then
    kubectl -n "$NAMESPACE" port-forward "$alertmanager_svc" 9093:9093 &
    echo "✓ Alertmanager → http://localhost:9093 (PID: $!)"
else
    echo "⚠ Alertmanager service not found"
fi

# Loki
loki_svc=$(kubectl -n "$NAMESPACE" get svc -o name 2>/dev/null | grep 'loki' | grep -v 'promtail' | head -1 || echo "")
if [ -n "$loki_svc" ]; then
    kubectl -n "$NAMESPACE" port-forward "$loki_svc" 3100:3100 &
    echo "✓ Loki         → http://localhost:3100 (PID: $!)"
else
    echo "⚠ Loki service not found"
fi

# Jaeger
jaeger_svc=$(kubectl -n "$NAMESPACE" get svc -o name 2>/dev/null | grep jaeger | head -1 || echo "")
if [ -n "$jaeger_svc" ]; then
    kubectl -n "$NAMESPACE" port-forward "$jaeger_svc" 16686:16686 &
    echo "✓ Jaeger       → http://localhost:16686 (PID: $!)"
else
    echo "⚠ Jaeger service not found"
fi

# Grafana
grafana_svc=$(kubectl -n "$NAMESPACE" get svc -o name 2>/dev/null | grep grafana | head -1 || echo "")
if [ -n "$grafana_svc" ]; then
    kubectl -n "$NAMESPACE" port-forward "$grafana_svc" 3000:80 &
    echo "✓ Grafana      → http://localhost:3000 (PID: $!)"
else
    echo "⚠ Grafana service not found"
fi

echo ""
echo "All port-forwards running. Press Ctrl+C to stop."
wait
