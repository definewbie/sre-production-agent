#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="observability"

echo "═══════════════════════════════════════════════════"
echo "  SRE Agent — Uninstall Observability Stack"
echo "═══════════════════════════════════════════════════"

echo "→ Uninstalling Jaeger..."
helm uninstall jaeger --namespace "$NAMESPACE" 2>/dev/null || echo "  (not installed)"

echo "→ Uninstalling Loki..."
helm uninstall loki --namespace "$NAMESPACE" 2>/dev/null || echo "  (not installed)"

echo "→ Uninstalling Prometheus stack..."
helm uninstall prometheus --namespace "$NAMESPACE" 2>/dev/null || echo "  (not installed)"

echo ""
echo "Remaining resources in $NAMESPACE:"
kubectl -n "$NAMESPACE" get all 2>/dev/null || true

echo ""
echo "✓ Observability stack uninstalled."
echo "  Namespace '$NAMESPACE' preserved (use 'kubectl delete ns $NAMESPACE' to remove)"
