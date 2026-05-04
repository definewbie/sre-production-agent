# Fault Injection

## Overview

Each demo service exposes a `/fault` REST API for runtime fault injection. This allows controlled chaos engineering without restarting services or modifying configuration.

**Design principle:** Faults are per-service, in-process, and fully reversible.

---

## Fault Types

### 1. Latency Injection

Adds artificial delay to request processing.

```json
// Enable latency on payment-service
POST /fault
{
  "type": "latency",
  "enabled": true,
  "delayMs": 2000
}
```

**Effect:** All requests to this service sleep for `delayMs` before processing.

**Use cases:**
- Simulate downstream latency spikes (Scenario E)
- Test p95/p99 latency alerting
- Validate SLO breach detection

### 2. Error Injection

Returns HTTP errors for a percentage of requests.

```json
// Enable 50% error rate on inventory-service
POST /fault
{
  "type": "error",
  "enabled": true,
  "errorRate": 0.5,
  "statusCode": 500
}
```

**Effect:** `errorRate` fraction of requests return the configured `statusCode`.

**Use cases:**
- Simulate error rate spikes
- Test error budget burn rate alerts
- Validate error-based RCA evidence

### 3. Crash Simulation

Forces the service to become unresponsive.

```json
// Simulate crash on order-service
POST /fault
{
  "type": "crash",
  "enabled": true
}
```

**Effect:** Service returns 503 for all requests (simulates unavailable state).

**Use cases:**
- Simulate service outage
- Test CircuitBreaker patterns
- Validate CrashLoopBackOff-like scenarios

---

## API Reference

### Get Current Fault Configuration

```
GET /fault
```

Response:
```json
{
  "type": "latency",
  "enabled": true,
  "delayMs": 2000,
  "errorRate": 0.0,
  "statusCode": 500
}
```

### Set Fault Configuration

```
POST /fault
Content-Type: application/json

{
  "type": "latency" | "error" | "crash",
  "enabled": true | false,
  "delayMs": 2000,        // for latency type
  "errorRate": 0.5,       // for error type (0.0-1.0)
  "statusCode": 500       // for error type
}
```

### Clear All Faults

```
POST /fault
{
  "type": "none",
  "enabled": false
}
```

---

## Usage Examples

### Scenario: Downstream Latency Spike

```bash
# Inject 2s latency on payment-service
curl -X POST http://localhost:8082/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"latency","enabled":true,"delayMs":2000}'

# Generate traffic through order-service
# order-service → payment-service will see p95 > 2s

# Clear fault
curl -X POST http://localhost:8082/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"latency","enabled":false}'
```

### Scenario: Error Rate Spike

```bash
# Inject 30% error rate on inventory-service
curl -X POST http://localhost:8083/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"error","enabled":true,"errorRate":0.3,"statusCode":500}'

# Clear fault
curl -X POST http://localhost:8083/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"error","enabled":false}'
```

### Via SRE Agent Server Proxy

```bash
# Proxy through SRE Agent server
curl -X POST http://localhost:8080/api/demo/services/payment-service/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"latency","enabled":true,"delayMs":2000}'

# Check service status
curl http://localhost:8080/api/demo/services
```

---

## SRE Agent UI

The Demo Services page in the SRE Agent Web UI provides a visual interface for fault injection:

1. **Service cards** show current health and fault state
2. **Topology view** shows service call graph with latency annotations
3. **Fault control panel** allows enabling/disabling faults with a form
4. **Status indicators** show green (healthy) / red (fault active)

Access: `http://localhost:8080` → "Demo Services" tab

---

## Safety

- Faults are **runtime only** — service restart clears all faults
- Faults are **per-service** — no cross-service cascading by default
- Faults are **reversible** — POST `/fault` with `enabled: false` to clear
- **No data corruption** — faults only affect response timing/status
- **No resource exhaustion** — crash simulation returns 503, does not kill the process
- **Cluster-scoped** — port-forward required for local access; no external exposure

---

## Implementation

Each service uses a `FaultInjectionFilter` (Spring WebFilter):

```text
Request → FaultInjectionFilter → Controller
              │
              ├─ if latency: Thread.sleep(delayMs)
              ├─ if error: random check → return statusCode
              └─ if crash: return 503 immediately
```

The filter is registered as a `@Component` with highest priority (`Ordered.HIGHEST_PRECEDENCE + 1`).

Fault state is held in an in-memory `AtomicReference<FaultConfig>` — thread-safe, no shared state between instances.

---

## Metrics Impact

When faults are active, you should see:

| Fault Type | Metric Impact |
|------------|--------------|
| Latency | `http_server_requests_seconds` p95/p99 spike |
| Error | `http_server_requests_seconds_count{status=5xx}` spike |
| Crash | `http_server_requests_seconds_count{status=503}` spike, health check failure |

These metrics flow through Prometheus → SRE Agent evidence pipeline.
