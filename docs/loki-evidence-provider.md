# Loki Logs Evidence Provider (Step N)

## Overview

Step N introduces `sre-agent-loki-provider` — a dedicated adapter module that collects **log evidence** from Grafana Loki and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This complements the Prometheus metric provider (Step M) by adding log-line-level observability, enabling the agent to reason about error bursts, timeout patterns, OOM messages, and crash indicators found in application logs.

**Key design invariant:** `sre-agent-core` has zero Loki dependency. The Loki provider is a pure adapter that translates Loki query results into the domain model's `Evidence` record.

---

## Module Structure

```
sre-agent-loki-provider/
├── pom.xml
└── src/main/java/ai/sreagent/loki/
    ├── client/
    │   ├── LokiQueryClient.java              ← Interface (SPI)
    │   ├── LokiClientConfig.java             ← Config record
    │   ├── FixtureLokiQueryClient.java       ← Fixture-based (tests, CI)
    │   └── HttpLokiQueryClient.java          ← HTTP client (production)
    ├── parser/
    │   ├── LokiLogEntry.java                 ← Parsed log entry record
    │   ├── LokiQueryResult.java              ← Parsed query result record
    │   └── LokiResponseParser.java           ← Parses Loki API JSON
    ├── query/
    │   ├── LokiQueryType.java                ← 8 semantic query types
    │   ├── LokiQueryTemplate.java            ← LogQL template with substitution
    │   └── LokiQueryTemplateRegistry.java    ← Query template registry
    ├── mapper/
    │   ├── LokiEvidenceTypes.java            ← Evidence type constants
    │   └── LokiEvidenceMapper.java           ← Maps log patterns → Evidence
    ├── LokiEvidenceRequest.java              ← Provider request record
    ├── LokiEvidenceResult.java               ← Provider result record
    └── LokiEvidenceProvider.java             ← Orchestrator
```

### Maven Coordinates

```
sre-production-agent (parent POM)
├── sre-agent-core
├── sre-agent-llm
├── sre-agent-k8s-provider
├── sre-agent-prometheus-provider
├── sre-agent-loki-provider          ← NEW
├── sre-agent-cli
└── sre-agent-server
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  sre-agent-loki-provider                      │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │          LokiEvidenceProvider (orchestrator)          │     │
│  │                                                       │     │
│  │   1. Resolve query template (by type)                │     │
│  │   2. Execute LogQL via LokiQueryClient               │     │
│  │   3. Parse raw response via LokiResponseParser       │     │
│  │   4. Map to Evidence via LokiEvidenceMapper          │     │
│  │                                                       │     │
│  └───┬──────────────┬──────────────┬──────────────┬─────┘    │
│       ↓              ↓              ↓              ↓          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Client   │  │ Parser   │  │ Query    │  │ Mapper   │    │
│  │ (SPI)    │  │          │  │ Templates│  │          │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│       ↑                                                      │
│  ┌────┴─────────────────────────────┐                        │
│  │  FixtureLokiQueryClient         │  ← Tests / CI          │
│  │  HttpLokiQueryClient            │  ← Production          │
│  └──────────────────────────────────┘                        │
│                                                               │
│  Output: List<Evidence> (source = "loki")                     │
│  Zero Spring dependency · Zero core dependency on Loki        │
└─────────────────────────────────────────────────────────────┘
          │
          ↓
   ┌──────────────┐
   │ sre-agent-core│   ← Consumes Evidence, unaware it came from Loki
   └──────────────┘
```

**Data flow:**

```
CLI / Server
  ↓  (service, namespace, queryType, reader mode)
LokiEvidenceProvider.collect()
  ↓  resolve template → substitute labels
LokiQueryClient.query(logql, start, end, limit)
  ↓  raw JSON response
LokiResponseParser.parse(response)
  ↓  list of (log line + labels + timestamp)
LokiEvidenceMapper.map(parsedResults, queryType, service, namespace)
  ↓
List<Evidence>  (source = "loki")
```

---

## Client Abstraction

### `LokiQueryClient` (Interface)

```java
public interface LokiQueryClient {
    String query(String logql, Instant start, Instant end, int limit);
    String queryRange(String logql, Instant start, Instant end, Duration step, int limit);
}
```

### `FixtureLokiQueryClient`

Loads pre-canned Loki query responses from classpath fixture files. Used by:
- **Unit tests** — deterministic, no network required
- **CI pipelines** — fast, no Loki dependency
- **Demo scenarios** — reproducible results

### `HttpLokiQueryClient`

Executes real LogQL queries against a Loki HTTP API endpoint. Used by:
- **Production** — connects to an actual Loki instance
- **Local development** — connects to a local Loki or Grafana Loki

Requires `--loki-url` to be specified on the CLI.

---

## Parser

### `LokiResponseParser`

Handles the Loki HTTP API JSON response format:

