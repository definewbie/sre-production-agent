# Prometheus Metrics Evidence Provider (Step M)

## Overview

Step M introduces `sre-agent-prometheus-provider` — a dedicated adapter module that collects **metric evidence** from Prometheus and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This is the first observability signal provider beyond Kubernetes resource evidence, enabling the agent to reason about time-series anomalies such as error rate spikes, latency degradation, and resource saturation.

**Key design invariant:** `sre-agent-core` has zero Prometheus dependency. The Prometheus provider is a pure adapter that translates Prometheus query results into the domain model's `Evidence` record.

---

## Module Structure

```
sre-agent-prometheus-provider/
├── pom.xml
└── src/main/java/ai/sreagent/prometheus/
    ├── client/
    │   ├── PrometheusQueryClient.java          ← Interface (SPI)
    │   ├── FixturePrometheusQueryClient.java   ← Fixture-based (tests, CI)
    │   └── HttpPrometheusQueryClient.java      ← HTTP client (production)
    ├── parser/
    │   └── PrometheusResponseParser.java       ← Parses vector/range results
    ├── query/
    │   └── PrometheusQueryTemplateRegistry.java← Query template registry
    ├── mapper/
    │   └── PrometheusEvidenceMapper.java       ← Maps results → Evidence
    └── provider/
        └── PrometheusEvidenceProvider.java     ← Orchestrator
```

### Maven Coordinates

The module is declared as a sibling of the existing provider modules:

```
sre-production-agent (parent POM)
├── sre-agent-core
├── sre-agent-llm
├── sre-agent-k8s-provider
├── sre-agent-prometheus-provider   ← NEW
├── sre-agent-cli
└── sre-agent-server
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 sre-agent-prometheus-provider                │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          PrometheusEvidenceProvider (orchestrator)    │    │
│  │                                                       │    │
│  │   1. Resolve query template (by type)                │    │
│  │   2. Execute query via PrometheusQueryClient         │    │
│  │   3. Parse raw response via PrometheusResponseParser │    │
│  │   4. Map to Evidence via PrometheusEvidenceMapper    │    │
│  │                                                       │    │
│  └───┬──────────────┬──────────────┬──────────────┬─────┘    │
│       ↓              ↓              ↓              ↓          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Client   │  │ Parser   │  │ Query    │  │ Mapper   │    │
│  │ (SPI)    │  │          │  │ Templates│  │          │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│       ↑                                                      │
│  ┌────┴─────────────────────────────┐                        │
│  │  FixturePrometheusQueryClient    │  ← Tests / CI          │
│  │  HttpPrometheusQueryClient       │  ← Production          │
│  └──────────────────────────────────┘                        │
│                                                              │
│  Output: List<Evidence> (source = "prometheus")              │
│  Zero Spring dependency · Zero core dependency on Prometheus │
└─────────────────────────────────────────────────────────────┘
          │
          ↓
   ┌──────────────┐
   │ sre-agent-core│   ← Consumes Evidence, unaware it came from Prometheus
   └──────────────┘
```

**Data flow:**

```
CLI / Server
  ↓  (service, namespace, queryType, reader mode)
PrometheusEvidenceProvider.collect()
  ↓  resolve template → substitute labels
PrometheusQueryClient.query(promql, start, end, step)
  ↓  raw JSON response
PrometheusResponseParser.parse(response)
  ↓  list of (metric labels + value + timestamp)
PrometheusEvidenceMapper.map(parsedResults, queryType, service, namespace)
  ↓
List<Evidence>  (source = "prometheus")
```

---

## Client Abstraction

### `PrometheusQueryClient` (Interface)

The SPI that decouples the provider from any specific Prometheus access mechanism:

```java
public interface PrometheusQueryClient {
    PrometheusQueryResult query(String promql, Instant start, Instant end, Duration step);
    String getName();  // e.g. "fixture", "http"
}
```

### `FixturePrometheusQueryClient`

Loads pre-canned Prometheus query responses from classpath fixture files. Used by:
- **Unit tests** — deterministic, no network required
- **CI pipelines** — fast, no Prometheus dependency
- **Demo scenarios** — reproducible results

### `HttpPrometheusQueryClient`

