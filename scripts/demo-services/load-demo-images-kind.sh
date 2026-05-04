#!/usr/bin/env bash
set -euo pipefail

KIND_CLUSTER="${KIND_CLUSTER_NAME:-sre-agent}"

echo "=== Loading demo images into kind cluster '$KIND_CLUSTER' ==="

echo "Loading order-service..."
kind load docker-image sre-agent/order-service:local --name "$KIND_CLUSTER"

echo "Loading payment-service..."
kind load docker-image sre-agent/payment-service:local --name "$KIND_CLUSTER"

echo "Loading inventory-service..."
kind load docker-image sre-agent/inventory-service:local --name "$KIND_CLUSTER"

echo ""
echo "✓ All demo images loaded into kind cluster."
