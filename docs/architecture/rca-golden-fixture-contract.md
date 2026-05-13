# RCA Golden Fixture Contract

**Status:** test design contract, no implementation changes  
**Last updated:** 2026-05-12  
**Purpose:** define how scenario derivations become executable tests for the causal RCA model.

## Why Golden Fixtures Come First

The RCA model is changing from score-first to decision-cap-first. Before implementation changes, the expected behavior must be locked down as fixtures.

Golden fixtures prevent the engine from drifting back into scenario-specific scoring patches:

```text
scenario
  -> normalized facts
  -> expected causal claims
  -> expected decision caps
  -> expected missing evidence and next probes
```

An implementation passes a fixture only if it respects the hard causal boundaries.

## Fixture Shape

```text
RcaGoldenFixture
  fixtureId: string
  title: string
  description: string
  tags: list<string>
  problemWindow: ProblemWindowFixture
  providerHealth: list<ProviderHealthFixture>
  topology: list<TopologyEdgeFixture>
  observations: list<ObservationFixture>
  actions: list<ActionFixture>
  optionalKnowledgeContext: list<KnowledgeContextFixture>
  expected: ExpectedRcaFixture
```

## Inputs

### ProblemWindowFixture

```text
ProblemWindowFixture
  start: instant
  end: instant
  lookback: duration
  lookahead: duration
  source: ALERT | EVIDENCE | MANUAL
```

### ProviderHealthFixture

```text
ProviderHealthFixture
  provider: PROMETHEUS | LOKI | TRACE | KUBERNETES | CLOUDTRAIL | GIT | ...
  status: HEALTHY | DEGRADED | BLIND | UNKNOWN
  queryWindowCoverage: double
  samplingRate: double?
  ingestionDelay: duration?
```

### TopologyEdgeFixture

```text
TopologyEdgeFixture
  from: string
  to: string
  relation: CALLS | DEPENDS_ON | HOSTED_ON | OWNS | MANAGES | DEPLOYED_BY | ROUTES_TO | BACKED_BY
  source: TRACE | CONFIG | CMDB | KUBERNETES_OWNER_REF | CLOUD_API | DISCOVERED | MANUAL
  confidence: HIGH | MEDIUM | LOW
  validFrom: instant?
  validTo: instant?
```

### ObservationFixture

```text
ObservationFixture
  id: string
  entity: string
  signal: LATENCY_SPIKE | ERROR_RATE_SPIKE | TIMEOUT | CRASH_LOOP | RESTART |
          OOM | POD_NOT_READY | SCHEDULING_FAILURE | NODE_NOT_READY |
          CPU_HIGH | MEMORY_HIGH | NO_SIGNAL | HEALTHY | UNKNOWN
  sourceKind: PROMETHEUS | LOKI | TRACE | KUBERNETES | ALERTMANAGER | EC2 | SYSTEMD | CLOUDWATCH | UNKNOWN
  timestamp: instant
  severity: INFO | WARNING | CRITICAL
  strength: double
  attributes: map
```

### ActionFixture

```text
ActionFixture
  id: string
  targetEntity: string
  actionType: DEPLOYMENT | ROLLBACK | CONFIG_CHANGE | FEATURE_FLAG_CHANGE |
              MANUAL_POD_DELETE | NODE_DRAIN | AUTOSCALER_EVICTION |
              INSTANCE_REBOOT | PROCESS_RESTART | CHAOS_INJECTION |
              CREDENTIAL_ROTATION | NETWORK_POLICY_CHANGE | UNKNOWN
  actorType: HUMAN | CI_CD | CONTROLLER | AUTOSCALER | CHAOS_TOOL | CLOUD_PROVIDER | UNKNOWN
  timestamp: instant
  sourceKind: GIT | ARGOCD | SPINNAKER | KUBERNETES_AUDIT | CLOUDTRAIL | CHAOS | MANUAL | UNKNOWN
  confidence: HIGH | MEDIUM | LOW
  attributes: map
```

## Expected Output

