# Trace Evidence Provider

> Step P: Trace Evidence Provider v1

## Overview

The `sre-agent-trace-provider` module bridges **distributed trace data** into the SRE Agent's RCA evidence plane.

It converts trace/span query results into semantic `Evidence` objects, enabling the RCA workflow to reason about:
- Which downstream dependency is slow
- Which span failed
- Whether root span or child span dominates latency
- Dependency topology between services

**This provider does not require a live tracing backend for tests.** All tests use deterministic fixtures.

## Architecture

```
TraceQueryClient (interface)
├── FixtureTraceQueryClient   — unit tests, deterministic CI
└── HttpTraceQueryClient      — optional live backend validation

TraceResponseParser           — parses Jaeger-like JSON → ParsedTrace/ParsedSpan
TraceEvidenceMapper           — maps parsed traces → semantic Evidence
TraceEvidenceProvider         — orchestrates client → parser → mapper → result
```

## Evidence Types

| Evidence Type | Strength | Trigger |
|---|---|---|
| `trace_downstream_span_slow` | 0.85 | Child span duration ≥ 1000ms |
| `trace_error_span` | 0.80 | Span status is error or has `error=true` attribute |
| `trace_root_span_slow` | 0.70 | Root span duration ≥ 1000ms |
| `trace_dependency_path` | 0.65 | Trace shows service → downstream relationship |
| `trace_timeout_span` | 0.85 | Span has `timeout=true` attribute |
| `trace_child_span_dominates_latency` | 0.90 | Child duration / root duration ≥ 0.70 |
| `trace_no_signal` | 0.0 | Empty trace result |

Source: `tracing`

## Query Types

| Query Type | Intent |
|---|---|
| `DOWNSTREAM_SLOW_SPAN` | Find traces where service has child spans exceeding latency threshold |
| `ERROR_SPAN` | Find traces with span status error or error=true tag |
| `ROOT_SPAN_SLOW` | Find traces where root span duration exceeds threshold |
| `DEPENDENCY_PATH` | Find traces involving service and its downstream dependency path |
| `TIMEOUT_SPAN` | Find spans with timeout-related attributes or operation names |

## CLI Usage

### Fixture (deterministic)

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type DOWNSTREAM_SLOW_SPAN \
  --output examples/evidence/trace_order_payment_latency.json \
  --reader fixture
```

### HTTP (live backend)

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type DOWNSTREAM_SLOW_SPAN \
  --output examples/evidence/trace_live.json \
  --reader http \
  --trace-url http://localhost:16686 \
  --backend-type jaeger
```

## Fixture Format

Fixtures use Jaeger-compatible JSON format:

```json
{
  "data": [
    {
      "traceID": "trace-001",
      "spans": [
        {
          "spanID": "span-root",
          "operationName": "POST /checkout",
          "startTime": 1714292400000000,
          "duration": 1500000,
          "processID": "p1",
          "references": [],
          "tags": [{"key": "http.status_code", "value": "200"}]
        },
        {
          "spanID": "span-child",
          "operationName": "POST /charge",
          "startTime": 1714292400200000,
          "duration": 1200000,
          "processID": "p2",
          "references": [{"refType": "CHILD_OF", "spanID": "span-root"}],
          "tags": [{"key": "error", "value": "true"}]
        }
      ],
      "processes": {
        "p1": {"serviceName": "order-service"},
        "p2": {"serviceName": "payment-service"}
      }
    }
  ]
}
```

**Note:** Jaeger durations are in microseconds. The parser converts to milliseconds.

## Mapping to Observability Plane

| Signal | Provider | Evidence Focus |
|---|---|---|
| Metrics | Prometheus Provider | Symptoms (error rate, latency, saturation) |
| Logs | Loki Provider | Error semantics (exceptions, timeouts) |
| Traces | **Trace Provider** | Request path, span latency, dependency edges |
| Alerts | Alertmanager Provider | Alert lifecycle, incident metadata |
| Runtime | K8s Provider | Pod state, events, resource pressure |

All produce normalized `Evidence` for the same RCA core.

## Relationship to LLM Hypothesis Proposer (Future)

The LLM proposer will suggest trace probe intents:
- "Check downstream span latency for payment-service"
- "Inspect error spans in the last 30 minutes"
- "Compare root vs child span duration ratio"

The trace provider will execute those intents and convert results to Evidence.

## Limitations (Step P)

- Only supports Jaeger-like JSON fixture format
- HTTP client tested against Jaeger API paths only
- No TraceQL / search language support
- No causal graph analysis beyond simple span duration comparison
- No live backend required for CI
- Metric names and trace structures are template examples

## Files

```
sre-agent-trace-provider/
├── client/          TraceQueryClient, FixtureTraceQueryClient, HttpTraceQueryClient, TraceClientConfig
├── parser/          TraceResponseParser, ParsedTrace, ParsedSpan
├── mapper/          TraceEvidenceMapper, TraceEvidenceTypes
├── query/           TraceQueryType, TraceQueryTemplate, TraceQueryTemplateRegistry
├── TraceEvidenceProvider, TraceEvidenceRequest, TraceEvidenceResult
└── src/test/resources/fixtures/trace/  (6 fixture JSON files)
```
