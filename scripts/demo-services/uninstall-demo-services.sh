#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
K8S_DIR="$PROJECT_ROOT/k8s/demo-services"

echo "=== Uninstalling Demo Services ==="

kubectl delete -f "$K8S_DIR/traffic-generator.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/order-service.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/payment-service.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/inventory-service.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/servicemonitors.yaml" --ignore-not-found=true

echo ""
echo "✓ Demo services uninstalled."
kubectl -n demo get pods 2>/dev/null || echo "  (namespace 'demo' is empty or gone)"
