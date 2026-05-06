# Evidence Taxonomy

> Step Q: Observability Evidence Taxonomy / Normalization

## Why Evidence Taxonomy Exists

The SRE Agent collects evidence from multiple observability providers:

```text
Kubernetes → container_crash_loop_backoff, pod_restart_count_increased, ...
Prometheus  → metric_latency_p95_spike, metric_error_rate_spike, ...
Loki        → log_downstream_timeout, log_exception_spike, ...
Alertmanager → alert_firing, alert_severity_high, ...
Trace       → trace_downstream_span_slow, trace_error_span, ...
Static JSON → deploy_event_near_alert_window, downstream_latency_spike, ...
```

Each provider defines its own evidence type strings. Without a normalization layer:

1. **DiagnosticPatterns** must match provider-specific type strings
2. **LLM Hypothesis Proposer** must understand 50+ provider-specific strings
3. **Multi-provider correlation** (e.g. "Prometheus says latency spike + Trace says downstream slow") is ad-hoc
4. **Adding a new provider** requires updating all consumers

The Evidence Taxonomy provides a **provider-agnostic normalized view**.

## Architecture

```text
Provider-specific Evidence
        ↓ EvidenceNormalizer
NormalizedEvidence (category + signal + sourceKind + severity + causalRole)
        ↓
DiagnosticPattern (future: match by signal/category)
LLM Hypothesis Proposer (future: consume normalized view)
```

## NormalizedEvidence Model

```java
public record NormalizedEvidence(
    String originalEvidenceType,   // raw provider string
    String normalizedType,         // same as original (identity for now)
    EvidenceCategory category,     // ALERT, METRIC, LOG, TRACE, KUBERNETES, ...
    EvidenceSignal signal,         // LATENCY_SPIKE, CRASH_LOOP, TIMEOUT, ...
    EvidenceSourceKind sourceKind, // PROMETHEUS, LOKI, TRACE, ...
    EvidenceSeverity severity,     // INFO, WARNING, CRITICAL
    EvidenceCausalRole causalRole, // SYMPTOM, CAUSE_CANDIDATE, CONTEXT, ...
    String entity,                 // what entity this is about
    String service,                // service name
    String namespace,              // namespace
    double strength,               // original strength
    Instant timestamp,             // original timestamp
    String content,                // original content
    Map<String, Object> attributes // original + enriched attributes
)
```

## Enum Definitions

### EvidenceCategory

| Value | Description |
|-------|-------------|
| ALERT | Alerting system evidence |
| METRIC | Time-series metrics evidence |
| LOG | Log/messaging evidence |
| TRACE | Distributed tracing evidence |
| KUBERNETES | Container orchestration evidence |
| TOPOLOGY | Service dependency/topology evidence |
| DEPLOYMENT | Deployment/change evidence |
| RUNTIME | Runtime/VM evidence |
| UNKNOWN | Unrecognized evidence |

### EvidenceSignal (partial)

| Value | Example Sources |
|-------|----------------|
| ERROR_RATE_SPIKE | Prometheus metric, Static JSON |
| LATENCY_SPIKE | Prometheus metric, Trace root span |
| DOWNSTREAM_LATENCY | Prometheus metric, Loki log, Trace span |
| TIMEOUT | Loki log, Trace span |
| CRASH_LOOP | K8s status, Loki log |
| OOM | K8s event, Loki log |
| RESTART | K8s status, Prometheus metric |
| ALERT_FIRING | Alertmanager alert |
| DEPENDENCY_PATH | Trace span |
| SLOW_SPAN | Trace span |
| ERROR_SPAN | Trace span |
| NO_SIGNAL | Any provider (empty result) |
| RUNTIME_HEALTHY | K8s pod container Running/Completed state |
| POD_READY | K8s pod ready condition True |
| SCHEDULING_FAILURE | K8s FailedScheduling event |
| POD_TERMINATION | K8s Killing event |
| NORMAL_EVENT | K8s Normal events (non-warning) |

### EvidenceSeverity