| Scenario | Behavior |
|----------|----------|
| **Stream result** (`resultType: "streams"`) | Extracts log lines from `result[].values[][]` with nanosecond timestamps |
| **Nanosecond timestamps** | Correctly converts nanosecond epoch to `Instant` |
| **Empty result set** (`result: []`) | Returns empty list (maps to `log_no_signal` evidence) |
| **Error response** (`status: "error"`) | Returns empty list gracefully |
| **Missing fields** | Gracefully skipped with null checks |

---

## Query Template Registry

### `LokiQueryTemplateRegistry`

Maps semantic query types to LogQL templates. Templates contain placeholders (`{{service}}`, `{{namespace}}`) that are resolved at query time.

| Query Type | LogQL Template (example) | Semantic Meaning |
|---|---|---|
| `TIMEOUT_ERROR` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)timeout\|timed out"` | Timeout error log lines |
| `DOWNSTREAM_TIMEOUT` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)downstream.*timeout\|upstream.*timeout"` | Downstream/upstream timeout errors |
| `DOWNSTREAM_ERROR` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)downstream.*error\|upstream.*error"` | Downstream error responses |
| `EXCEPTION_LOGS` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)exception\|error\|fatal"` | Exception and error log lines |
| `CRASH_LOGS` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)crash\|panic\|fatal\|abort"` | Crash and panic indicators |
| `OOM_LOGS` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)out of memory\|oom\|killed"` | OOM-related log messages |
| `DB_CONNECTION_TIMEOUT` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)connection.*timeout\|pool.*exhausted\|database.*refused"` | Database connection failures |
| `RETRY_EXHAUSTED` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "(?i)retry.*exhausted\|max.*retry\|retries.*exceeded"` | Retry exhaustion events |
| `HTTP_5XX_LOGS` | `{app="{{service}}",namespace="{{namespace}}"} \|~ "status=[45]\\d{2}" | HTTP 4xx/5xx status codes |

> **Important caveat:** The LogQL templates above are **environment-specific examples**. Label names (`app`, `namespace`) and log patterns vary across logging stacks. The registry is designed to be extended or overridden per deployment environment.

---

## Evidence Types and Mapping Policy

### `LokiEvidenceMapper`

The mapper translates parsed Loki log results into semantic `Evidence` types.

| Log Pattern | Evidence Type | Description |
|---|---|---|
| Timeout error logs found | `log_timeout_error` | Timeout pattern detected in logs |
| Downstream timeout logs found | `log_downstream_timeout` | Downstream dependency timeout |
| Exception/error log burst | `log_exception_spike` | Error or exception burst detected |
| Crash/panic logs found | `log_crash_loop` | Crash indicator in logs |
| OOM-related logs found | `log_oom_message` | Out-of-memory message detected |
| DB connection timeout logs | `log_db_connection_timeout` | Database connection failure |
| Retry exhaustion logs | `log_retry_exhausted` | Retry limit exceeded |
| HTTP 5xx logs found | `log_http_5xx` | Server error responses in logs |
| No log data returned | `log_no_signal` | Loki has no data for this query |

### Mapping Policy

1. **Log lines matching pattern** → corresponding evidence type with log line count, sample timestamps, and severity
2. **Empty result set** → `log_no_signal` evidence (distinguishes "no anomaly" from "no data")
3. **Multiple matches** → one evidence per query type, with aggregated statistics

All produced evidence carries:
- `source: "loki"` — identifies the evidence origin
- `logCount` — number of matching log lines
- `sampleTimestamp` — first matching log timestamp
- `sampleLine` — representative log line
- `labels` — relevant Loki stream labels

---

## Provider Orchestrator

### `LokiEvidenceProvider`

```java
public class LokiEvidenceProvider {
    public LokiEvidenceResult collect(LokiEvidenceRequest request) {
        // 1. Resolve LogQL from template registry
        // 2. Execute query via client SPI
        // 3. Parse raw response
        // 4. Map to evidence
        // 5. Return result with evidence list and raw summary
    }
}
```

---

## CLI Usage

### Fixture Mode (default, no Loki required)

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR \
  --output /tmp/loki_timeout_evidence.json \
  --reader fixture
```

### Multi Query Type

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR,EXCEPTION_LOGS,OOM_LOGS \
  --output /tmp/loki_multi.json \
  --reader fixture
```

### HTTP Mode (requires live Loki)

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR \
  --output /tmp/loki_timeout_live.json \
  --reader http \
  --loki-url http://localhost:3100
