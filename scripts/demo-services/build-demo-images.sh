#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT/demo-services"

echo "=== Building demo-service JARs ==="
mvn package -DskipTests

echo ""
echo "=== Building Docker images ==="
docker build -t sre-agent/order-service:local order-service/
docker build -t sre-agent/payment-service:local payment-service/
docker build -t sre-agent/inventory-service:local inventory-service/

echo ""
echo "✓ All demo images built:"
docker images | grep 'sre-agent/' | grep 'local'
