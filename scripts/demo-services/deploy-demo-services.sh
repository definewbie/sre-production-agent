#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
K8S_DIR="$PROJECT_ROOT/k8s/demo-services"

echo "=== Deploying Demo Services ==="

# Check if observability stack exists
if ! kubectl get namespace observability &>/dev/null; then
  echo "⚠  Warning: 'observability' namespace not found."
  echo "   Consider running 'make observability-install' first for full monitoring."
  echo ""
fi

# Ensure demo namespace exists with correct labels
echo "Applying namespace..."
kubectl apply -f "$PROJECT_ROOT/k8s/namespaces/demo.yaml"

# Apply all demo-service manifests
echo "Applying manifests..."
kubectl apply -f "$K8S_DIR/order-service.yaml"
kubectl apply -f "$K8S_DIR/payment-service.yaml"
kubectl apply -f "$K8S_DIR/inventory-service.yaml"
kubectl apply -f "$K8S_DIR/traffic-generator.yaml"

# Apply ServiceMonitor only if CRD exists
if kubectl get crd servicemonitors.monitoring.coreos.com &>/dev/null; then
  echo "Applying ServiceMonitor..."
  kubectl apply -f "$K8S_DIR/servicemonitors.yaml"
else
  echo "⚠  ServiceMonitor CRD not found — skipping. Install Prometheus Operator first."
fi

echo ""
echo "=== Waiting for rollouts ==="
kubectl -n demo rollout status deployment/order-service --timeout=120s
kubectl -n demo rollout status deployment/payment-service --timeout=120s
kubectl -n demo rollout status deployment/inventory-service --timeout=120s
kubectl -n demo rollout status deployment/traffic-generator --timeout=120s

echo ""
echo "✓ Demo services deployed:"
kubectl -n demo get pods -o wide
echo ""
kubectl -n demo get svc