```

### CLI Options

| Flag | Required | Description |
|------|----------|-------------|
| `--service` | Yes | Target service name (used in LogQL label matching) |
| `--namespace` | Yes | Kubernetes namespace (used in LogQL label matching) |
| `--query-type` | Yes | Comma-separated list of: `TIMEOUT_ERROR`, `DOWNSTREAM_TIMEOUT`, `DOWNSTREAM_ERROR`, `EXCEPTION_LOGS`, `CRASH_LOGS`, `OOM_LOGS`, `DB_CONNECTION_TIMEOUT`, `RETRY_EXHAUSTED`, `HTTP_5XX_LOGS` |
| `--output` | Yes | Output file path for collected evidence JSON |
| `--reader` | No | `fixture` (default) or `http` |
| `--loki-url` | Only with `--reader http` | Loki HTTP API URL (e.g. `http://localhost:3100`) |

---

## Testing Approach

The Loki provider uses **fixture-based testing** — no live Loki instance is required for any test.

### Test Structure

| Test Class | What It Tests | Count |
|---|---|---|
| `LokiResponseParserTest` | Parser edge cases (streams, nanosecond timestamps, empty results, error responses) | ~8 |
| `LokiQueryTemplateRegistryTest` | Template resolution, placeholder substitution, unknown type handling | ~5 |
| `LokiEvidenceMapperTest` | Pattern-based evidence mapping, no-signal detection | ~6 |
| `LokiEvidenceProviderTest` | End-to-end orchestration with fixture client | ~4 |
| `HttpLokiQueryClientTest` | HTTP client construction (no live calls in tests) | ~4 |
| `CollectLokiEvidenceCommandTest` | CLI command integration | ~3 |

**Total: 30 new tests** (263 project-wide, up from 229).

### Running Tests

```bash
# All tests including Loki provider
mvn test

# Only Loki provider tests
mvn test -pl sre-agent-loki-provider

# Expected: 263 tests passing
```

---

## Known Limitations

1. **LogQL templates are environment-specific** — The built-in templates assume standard label names (`app`, `namespace`). Production deployments may need custom templates matching their logging pipeline.

2. **No structured log parsing** — The mapper uses regex pattern matching on log lines. Structured JSON log parsing is a future enhancement.

3. **No anomaly detection on log volume** — The mapper detects pattern presence but does not compute log rate anomalies (e.g., burst detection against baseline).

4. **Single query per invocation** — Each query type executes independently. Correlation across multiple query results requires future work.

5. **Fixture responses are hand-crafted** — Fixture JSON files represent idealized Loki responses. They may not cover all real-world response variations.

6. **No authentication on HTTP client** — `HttpLokiQueryClient` currently assumes an unauthenticated Loki endpoint. Production deployments may need bearer token or basic auth support.

7. **Nanosecond timestamp precision** — Java `Instant` has nanosecond precision, which correctly handles Loki's nanosecond timestamps without precision loss.

---

## Relationship to Future Steps

### Step R: LLM Hypothesis Proposer

The Loki evidence provider is a key input source for the planned LLM Hypothesis Proposer (Step R):

```
LokiEvidenceProvider  →  List<Evidence>
                              ↓
LLM Hypothesis Proposer (Step R)  →  Additional hypotheses based on log patterns
                              ↓
Existing RCA Pipeline (Verification → Scoring → Decision)
```

### Combined Prometheus + Loki Evidence

Together with the Prometheus provider (Step M), the agent can now correlate:
- **Metric spikes** (from Prometheus) with **log anomalies** (from Loki)
- Error rate increases with corresponding exception log bursts
- Latency degradation with downstream timeout logs
- Memory pressure with OOM kill messages

This multi-signal correlation is a key capability for the future LLM Hypothesis Proposer.

### Step Q: Observability Taxonomy

Step Q will formalize the taxonomy of observability evidence types across all providers (K8s, Prometheus, Loki, Alertmanager, Traces), establishing a unified vocabulary for evidence types that all diagnostic patterns reference.

---

## Design Decisions

### Why a Separate Module?

Following the same pattern as `sre-agent-prometheus-provider` and `sre-agent-k8s-provider`, the Loki provider is isolated in its own Maven module to:
- Keep `sre-agent-core` free of any Loki dependency
- Allow the module to be excluded from builds that don't need log evidence
- Make the dependency graph explicit: `core ← loki-provider`

### Why Fixture-Based Testing?

- **CI reliability** — no external service dependency
- **Determinism** — same input → same output, every time
- **Speed** — no network round-trips
- **Coverage** — fixtures can simulate edge cases (empty results, error responses) that are hard to reproduce with live Loki

### Why Pattern-Based Mapping?

The mapper uses regex pattern matching on log lines rather than structured log parsing because:
- Most Kubernetes application logs are semi-structured or unstructured
- Regex patterns capture the most common error indicators without requiring log format standardization
- The approach is environment-agnostic — it works regardless of the logging framework used

### Why Generic Evidence Types?

The provider outputs generic `Evidence` records (with `source = "loki"`) rather than Loki-specific types. This ensures:
- The core RCA pipeline remains data-source agnostic
- Evidence from different providers (K8s, Prometheus, Loki) can be combined in a single investigation
- New evidence sources can be added without modifying the core pipeline