Executes real PromQL queries against a Prometheus HTTP API endpoint. Used by:
- **Production** — connects to an actual Prometheus instance
- **Local development** — connects to a local Prometheus or Thanos

Requires `--prometheus-url` to be specified on the CLI.

---

## Parser

### `PrometheusResponseParser`

Handles the Prometheus HTTP API JSON response format, normalizing various edge cases:

| Scenario | Behavior |
|----------|----------|
| **Instant vector** (`resultType: "vector"`) | Extracts current values from `result[].value[]` |
| **Range vector** (`resultType: "matrix"`) | Extracts all data points from `result[].values[][]` |
| **NaN values** | Filtered out — treated as no data |
| **+Inf / -Inf** | Filtered out — treated as invalid |
| **Empty result set** (`result: []`) | Returns empty list (maps to `metric_no_signal` evidence) |
| **Missing fields** | Gracefully skipped with null checks |

The parser outputs a list of `ParsedPrometheusMetric` records containing metric labels, numeric value, and timestamp — clean, validated data for the mapper to consume.

---

## Query Template Registry

### `PrometheusQueryTemplateRegistry`

Maps semantic query types to PromQL templates. Templates contain placeholders (`{{service}}`, `{{namespace}}`) that are resolved at query time.

| Query Type | PromQL Template (example) | Semantic Meaning |
|---|---|---|
| `ERROR_RATE` | `sum(rate(http_requests_total{service="{{service}}",namespace="{{namespace}}",code=~"5.."}[5m])) / sum(rate(http_requests_total{service="{{service}}",namespace="{{namespace}}"}[5m]))` | 5xx error rate |
| `LATENCY_P95` | `histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{service="{{service}}",namespace="{{namespace}}"}[5m])) by (le))` | 95th percentile latency |
| `LATENCY_P99` | `histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{service="{{service}}",namespace="{{namespace}}"}[5m])) by (le))` | 99th percentile latency |
| `DOWNSTREAM_LATENCY_P95` | `histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{service="{{service}}",namespace="{{namespace}}"}[5m])) by (le, downstream_service))` | Downstream dependency latency |
| `MEMORY_USAGE` | `container_memory_working_set_bytes{pod=~"{{service}}-.*",namespace="{{namespace}}"} / container_spec_memory_limit_bytes{pod=~"{{service}}-.*",namespace="{{namespace}}"}` | Container memory utilization |
| `CPU_USAGE` | `sum(rate(container_cpu_usage_seconds_total{pod=~"{{service}}-.*",namespace="{{namespace}}"}[5m])) by (pod)` | Container CPU usage |
| `RESTART_RATE` | `sum(rate(kube_pod_container_status_restarts_total{pod=~"{{service}}-.*",namespace="{{namespace}}"}[1h]))` | Pod restart rate |
| `REQUEST_RATE` | `sum(rate(http_requests_total{service="{{service}}",namespace="{{namespace}}"}[5m]))` | Request throughput |

> **Important caveat:** The PromQL templates above are **environment-specific examples**. Metric names, label names, and label values vary across observability stacks (kube-prometheus-stack vs. custom vs. Thanos vs. VictoriaMetrics). The registry is designed to be extended or overridden per deployment environment. In a production deployment, these templates would be externalized to configuration files.

---

## Evidence Types and Mapping Policy

### `PrometheusEvidenceMapper`

The mapper translates parsed Prometheus results into semantic `Evidence` types that the core RCA pipeline can match against diagnostic patterns.

| Metric Condition | Evidence Type | Description |
|---|---|---|
| Error rate exceeds threshold | `metric_error_rate_spike` | 5xx rate anomaly detected |
| P95 latency exceeds threshold | `metric_latency_p95_spike` | Tail latency degradation |
| P99 latency exceeds threshold | `metric_latency_p99_spike` | Extreme tail latency |
| Downstream latency exceeds threshold | `metric_downstream_latency_spike` | Dependency latency issue |
| Memory usage exceeds threshold | `metric_memory_usage_high` | Memory pressure detected |
| CPU usage exceeds threshold | `metric_cpu_usage_high` | CPU saturation detected |
| Restart rate exceeds threshold | `metric_restart_rate_increased` | Pod instability detected |
| Request rate drops below threshold | `metric_request_rate_drop` | Traffic anomaly / possible outage |
| No data returned (empty result) | `metric_no_signal` | Prometheus has no data for this query |