```text
ExpectedRcaFixture
  expectedDecision: RcaDecision
  expectedDiagnosticQuality: DiagnosticQuality
  leadingClaim: ExpectedClaimFixture?
  expectedClaims: list<ExpectedClaimFixture>
  expectedRejectedClaims: list<ExpectedClaimFixture>
  expectedMissingEvidence: list<ExpectedMissingEvidenceFixture>
  expectedCounterSignals: list<ExpectedCounterSignalFixture>
  expectedNextProbes: list<ExpectedNextProbeFixture>
  forbiddenOutcomes: list<ForbiddenOutcomeFixture>
```

### ExpectedClaimFixture

```text
ExpectedClaimFixture
  candidateEntity: string
  affectedEntity: string
  faultMode: string
  relation: CausalRelation
  allowedDecision: AllowedDecision
  maxConfidenceAtMost: double?
  minConfidenceAtLeast: double?
  expectedEvidenceRoles: map<EvidenceRole, list<EvidenceId>>
  expectedGuardStatuses: map<GuardType, GuardStatus>
```

Use confidence ranges sparingly. Most fixtures should assert decision caps and guard statuses rather than exact confidence.

### ForbiddenOutcomeFixture

```text
ForbiddenOutcomeFixture
  description: string
  candidateEntity: string?
  faultMode: string?
  forbiddenDecisionAbove: AllowedDecision?
  forbiddenRelation: CausalRelation?
```

Example:

```text
pod restart without crash primary evidence
  must not become PROBABLE_ROOT_CAUSE or LIKELY_ROOT_CAUSE
```

## Required Fixture Assertions

Every fixture should assert:

1. Leading claim, if one should exist.
2. Allowed decision cap for each important candidate.
3. Evidence roles for primary, symptom, impact, control-plane, and counter evidence.
4. Missing required evidence.
5. Counter-signals.
6. Diagnostic quality.
7. Forbidden high-confidence outcomes.
8. Next probes when the result is uncertain.

## Fixture Categories

| Category | Purpose |
|---|---|
| Happy path | Prove strong evidence produces correct leading claim. |
| Counterfactual | Prove tempting but wrong hypotheses are capped/rejected. |
| Provider blindness | Prove no-signal semantics depend on provider health. |
| Incident normalization | Prove related alerts update one problem. |
| Common cause | Prove shared dependency/node/provider is recognized. |
| Independent incidents | Prove unrelated incidents are not merged. |
| LLM adversarial | Prove unsupported proposals remain unverified. |
| GraphRAG ablation | Prove knowledge layer improves recall/context, not final confidence without evidence. |

## Minimal P0 Fixture Set

Start with these fixtures before changing engine behavior:

### F1: Downstream Latency Beats Weak Crash Symptoms

Purpose:

```text
payment latency with order timeout/restart should not become order pod crash root cause
```

Required assertions:

1. `payment-service DOWNSTREAM_DEPENDENCY_LATENCY` is leading claim.
2. `order-service POD_CRASH_LOOP` is capped at `UNCERTAIN_REQUIRES_MORE_EVIDENCE`.
3. Order restart/probe failure is `SYMPTOM` or `IMPACT`.
4. Missing crash evidence includes exit code/startup failure/OOM/CrashLoopBackOff.

### F2: Real CrashLoop Wins When Primary Crash Evidence Exists

Purpose:

```text
real local crash should not be suppressed by dependency rules
```

Required assertions:

1. `order-service POD_CRASH_LOOP` is leading claim.
2. Primary evidence includes CrashLoopBackOff or non-zero exit code or startup failure log.
3. Healthy dependency signal is counter evidence against dependency latency.

### F3: Deploy Action Alone Is Not Deployment Regression

Purpose:

```text
recent deploy is required context, not sufficient proof
```

Required assertions:

1. Deployment action without change-specific runtime evidence is capped at `POSSIBLE_ROOT_CAUSE`.
2. Missing evidence includes post-action anomaly tied to changed code/config/flag.
3. If anomaly predates deploy, deployment regression is capped lower or rejected.

### F4: Provider Blindness Does Not Create Counter Evidence

Purpose:

```text
blind provider no_signal is observability degradation, not negative proof
```

Required assertions:

1. Missing Loki logs are not counter evidence when Loki is `BLIND`.
2. `diagnosticQuality` is `DEGRADED` or `BLIND`.
3. Next probes include restoring provider or alternate source.

### F5: Same Topology Chain Updates One Problem

Purpose:

```text
payment latency, order timeout, and order restart in one chain should not produce three independent RCA runs
```