| Value | Strength Range |
|-------|---------------|
| CRITICAL | ≥ 0.85 |
| WARNING | ≥ 0.60 |
| INFO | < 0.60 |
| UNKNOWN | N/A |

### EvidenceCausalRole

| Value | Description |
|-------|-------------|
| SYMPTOM | Observable effect (error rate, latency spike) |
| CAUSE_CANDIDATE | Potential root cause (downstream timeout, crash loop) |
| CONTEXT | Environmental context (deployment metadata) |
| COUNTER_SIGNAL | Evidence against a hypothesis (normal metrics) |
| TOPOLOGY_CONTEXT | Service dependency information |
| NO_SIGNAL | Empty result from provider |

## Provider Mapping Examples

```text
container_crash_loop_backoff  → KUBERNETES / CRASH_LOOP / CAUSE_CANDIDATE
metric_latency_p95_spike      → METRIC / LATENCY_SPIKE / SYMPTOM
log_downstream_timeout        → LOG / DOWNSTREAM_LATENCY / CAUSE_CANDIDATE
trace_downstream_span_slow    → TRACE / DOWNSTREAM_LATENCY / CAUSE_CANDIDATE
alert_firing                  → ALERT / ALERT_FIRING / SYMPTOM
trace_dependency_path         → TRACE / DEPENDENCY_PATH / TOPOLOGY_CONTEXT
deployment_metadata           → KUBERNETES / DEPLOYMENT_METADATA / CONTEXT
k8s_runtime_healthy           → KUBERNETES / RUNTIME_HEALTHY / COUNTER_SIGNAL
container_oom_killed          → KUBERNETES / OOM / CAUSE_CANDIDATE
pod_ready                     → KUBERNETES / POD_READY / COUNTER_SIGNAL
restart_count_observed        → KUBERNETES / RESTART / CONTEXT
k8s_no_signal                 → KUBERNETES / NO_SIGNAL / NO_SIGNAL
k8s_event_unhealthy           → KUBERNETES / CRASH_LOOP / CAUSE_CANDIDATE
k8s_event_failed_scheduling   → KUBERNETES / SCHEDULING_FAILURE / CAUSE_CANDIDATE
k8s_event_killing             → KUBERNETES / POD_TERMINATION / SYMPTOM
k8s_event_normal              → KUBERNETES / NORMAL_EVENT / CONTEXT
```

Key insight: **multiple providers can produce the same signal**.

For example, `DOWNSTREAM_LATENCY` is produced by:
- Prometheus (`metric_downstream_latency_spike`)
- Loki (`log_downstream_timeout`)
- Trace (`trace_downstream_span_slow`)

This allows future pattern matching to be **provider-agnostic**.

## EvidenceTaxonomyRegistry

Static registry in `sre-agent-core` mapping 56+ evidence type strings to taxonomy entries.

- No provider module dependency (strings listed directly)
- Unknown types map to `UNKNOWN` for all fields
- Source kind can be inferred from `Evidence.source` field when type is not registered

## EvidenceNormalizer

```java
EvidenceNormalizer.normalize(evidence)    → NormalizedEvidence
EvidenceNormalizer.normalizeAll(list)      → List<NormalizedEvidence>
```

Does not modify original `Evidence` objects. Creates new `NormalizedEvidence` records.

## NormalizedEvidenceView

Helper for querying normalized evidence:

```java
view.hasSignal(EvidenceSignal.LATENCY_SPIKE)
view.byCategory(EvidenceCategory.METRIC)
view.causeCandidates()
view.symptoms()
view.counterSignals()
view.topologyContext()
```

## Relationship To DiagnosticPattern

Current `DiagnosticPattern` matches provider-specific `evidenceType` strings.

Future evolution:
- Patterns can match by `EvidenceSignal` instead of raw type string
- A pattern like "error rate spike + downstream timeout" becomes provider-agnostic
- Adding a new provider auto-enables existing patterns

## Relationship To LLM Hypothesis Proposer