### Mapping Policy

The mapper applies threshold-based classification:

1. **Values above/below threshold** → spike/high/drop evidence type
2. **Values within normal range** → evidence is still produced but with `severity: "normal"` to inform the pipeline that no anomaly was detected
3. **Empty result set** → `metric_no_signal` evidence (important for the pipeline to distinguish "no anomaly" from "no data")

All produced evidence carries:
- `source: "prometheus"` — identifies the evidence origin
- `timestamp` — from the Prometheus data point
- `metricValue` — the actual numeric value
- `threshold` — the threshold that was applied
- `labels` — relevant Prometheus metric labels

---

## Provider Orchestrator

### `PrometheusEvidenceProvider`

The top-level component that orchestrates the full collection pipeline:

```java
public class PrometheusEvidenceProvider {
    public List<Evidence> collect(String service, String namespace, String queryType,
                                  Instant start, Instant end, Duration step) {
        // 1. Resolve PromQL from template registry
        String promql = templateRegistry.resolve(queryType, service, namespace);

        // 2. Execute query via client SPI
        PrometheusQueryResult result = client.query(promql, start, end, step);

        // 3. Parse raw response
        List<ParsedPrometheusMetric> parsed = parser.parse(result);

        // 4. Map to evidence
        return mapper.map(parsed, queryType, service, namespace);
    }
}
```

---

## CLI Usage

### Fixture Mode (default, no Prometheus required)

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar collect-prometheus-evidence \
  --service payment-service \
  --namespace demo \
  --query-type LATENCY_P95 \
  --output examples/evidence/prometheus_payment_latency.json \
  --reader fixture
```

### HTTP Mode (requires live Prometheus)

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar collect-prometheus-evidence \
  --service payment-service \
  --namespace demo \
  --query-type LATENCY_P95 \
  --output examples/evidence/prometheus_payment_latency.json \
  --reader http \
  --prometheus-url http://localhost:9090
```

### CLI Options

| Flag | Required | Description |
|------|----------|-------------|
| `--service` | Yes | Target service name (used in PromQL label matching) |
| `--namespace` | Yes | Kubernetes namespace (used in PromQL label matching) |
| `--query-type` | Yes | One of: `ERROR_RATE`, `LATENCY_P95`, `LATENCY_P99`, `DOWNSTREAM_LATENCY_P95`, `MEMORY_USAGE`, `CPU_USAGE`, `RESTART_RATE`, `REQUEST_RATE` |
| `--output` | Yes | Output file path for collected evidence JSON |
| `--reader` | No | `fixture` (default) or `http` |
| `--prometheus-url` | Only with `--reader http` | Prometheus HTTP API URL (e.g. `http://localhost:9090`) |

---

## Testing Approach

The Prometheus provider uses **fixture-based testing** — no live Prometheus instance is required for any test.

### Test Structure

| Test Class | What It Tests | Count |
|---|---|---|
| `PrometheusResponseParserTest` | Parser edge cases (vector/range, NaN/+Inf, empty results, missing fields) | ~12 |
| `PrometheusQueryTemplateRegistryTest` | Template resolution, placeholder substitution, unknown type handling | ~6 |
| `PrometheusEvidenceMapperTest` | Threshold-based evidence mapping, no-signal detection | ~10 |
| `PrometheusEvidenceProviderTest` | End-to-end orchestration with fixture client | ~5 |
| `FixturePrometheusQueryClientTest` | Fixture loading correctness | ~4 |
| `HttpPrometheusQueryClientTest` | HTTP client construction (no live calls in tests) | ~6 |

**Total: 43 new tests** (229 project-wide, up from 186).

### Key Test Scenarios

- **NaN handling:** Parser correctly filters NaN values from Prometheus results
- **+Inf handling:** Parser correctly filters infinite values
- **Empty result set:** Produces `metric_no_signal` evidence
- **Range vector parsing:** Multi-point range data is correctly parsed
- **Template substitution:** `{{service}}` and `{{namespace}}` placeholders are correctly resolved
- **Unknown query type:** Returns empty result (no crash)
- **Fixture client:** Returns deterministic data from classpath JSON files

