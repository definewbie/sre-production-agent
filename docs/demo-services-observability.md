# Demo Services Observability Guide

## Overview

This document explains how demo services integrate with the observability stack (Prometheus, Loki, Jaeger, Grafana) deployed via Step T.

---

## Prerequisites

1. **Observability stack running** in `observability` namespace:
   ```bash
   make check-observability
   # or
   ./scripts/observability/check-observability.sh
   ```

2. **Demo services deployed** in `demo` namespace:
   ```bash
   make demo-deploy
   make demo-check
   ```

3. **Port-forwards active**:
   ```bash
   make port-forward-observability
   make demo-port-forward
   ```

---

## Signal Flow

```
┌──────────────────────────────────────────────────────────┐
│  Demo Services (demo namespace)                          │
│                                                          │
│  order-service ──┐                                       │
│  payment-service ├── /actuator/prometheus (metrics)      │
│  inventory-service├── stdout (structured JSON logs)      │
│                  └── OTLP → Jaeger (traces)              │
└────────┬──────────────┬────────────────┬─────────────────┘
         │              │                │
    ServiceMonitor  Promtail         OTLP Exporter
         │              │                │
         ▼              ▼                ▼
┌─────────────────────────────────────────────────────────┐
│  Observability Stack (observability namespace)          │
│                                                         │
│  Prometheus  ←── scrape /metrics via ServiceMonitor     │
│  Loki        ←── collect JSON logs via Promtail         │
│  Jaeger      ←── receive spans via OTLP                 │
│  Grafana     ←── dashboards querying all three          │
└─────────────────────────────────────────────────────────┘
```

---

## Metrics (Prometheus)

### Service Discovery

Prometheus discovers demo services via `ServiceMonitor` resources:

```yaml
# k8s/demo/servicemonitors.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: order-service
  namespace: demo
spec:
  selector:
    matchLabels:
      app: order-service
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 15s
```

### Key Metrics to Watch

| Metric | Source | What It Shows |
|--------|--------|---------------|
| `http_server_requests_seconds` | Spring Boot | Request latency histogram |
| `http_server_requests_seconds_count` | Spring Boot | Request count by status |
| `http_client_requests_seconds` | Spring Boot | Downstream call latency |
| `checkout_requests_total` | Custom | Business checkout counter |
| `jvm_memory_used_bytes` | JVM | Memory pressure |
| `process_cpu_usage` | JVM | CPU utilization |
| `tomcat_threads_busy_threads` | Tomcat | Thread pool saturation |

### Useful PromQL Queries

```promql
# Error rate by service
sum(rate(http_server_requests_seconds_count{status=~"5..",namespace="demo"}[5m]))
/
sum(rate(http_server_requests_seconds_count{namespace="demo"}[5m]))

# P95 latency by service
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{namespace="demo"}[5m])) by (le,uri))

# Downstream latency (order → payment)
histogram_quantile(0.95,
  sum(rate(http_client_requests_seconds_bucket{namespace="demo"}[5m])) by (le,uri))

# Request rate
sum(rate(http_server_requests_seconds_count{namespace="demo"}[5m])) by (uri)
```

### Verify Scrape

```bash
# Port-forward Prometheus
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] |
  select(.labels.namespace=="demo") |
  {scrapeUrl: .scrapeUrl, health: .health}'
```

---

## Logs (Loki)

### Log Format

Demo services output structured JSON logs:

```json
{
  "timestamp": "2026-05-04T12:00:00.000Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "abc123",
  "spanId": "def456",
  "message": "Checkout completed",
  "orderId": "ORD-001",
  "duration": 245
}
```

### LogQL Queries

```logql
# All logs from demo services
{namespace="demo"}

# Error logs from payment-service
{namespace="demo",app="payment-service"} |= "ERROR"

# Logs for a specific trace
{namespace="demo"} |= "traceId=abc123"

# Checkout latency spikes
{namespace="demo",app="order-service"} | json | duration > 1000
```

### Verify Collection