Future LLM Proposer should consume `NormalizedEvidence`:

```text
Instead of seeing:
  metric_latency_p95_spike + trace_downstream_span_slow + log_downstream_timeout

LLM can see:
  [METRIC/LATENCY_SPIKE] + [TRACE/DOWNSTREAM_LATENCY] + [LOG/DOWNSTREAM_LATENCY]
  → 3 sources agree on downstream latency anomaly
```

This makes proposals more stable and less provider-specific.

## Current Limitations

1. Taxonomy is manually maintained in core registry
2. Severity is strength-based only (no alert severity mapping yet)
3. Causal role is static mapping (no dynamic inference)
4. Entity inference is basic (pod name > deployment > service)
5. No automatic sync when new provider evidence types are added

## CLI Usage

```bash
# Normalize an existing evidence JSON
java -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  normalize-evidence \
  --input examples/evidence/k8s_crashloop.json \
  --output examples/evidence/k8s_crashloop_normalized.json
```

Output:
```text
Normalized evidence written
input: 4 evidence items
categories:
  - KUBERNETES
signals:
  - CRASH_LOOP
  - DEPLOYMENT_METADATA
  - POD_NOT_READY
  - RESTART
causal roles:
  - CAUSE_CANDIDATE
  - CONTEXT
  - SYMPTOM
output: examples/evidence/k8s_crashloop_normalized.json
```

## V.2-UI-4.1: Semantic Typing Enhancement

### Problem

Previously, Kubernetes evidence used generic types like `k8s_event` for all events and
`pod_not_ready` for any pod with restartCount > 0. This caused false positives in RCA:
- `pod_crash_loop` incorrectly ranked #1 in latency scenarios
- Healthy pods with non-zero restart counts triggered failure evidence
- K8s events were not semantically classified

### Solution

1. **Semantic event mapping**: Events now produce typed evidence (k8s_event_unhealthy, k8s_event_failed_scheduling, k8s_event_killing, k8s_event_normal) instead of generic `k8s_event`
2. **Precise crash loop detection**: `container_crash_loop_backoff` only fires when pod status.reason == "CrashLoopBackOff", not just on presence of restart count
3. **Counter evidence for healthy pods**: `k8s_runtime_healthy` and `pod_ready` provide counter-balancing signals
4. **VerificationEngine IGNORED_TYPES**: NONE, k8s_no_signal, k8s_runtime_healthy, restart_count_observed are excluded from supporting/counter scoring

### New Evidence Types

| Type | Signal | Role | When Produced |
|------|--------|------|---------------|
| `k8s_runtime_healthy` | RUNTIME_HEALTHY | COUNTER_SIGNAL | Pod containers in Running/Completed state |
| `container_oom_killed` | OOM | CAUSE_CANDIDATE | Container lastState.reason == OOMKilled |
| `pod_ready` | POD_READY | COUNTER_SIGNAL | Pod has ready condition True |
| `restart_count_observed` | RESTART | CONTEXT | Non-zero restart count observed (neutral observation) |
| `k8s_no_signal` | NO_SIGNAL | NO_SIGNAL | No anomalies detected in K8s data |
| `k8s_event_unhealthy` | CRASH_LOOP | CAUSE_CANDIDATE | K8s event with reason containing Unhealthy |
| `k8s_event_failed_scheduling` | SCHEDULING_FAILURE | CAUSE_CANDIDATE | K8s event with reason FailedScheduling |
| `k8s_event_killing` | POD_TERMINATION | SYMPTOM | K8s event with reason Killing |
| `k8s_event_normal` | NORMAL_EVENT | CONTEXT | K8s event with type Normal (non-warning) |

### Verification Impact

VerificationEngine now filters out non-diagnostic types before evidence classification:
```java
private static final Set<String> IGNORED_TYPES = Set.of(
    "NONE", "k8s_no_signal", "k8s_runtime_healthy", "restart_count_observed"
);
```

These types are preserved in the evidence stream (for audit/display) but never influence supporting or counter scoring.
