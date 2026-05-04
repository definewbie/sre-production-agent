# Probe Execution Framework (Step S)

## Overview

Step S introduces `sre-agent-probe-executor` — the bridge between LLM-proposed investigation intents and actual evidence collection. After the LLM Hypothesis Proposer generates hypotheses, it also emits **ProbeIntents** — structured suggestions like "check p95 latency" or "check error rate". The probe executor routes these intents to the appropriate evidence providers and collects the resulting Evidence.

**Key design invariant:** Probe execution does NOT bypass Verification. Probe execution does NOT mutate the RCA decision. `canAffectDecision` is always `false`.

---

## Module Structure

```
sre-agent-probe-executor/
├── pom.xml
└── src/main/java/ai/sreagent/probe/
    ├── ProbeType.java                    ← Enum: METRICS, LOGS, TRACES, KUBERNETES_DESCRIBE, ALERT_HISTORY
    ├── ProbeIntent.java                  ← Single probe intent from LLM proposer
    ├── ProbeIntentBundle.java            ← Collection of intents per incident
    ├── ProbeExecutionMode.java           ← FIXTURE only (LIVE reserved for future)
    ├── ProbeExecutionPlan.java           ← Pre-validated plan with canAffectDecision=false
    ├── ProbeExecutionResult.java         ← Final result with Evidence + guardrails
    ├── ProbeExecutionStatus.java         ← EXECUTED / PARTIAL / SKIPPED
    ├── ProbeRoutingException.java        ← Router error (unsupported probe type)
    ├── ProbeEvidenceBundle.java          ← Evidence grouped by probe type
    ├── ProbeIntentRouter.java            ← Routes ProbeType → provider
    ├── ProbeExecutionPolicy.java         ← Validates plans before execution
    ├── FixtureProbeExecutor.java         ← Generates fixture Evidence per probe type
    ├── mapper/
    │   ├── PrometheusProbeMapper.java    ← Maps Prometheus fixture → Evidence
    │   ├── LokiProbeMapper.java          ← Maps Loki fixture → Evidence
    │   ├── TraceProbeMapper.java         ← Maps Trace fixture → Evidence
    │   ├── KubernetesProbeMapper.java    ← Maps K8s fixture → Evidence
    │   └── AlertmanagerProbeMapper.java  ← Maps Alertmanager fixture → Evidence
    └── NormalizedEvidence.java           ← Cross-provider normalized evidence model
```

---

## Data Flow

```
LLM Hypothesis Proposer
        │
        ▼
   ProbeIntentBundle
   (list of ProbeIntents)
        │
        ▼
   ProbeIntentRouter
   (ProbeType → provider mapping)
        │
        ▼
   ProbeExecutionPolicy
   (canAffectDecision=false, mode check, max probes)
        │
        ▼
   ProbeExecutionPlan (validated)
        │
        ▼
   FixtureProbeExecutor
        │
        ├── PrometheusProbeMapper ──→ Evidence
        ├── LokiProbeMapper ────────→ Evidence
        ├── TraceProbeMapper ───────→ Evidence
        ├── KubernetesProbeMapper ──→ Evidence
        └── AlertmanagerProbeMapper → Evidence
        │
        ▼
   ProbeExecutionResult
   (evidence list + canAffectDecision=false)
```

---

## Key Models

### ProbeIntent
```java
public record ProbeIntent(
    String probeType,       // e.g. "METRICS", "LOGS"
    String target,          // e.g. "payment-service"
    String description,     // e.g. "Check p95 latency for payment-service"
    boolean canAffectDecision  // must be false
) {}
```

### ProbeExecutionResult
```java
public record ProbeExecutionResult(
    String incidentId,
    String proposalId,
    ProbeExecutionStatus status,     // EXECUTED / PARTIAL / SKIPPED
    List<Evidence> evidence,
    List<NormalizedEvidence> normalizedEvidence,
    List<String> executedProbeIds,
    List<String> skippedProbeIds,
    List<String> errors,
    boolean canAffectDecision        // enforced false at construction
) {}
```

---

## Provider Mappers

Each probe type has a dedicated mapper that converts fixture data into generic `Evidence`:

| Probe Type | Mapper | Evidence Source |
|------------|--------|-----------------|
| METRICS | PrometheusProbeMapper | Prometheus fixture (error rate, latency) |
| LOGS | LokiProbeMapper | Loki fixture (log patterns, errors) |
| TRACES | TraceProbeMapper | Trace fixture (spans, latency) |
| KUBERNETES_DESCRIBE | KubernetesProbeMapper | K8s fixture (pod status, events) |
| ALERT_HISTORY | AlertmanagerProbeMapper | Alertmanager fixture (firing alerts) |

---

## Safety Guardrails

1. **`canAffectDecision=false`** — Enforced at compile time. The `ProbeExecutionResult` constructor throws `IllegalArgumentException` if `true`.
2. **FIXTURE mode only** — No live backend probes in Step S. `ProbeExecutionPolicy` rejects `LIVE` mode plans.
3. **Max probes limit** — Policy enforces an upper bound on probes per execution.
4. **No RCA mutation** — Probe evidence supplements the investigation but does not feed back into the decision pipeline.

---

## CLI Usage

```bash
# Propose hypotheses and execute probes for Scenario E
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  propose-and-execute-probes
```

Output:
```
Probes executed successfully
incident: scenario-e-payment-deployment-vs-downstream
hypotheses: 2
probes proposed: 5
probes executed: 5
evidence collected: 5
can affect decision: false
probe types: METRICS, LOGS, TRACES, KUBERNETES_DESCRIBE, ALERT_HISTORY
```

---

## REST API

### POST /api/investigations/scenario-e/propose-and-execute-probes

Executes the full propose-and-execute pipeline for Scenario E in fixture mode.

**Response:**
```json
{
  "incidentId": "scenario-e-payment-deployment-vs-downstream",
  "proposalId": "...",
  "status": "EXECUTED",
  "evidence": [...],
  "normalizedEvidence": [...],
  "executedProbeIds": [...],
  "skippedProbeIds": [],
  "errors": [],
  "canAffectDecision": false
}
```

---

## UI Preview

The Web console includes a **Probe Execution** card (Step S) that appears after investigation completes:

- **Execute Probes (Fixture)** button — triggers probe execution via REST API
- **Status line** — shows execution mode, probe count, evidence count
- **Evidence list** — renders each collected evidence with type badge and content

The card is hidden by default and revealed when an investigation completes.

---

## Test Coverage

- **46 tests** in `sre-agent-probe-executor` module
- All fixture-based — no live backend required
- Tests cover: router, policy, executor, all 5 mappers, plan validation, edge cases
- Total project tests: **484**, 0 failures

---

## Known Limitations

1. **Fixture mode only** — no live Prometheus/Loki/Trace backend probes
2. **No re-run policy** — probe results do not trigger re-investigation
3. **No probe-to-RCA feedback loop** — probe evidence is informational only
4. **No probe scheduling** — all probes execute immediately
5. **Scenario E only** — only one scenario has probe execution wired up

---

## Future Direction

**Step W: Post-probe RCA Re-run Policy**
- Allow probe evidence to trigger a re-run of the RCA pipeline under controlled conditions
- Relax `canAffectDecision` with audit trail and human approval gate
- Define conditions under which probe evidence warrants re-investigation

**Step T: Local Observability Stack on Kind**
- Deploy Prometheus + Loki + Tempo on kind cluster
- Enable LIVE mode probe execution

**Step U: Instrumented Demo Services**
- Deploy services with OpenTelemetry instrumentation
- Generate realistic traffic patterns for testing
