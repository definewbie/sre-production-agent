# Demo Services

## Overview

Step U introduces **three instrumented Spring Boot microservices** for local fault injection and observability validation. These services form a realistic microservice topology that integrates with the observability stack installed in Step T.

**Purpose:** Provide a safe, reproducible environment for RCA scenario validation without touching production systems.

---

## Service Topology

```
                    ┌─────────────────┐
                    │  Traffic Gen    │
                    │  (curl loop)    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  order-service   │  :8081
                    │  /checkout       │
                    └───┬─────────┬───┘
                        │         │
           ┌────────────▼───┐ ┌───▼────────────┐
           │ payment-service│ │inventory-service│
           │  /charge       │ │  /reserve       │
           │  :8082         │ │  :8083          │
           └────────────────┘ └─────────────────┘
```

**Call chain:** `traffic-generator → order-service → payment-service + inventory-service`

---

## Services

### order-service (port 8081)

- **Endpoint:** `POST /checkout` — orchestrates payment + inventory
- **Health:** `GET /health`
- **Metrics:** `GET /actuator/prometheus`
- **Fault Control:** `GET /fault` | `POST /fault`
- Calls payment-service `/charge` and inventory-service `/reserve`

### payment-service (port 8082)

- **Endpoint:** `POST /charge` — processes payment
- **Health:** `GET /health`
- **Metrics:** `GET /actuator/prometheus`
- **Fault Control:** `GET /fault` | `POST /fault`

### inventory-service (port 8083)

- **Endpoint:** `POST /reserve` — reserves inventory
- **Health:** `GET /health`
- **Metrics:** `GET /actuator/prometheus`
- **Fault Control:** `GET /fault` | `POST /fault`

---

## Observability Integration

Each service emits:

| Signal | Implementation | Collection |
|--------|---------------|------------|
| **Metrics** | Micrometer + Prometheus registry | ServiceMonitor → Prometheus |
| **Traces** | OpenTelemetry SDK → Jaeger | OTLP → Jaeger Collector |
| **Logs** | Structured JSON to stdout | Promtail → Loki |

### Prometheus Metrics

Standard Spring Boot metrics + custom business metrics:
- `http_server_requests_seconds` (latency histograms)
- `http_client_requests_seconds` (downstream latency)
- `checkout_requests_total` (business counter)
- `checkout_errors_total` (error counter)
- JVM / Tomcat / Actuator defaults

### ServiceMonitors

K8s ServiceMonitors are deployed to `demo` namespace, automatically discovered by Prometheus in `observability` namespace (kube-prometheus-stack cross-namespace scraping).

---

## Quick Start

```bash
# Build and load demo services into kind
make demo-build

# Deploy to kind cluster
make demo-deploy

# Start traffic generator
make demo-traffic

# Check all services healthy
make demo-check

# Port-forward for local access
make demo-port-forward
```

### Manual Commands

```bash
# Build Docker images
./scripts/demo-services/build.sh

# Load into kind
./scripts/demo-services/load.sh

# Deploy K8s manifests
./scripts/demo-services/deploy.sh

# Check status
./scripts/demo-services/check.sh

# Port-forward
./scripts/demo-services/port-forward.sh
```

---

## K8s Resources

All demo resources live in the `demo` namespace:

```text
k8s/demo/
├── namespace.yaml
├── order-service.yaml
├── payment-service.yaml
├── inventory-service.yaml
├── traffic-generator.yaml
└── servicemonitors.yaml
```

Each service deployment includes:
- Deployment (1 replica, with resource limits)
- Service (ClusterIP)
- ServiceMonitor (Prometheus scraping)

---

## Architecture Decisions

1. **Spring Boot 3.x** — consistent with sre-agent-server stack
2. **In-process fault injection** — no sidecar or service mesh dependency
3. **Runtime-controlled via REST API** — enables programmatic fault scenarios
4. **Standard Micrometer metrics** — compatible with any Prometheus setup
5. **OpenTelemetry for tracing** — vendor-neutral, forwards to Jaeger
6. **Structured JSON logging** — Loki/Promtail compatible out of the box
7. **Separate Maven module** — independent build lifecycle, zero impact on core modules

---

## Configuration

Environment variables for each service:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8081/8082/8083 | HTTP port |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger-collector.observability:4317` | Trace exporter |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,prometheus,info` | Actuator endpoints |

---

## Relationship to SRE Agent

Demo services are **not** part of the RCA engine. They are a validation environment:

- **Step V** will use demo services for complex live RCA scenarios
- Evidence providers (Prometheus, Loki, Jaeger) collect real signals from these services
- Fault injection triggers real metric spikes, error logs, and trace anomalies
- The SRE Agent's RCA workflow processes this evidence the same way as production

---

## Cleanup

```bash
make demo-uninstall
# or
./scripts/demo-services/uninstall.sh
```

This removes the `demo` namespace and all resources. The observability stack is not affected.