```bash
# Port-forward Loki
curl http://localhost:3100/loki/api/v1/query \
  --data-urlencode 'query={namespace="demo"}' \
  --data-urlencode 'limit=5'
```

---

## Traces (Jaeger)

### Trace Propagation

- **Format:** W3C TraceContext (`traceparent` header)
- **Export:** OTLP → Jaeger Collector at `jaeger-collector.observability:4317`
- **Sampling:** Always-on (demo environment only)

### What to Look For

| Scenario | Trace Signature |
|----------|----------------|
| Normal checkout | order → payment (fast) + inventory (fast) |
| Downstream latency | order → payment (slow), high span duration |
| Error cascade | order → payment (error span), HTTP 500 |
| Service unavailable | order → payment (no span), timeout |

### Jaeger UI

```bash
# Port-forward Jaeger
# Open http://localhost:16686
# Service filter: "order-service" or "payment-service"
# Look for traces with > 3 spans (checkout flow)
```

### Verify Collection

```bash
curl http://localhost:16686/api/services | jq '.data.services'
# Should include: order-service, payment-service, inventory-service
```

---

## Grafana Dashboards

### Pre-built Dashboards

The kube-prometheus-stack includes default dashboards:
- **Kubernetes / Compute Resources / Pod** — CPU/Memory per pod
- **Spring Boot Statistics** — if Grafana dashboard provisioned

### Custom Dashboard for Demo Services

Create a dashboard with these panels:

1. **Request Rate** — `rate(http_server_requests_seconds_count{namespace="demo"}[5m])`
2. **Error Rate** — 5xx ratio
3. **P95 Latency** — by service
4. **Downstream Latency** — client call duration
5. **JVM Memory** — by service
6. **Active Faults** — from `/fault` endpoint

### Access Grafana

```bash
# Port-forward Grafana
# Open http://localhost:3000
# Default credentials: admin / prom-operator
```

---

## End-to-End Verification

Run this checklist after deploying demo services:

```bash
# 1. Services healthy
kubectl get pods -n demo
# All pods Running, 0 restarts

# 2. Prometheus scraping
curl -s http://localhost:9090/api/v1/query \
  --data-urlencode 'query=up{namespace="demo"}' | jq '.data.result[].value[1]'
# Should return "1" for each service

# 3. Loki receiving logs
curl -s http://localhost:3100/loki/api/v1/query \
  --data-urlencode 'query={namespace="demo"}' \
  --data-urlencode 'limit=1' | jq '.data.result'
# Should return log entries

# 4. Jaeger receiving traces
curl -s http://localhost:16686/api/services | jq '.data.services'
# Should list demo services

# 5. Generate traffic and verify
curl -X POST http://localhost:8081/checkout \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"test-001","amount":99.99,"items":[{"sku":"SKU-001","quantity":1}]}'

# Wait 30s for scrape, then check metrics
curl -s http://localhost:9090/api/v1/query \
  --data-urlencode 'query=checkout_requests_total{namespace="demo"}' | jq '.data.result'
```

---

## Troubleshooting

### Prometheus not scraping demo services

1. Check ServiceMonitor exists: `kubectl get servicemonitor -n demo`
2. Check Prometheus targets: `http://localhost:9090/targets`
3. Verify label match: ServiceMonitor selector must match Service labels
4. Check cross-namespace: Prometheus must allow scraping from `demo` namespace

### Loki not receiving logs

1. Check Promtail is running: `kubectl get pods -n observability -l app=promtail`
2. Check Promtail config includes `demo` namespace
3. Verify log format is valid JSON

### Jaeger not receiving traces

1. Check Jaeger Collector is running: `kubectl get pods -n observability -l app=jaeger`
2. Verify OTEL endpoint: `http://jaeger-collector.observability:4317`
3. Check service logs for OTLP export errors

### No metrics in Grafana

1. Check Prometheus datasource: Settings → Data Sources → Prometheus
2. Verify Loki datasource: Settings → Data Sources → Loki
3. Try manual query in Explore view
