#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
VALUES_DIR="$PROJECT_DIR/k8s/observability"
NAMESPACE="observability"

echo "═══════════════════════════════════════════════════"
echo "  SRE Agent — Observability Stack Installation"
echo "═══════════════════════════════════════════════════"
echo ""

# Check prerequisites
for cmd in kind kubectl helm; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: $cmd not found. Please install it first."
        exit 1
    fi
done

# Check kind cluster
if ! kind get clusters 2>/dev/null | grep -q "sre-agent"; then
    echo "ERROR: kind cluster 'sre-agent' not found."
    echo "Run 'make cluster-up' first."
    exit 1
fi

# Ensure namespace
echo "→ Ensuring namespace '$NAMESPACE' exists..."
kubectl apply -f "$PROJECT_DIR/k8s/namespaces/observability.yaml"

# Add Helm repos
echo "→ Adding Helm repositories..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo add grafana https://grafana.github.io/helm-charts 2>/dev/null || true
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts 2>/dev/null || true
helm repo update

# Install kube-prometheus-stack (Prometheus + Alertmanager + Grafana)
echo ""
echo "→ Installing kube-prometheus-stack..."
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
    --namespace "$NAMESPACE" \
    --values "$VALUES_DIR/prometheus-values.yaml" \
    --wait --timeout 5m

echo "✓ Prometheus + Alertmanager + Grafana installed"

# Install Loki + Promtail
echo ""
echo "→ Installing Loki + Promtail..."
helm upgrade --install loki grafana/loki \
    --namespace "$NAMESPACE" \
    --values "$VALUES_DIR/loki-values.yaml" \
    --wait --timeout 5m

echo "✓ Loki + Promtail installed"

# Install Jaeger
echo ""
echo "→ Installing Jaeger..."
helm upgrade --install jaeger jaegertracing/jaeger \
    --namespace "$NAMESPACE" \
    --values "$VALUES_DIR/jaeger-values.yaml" \
    --wait --timeout 5m

echo "✓ Jaeger installed"

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Observability Stack Installed!"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Pods:"
kubectl -n "$NAMESPACE" get pods -o wide
echo ""
echo "Next steps:"
echo "  make observability-port-forward   # Start port-forwarding"
echo "  make observability-check           # Verify endpoints"