Required assertions:

1. One problem fingerprint.
2. Lifecycle transitions `OPEN -> UPDATED -> UPDATED`.
3. RCA reranks claims inside the same problem.

### F6: LLM Proposal Without Evidence Is Rejected

Purpose:

```text
LLM can propose but cannot bypass deterministic guards
```

Required assertions:

1. Proposal without cited evidence IDs is rejected or remains `UNVERIFIED_PROPOSAL`.
2. Proposal does not increase final confidence.
3. Suggested probes may be accepted if actionable.

## YAML Example

```yaml
fixtureId: F1-downstream-latency-weak-crash
title: Downstream latency beats weak crash symptoms
tags:
  - downstream_dependency_latency
  - crash_loop
  - decision_cap

problemWindow:
  start: "2026-05-12T10:00:00Z"
  end: "2026-05-12T10:05:00Z"
  lookback: "PT5M"
  lookahead: "PT10M"
  source: ALERT

providerHealth:
  - provider: PROMETHEUS
    status: HEALTHY
    queryWindowCoverage: 1.0
  - provider: TRACE
    status: HEALTHY
    queryWindowCoverage: 1.0
  - provider: KUBERNETES
    status: HEALTHY
    queryWindowCoverage: 1.0

topology:
  - from: service:order-service
    to: service:payment-service
    relation: CALLS
    source: CONFIG
    confidence: MEDIUM

observations:
  - id: obs-payment-latency
    entity: service:payment-service
    signal: LATENCY_SPIKE
    sourceKind: PROMETHEUS
    timestamp: "2026-05-12T10:00:30Z"
    severity: WARNING
    strength: 0.9
    attributes:
      p95: "2s"
  - id: obs-order-timeout
    entity: service:order-service
    signal: TIMEOUT
    sourceKind: LOKI
    timestamp: "2026-05-12T10:01:00Z"
    severity: WARNING
    strength: 0.8
    attributes:
      target: payment-service
  - id: obs-order-restart
    entity: pod:order-service
    signal: RESTART
    sourceKind: KUBERNETES
    timestamp: "2026-05-12T10:02:00Z"
    severity: WARNING
    strength: 0.4
    attributes: {}

actions: []

expected:
  expectedDecision: LIKELY_ROOT_CAUSE
  expectedDiagnosticQuality: NORMAL
  leadingClaim:
    candidateEntity: service:payment-service
    affectedEntity: service:order-service
    faultMode: DOWNSTREAM_DEPENDENCY_LATENCY
    relation: LIKELY_CAUSED
    allowedDecision: LIKELY_ROOT_CAUSE
    expectedGuardStatuses:
      PRIMARY_EVIDENCE: PASS
      TOPOLOGY: PASS
      TEMPORAL_ORDER: PASS
      COUNTER_SIGNAL: PASS
      PROVIDER_TRUST: PASS
  expectedClaims:
    - candidateEntity: pod:order-service
      affectedEntity: service:order-service
      faultMode: CRASH_LOOP
      relation: INSUFFICIENT_EVIDENCE
      allowedDecision: UNCERTAIN_REQUIRES_MORE_EVIDENCE
      expectedEvidenceRoles:
        SYMPTOM:
          - obs-order-restart
  expectedMissingEvidence:
    - crash reason / exit code / startup failure log for order-service
  forbiddenOutcomes:
    - description: weak restart must not become high-confidence crash root cause
      candidateEntity: pod:order-service
      faultMode: CRASH_LOOP
      forbiddenDecisionAbove: UNCERTAIN_REQUIRES_MORE_EVIDENCE
```

## Validation Metrics From Fixtures

Fixture runs should report:

1. Guard violation count.
2. Leading claim match rate.
3. Forbidden outcome violations.
4. Missing evidence match rate.
5. Diagnostic quality match rate.
6. Next probe relevance.

Early success criterion:

```text
guard violation count == 0
forbidden outcome violations == 0
```

Confidence accuracy should be evaluated later with historical replay, not only fixtures.

## Design Conclusions

1. Fixtures should test causal boundaries, not exact score arithmetic.
2. Every scenario should include forbidden outcomes for known false positives.
3. Golden fixtures are the bridge between design and safe implementation.
4. The first implementation slice should satisfy F1-F6 before adding more fault modes.