### Running Tests

```bash
# All tests including Prometheus provider
mvn test

# Only Prometheus provider tests
mvn test -pl sre-agent-prometheus-provider

# Expected: 229 tests passing
```

---

## Known Limitations

1. **PromQL templates are environment-specific** — The built-in templates assume standard kube-prometheus-stack metric naming. Production deployments may need custom templates matching their observability stack.

2. **Single query per invocation** — Each `collect-prometheus-evidence` call executes one query type. Multi-signal collection requires multiple calls or a future batch mode.

3. **No anomaly detection** — The mapper uses static thresholds. Dynamic anomaly detection (e.g., comparing against historical baselines, seasonal adjustment) is a future capability.

4. **No rate-of-change analysis** — The provider evaluates point-in-time values against thresholds. Detecting sudden spikes in the rate-of-change of a metric requires future work.

5. **Fixture responses are hand-crafted** — Fixture JSON files represent idealized Prometheus responses. They may not cover all real-world response variations.

6. **No multi-service correlation** — Each evidence collection targets a single service. Cross-service metric correlation (e.g., "payment-service latency spike correlates with order-service error rate spike") is deferred to a future step.

7. **No authentication on HTTP client** — `HttpPrometheusQueryClient` currently assumes an unauthenticated Prometheus endpoint. Production deployments may need bearer token or basic auth support.

---

## Relationship to Future Steps

### Step R: LLM Hypothesis Proposer

The Prometheus evidence provider is a key input source for the planned LLM Hypothesis Proposer (Step R). The flow will be:

```
PrometheusEvidenceProvider  →  List<Evidence>
                                      ↓
LLM Hypothesis Proposer (Step R)  →  Additional hypotheses based on metric patterns
                                      ↓
Existing RCA Pipeline (Verification → Scoring → Decision)
```

The metric evidence types (`metric_error_rate_spike`, `metric_latency_p95_spike`, etc.) are designed to be consumable by both the deterministic pattern-matching engine and the future LLM-based hypothesis proposer.

### Step N: Loki Logs Evidence Provider

Loki (Step N) will follow the same adapter pattern:
- `LokiQueryClient` (interface) → `FixtureLokiQueryClient` / `HttpLokiQueryClient`
- `LokiResponseParser`
- `LokiEvidenceMapper`
- Evidence types: `log_error_burst`, `log_exception_pattern`, `log_timeout_pattern`, etc.

Combined Prometheus + Loki evidence will enable much stronger RCA — the agent can correlate metric spikes with log anomalies.

### Step Q: Observability Taxonomy

Step Q will formalize the taxonomy of observability evidence types across all providers (K8s, Prometheus, Loki, Alertmanager, Traces), establishing a unified vocabulary for evidence types that all diagnostic patterns reference.

---

## Design Decisions

### Why a Separate Module?

Following the same pattern as `sre-agent-k8s-provider`, the Prometheus provider is isolated in its own Maven module to:
- Keep `sre-agent-core` free of any Prometheus dependency
- Allow the module to be excluded from builds that don't need metric evidence
- Make the dependency graph explicit: `core ← prometheus-provider`

### Why Fixture-Based Testing?

The same rationale as the K8s provider:
- **CI reliability** — no external service dependency
- **Determinism** — same input → same output, every time
- **Speed** — no network round-trips
- **Coverage** — fixtures can simulate edge cases (NaN, empty results) that are hard to reproduce with a live Prometheus

### Why Threshold-Based Mapping?

Static thresholds are the simplest mapping policy that produces useful evidence. Future enhancements may include:
- Dynamic baselines (rolling average ± N standard deviations)
- Seasonal decomposition
- Machine-learned anomaly scores

The mapper interface is designed to accommodate these without changing the provider or parser.

### Why Generic Evidence Types?

The provider outputs generic `Evidence` records (with `source = "prometheus"`) rather than Prometheus-specific types. This ensures:
- The core RCA pipeline remains data-source agnostic
- Evidence from different providers (K8s, Prometheus, Loki) can be combined in a single investigation
- New evidence sources can be added without modifying the core pipeline
