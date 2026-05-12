# RCA Product Output Contract

**Status:** product contract design, no implementation changes  
**Last updated:** 2026-05-12  
**Purpose:** define what RCA should return to an on-call SRE, UI, API client, report generator, and future golden fixture tests.

## Why This Contract Exists

The causal engine should not only produce a score. A useful RCA product must answer:

```text
what probably caused the incident?
why do we believe it?
why are other hypotheses weaker?
what evidence is missing?
what should the operator do next?
how trustworthy is the diagnosis?
```

This contract turns causal analysis into an operator-facing result.

## Top-Level RcaResult

```text
RcaResult
  resultId: string
  incidentId: string
  problemId: string?
  generatedAt: instant
  decision: RcaDecision
  confidence: double
  diagnosticQuality: DiagnosticQuality
  leadingClaim: CausalClaim?
  competingClaims: list<CausalClaim>
  rejectedClaims: list<CausalClaim>
  impactSummary: ImpactSummary
  evidenceTimeline: list<TimelineEvent>
  propagationPath: PropagationPath?
  missingEvidence: list<MissingEvidence>
  counterSignals: list<CounterSignal>
  providerTrust: list<ProviderTrustSummary>
  nextProbes: list<NextProbe>
  recommendedMitigations: list<RecommendedMitigation>
  ownership: OwnershipHint?
  auditTrail: list<AuditEvent>
  llmProposals: list<LlmCausalProposal>
```

Product rule:

```text
decision and confidence summarize validated causal claims.
LLM proposals are shown as proposals only, not as validated claims.
```

## RcaDecision

`RcaDecision` is the operator-facing decision. It should be more expressive than a confidence bucket.

| Decision | Meaning | Operator posture |
|---|---|---|
| `LIKELY_ROOT_CAUSE` | Strong evidence and causal guards passed. | Act on the mitigation, validate effect. |
| `PROBABLE_ROOT_CAUSE` | Good evidence, some uncertainty remains. | Mitigate if low risk, continue validation. |
| `POSSIBLE_ROOT_CAUSE` | Plausible but key evidence is incomplete. | Probe first unless urgency requires mitigation. |
| `COMPETING_HYPOTHESES` | Multiple candidates remain plausible. | Run differentiating probes. |
| `COMMON_CAUSE` | Shared dependency/infrastructure/provider explains multiple symptoms. | Escalate to shared owner or dependency owner. |
| `MULTI_ROOT` | More than one independent root cause is likely. | Split workstreams and track each claim. |
| `EXPLAINED_BY_ACTION` | Manual/control-plane action explains symptoms. | Verify expected action and blast radius. |
| `INSUFFICIENT_EVIDENCE` | Causal boundary cannot be crossed safely. | Collect missing evidence. |
| `OBSERVABILITY_DEGRADED` | RCA quality is limited by blind/degraded providers. | Repair observability or use alternate source. |

Mapping to algorithm:

```text
RcaDecision is derived from CausalClaim.allowedDecision,
competing claim relationships,
provider trust,
and incident/product context.
```

## DiagnosticQuality

`DiagnosticQuality` tells users how much to trust the RCA process itself.

| Value | Meaning |
|---|---|
| `NORMAL` | Required providers are healthy enough for the leading fault mode. |
| `DEGRADED` | One or more providers are blind/degraded, but enough evidence remains for a bounded claim. |
| `PARTIAL` | RCA can produce candidates, but important primary evidence sources are missing. |
| `BLIND` | Required evidence sources are unavailable; the system should not make strong causal claims. |
| `UNVERIFIED` | Result relies on proposals or context that have not been validated by deterministic guards. |

Rules:

1. `NO_SIGNAL` from a blind provider contributes to `diagnosticQuality`, not counter evidence.
2. A `LIKELY_ROOT_CAUSE` result should generally require `NORMAL` or carefully explained `DEGRADED` quality.
3. `BLIND` should cap root-cause decisions at `POSSIBLE_ROOT_CAUSE` or lower unless another independent provider supplies primary evidence.
4. `UNVERIFIED` must not be displayed as a validated root cause.

## CausalClaim

`CausalClaim` is the auditable unit of RCA reasoning.

```text
CausalClaim
  claimId: string
  candidateEntity: EntityRef
  affectedEntity: EntityRef
  faultMode: string
  relation: CausalRelation
  allowedDecision: AllowedDecision
  maxConfidence: double
  confidence: double
  evidenceRoles: EvidenceRoleSummary
  topologyPath: PropagationPath?
  temporalRelation: TemporalRelation
  guardResults: list<CausalGuardResult>
  missingEvidence: list<MissingEvidence>
  counterSignals: list<CounterSignal>
  providerTrustSummary: list<ProviderTrustSummary>
  explanation: ClaimExplanation
```

### CausalRelation

| Relation | Meaning |
|---|---|
| `CAUSED` | Strong deterministic evidence supports cause and effect. |
| `LIKELY_CAUSED` | Evidence supports the claim with some degraded/incomplete dimension. |
| `MAY_HAVE_CAUSED` | Plausible but needs additional probes. |
| `COMMON_CAUSE` | Candidate explains multiple affected entities through shared dependency/infrastructure/action. |
| `CAUSED_BY_EXTERNAL` | Cause is outside owned service graph. |
| `EXPLAINS_AS_SYMPTOM` | Event is better explained as an effect of another claim. |
| `ACTION_EXPLAINS_OBSERVATION` | A control-plane action explains the observed behavior. |
| `INSUFFICIENT_EVIDENCE` | Required evidence is missing. |
| `UNRELATED` | Topology/time/counter-signals argue against relation. |

### AllowedDecision

`AllowedDecision` is the algorithm-level cap before product-level decision composition.

| AllowedDecision | Max confidence |
|---|---:|
| `NOT_ROOT_CAUSE` | 0.00 |
| `UNCERTAIN_REQUIRES_MORE_EVIDENCE` | 0.49 |
| `POSSIBLE_ROOT_CAUSE` | 0.69 |
| `PROBABLE_ROOT_CAUSE` | 0.84 |
| `LIKELY_ROOT_CAUSE` | 0.95 |

## EvidenceRoleSummary

```text
EvidenceRoleSummary
  primary: list<EvidenceRef>
  secondary: list<EvidenceRef>
  symptoms: list<EvidenceRef>
  impacts: list<EvidenceRef>
  controlPlane: list<EvidenceRef>
  counter: list<EvidenceRef>
  noSignal: list<EvidenceRef>
```

Product rule:

```text
primary evidence explains why the claim can be strong.
symptoms explain blast radius.
counter evidence explains why confidence is lower or candidate was rejected.
```

## CausalGuardResult

```text
CausalGuardResult
  guardType:
    PRIMARY_EVIDENCE | TOPOLOGY | EXPLICIT_ACTION |
    TEMPORAL_ORDER | COUNTER_SIGNAL | PROVIDER_TRUST
  status: PASS | FAIL | DEGRADED | NOT_APPLICABLE
  cap: AllowedDecision?
  explanation: string
  evidenceIds: list<string>
```

Guard examples:

| Guard | Example result |
|---|---|
| `PRIMARY_EVIDENCE` | `FAIL`: crash loop missing exit code/startup log |
| `TOPOLOGY` | `PASS`: order-service calls payment-service |
| `EXPLICIT_ACTION` | `PASS`: GitHub deployment action found |
| `TEMPORAL_ORDER` | `FAIL`: anomaly predates deploy action |
| `COUNTER_SIGNAL` | `DEGRADED`: rollback did not improve symptom |
| `PROVIDER_TRUST` | `DEGRADED`: Loki blind, logs cannot be used as absence proof |

## NextProbe

When RCA is not decisive, the product should rank next probes.

```text
NextProbe
  probeId: string
  title: string
  targetProvider: PROMETHEUS | LOKI | TRACE | KUBERNETES | CLOUDTRAIL | GIT | MANUAL
  reason: string
  closesMissingEvidence: list<MissingEvidenceRef>
  expectedDecisionLift: HIGH | MEDIUM | LOW
  cost: LOW | MEDIUM | HIGH
  urgency: LOW | MEDIUM | HIGH
  commandOrQuery: string?
  manualInstruction: string?
```

Probe priority:

```text
priority = expectedDecisionLift * providerAvailability * urgency / cost
```

LLM may suggest probes, but the final probe should reference the missing guard or missing evidence.

## RecommendedMitigation

```text
RecommendedMitigation
  mitigationId: string
  actionType:
    ROLLBACK | DISABLE_FEATURE_FLAG | SCALE_UP | FAILOVER |
    DRAIN_NODE | RESTART_PROCESS | RESTORE_PROVIDER | ESCALATE | MANUAL
  targetEntity: EntityRef
  reason: string
  risk: LOW | MEDIUM | HIGH
  preconditions: list<string>
  validationProbe: NextProbe?
```

Product rules:

1. `LIKELY_ROOT_CAUSE` may recommend direct mitigation.
2. `PROBABLE_ROOT_CAUSE` may recommend mitigation with validation.
3. `POSSIBLE_ROOT_CAUSE` and `COMPETING_HYPOTHESES` should prefer probes before mitigation unless user impact is severe.
4. `OBSERVABILITY_DEGRADED` should include observability repair actions.

## Example Result: Downstream Payment Latency

```text
RcaResult
  decision: LIKELY_ROOT_CAUSE
  confidence: 0.88
  diagnosticQuality: NORMAL
  leadingClaim:
    candidateEntity: service:payment-service
    affectedEntity: service:order-service
    faultMode: DOWNSTREAM_DEPENDENCY_LATENCY
    relation: LIKELY_CAUSED
    allowedDecision: LIKELY_ROOT_CAUSE
    confidence: 0.88
    guardResults:
      - PRIMARY_EVIDENCE: PASS
      - TOPOLOGY: PASS
      - TEMPORAL_ORDER: PASS
      - COUNTER_SIGNAL: PASS
      - PROVIDER_TRUST: PASS
  nextProbes:
    - verify payment p95 after mitigation
  recommendedMitigations:
    - reduce traffic / rollback payment dependency change / escalate payment owner
```

## Example Result: Provider Blindness

```text
RcaResult
  decision: OBSERVABILITY_DEGRADED
  confidence: 0.42
  diagnosticQuality: DEGRADED
  leadingClaim:
    relation: MAY_HAVE_CAUSED
    allowedDecision: UNCERTAIN_REQUIRES_MORE_EVIDENCE
  missingEvidence:
    - Loki timeout logs unavailable because provider is blind
  nextProbes:
    - restore Loki ingestion
    - query alternate application logs
    - verify trace sampling before treating trace absence as counter evidence
```

## UI / Report Sections

A human-facing RCA page or markdown report should show:

1. Decision and confidence.
2. Diagnostic quality.
3. Leading claim.
4. Impact summary.
5. Propagation path.
6. Evidence timeline.
7. Why this claim.
8. Why not competing claims.
9. Missing evidence.
10. Next probes.
11. Recommended mitigation.
12. Provider health.
13. Audit trail.

## Glossary

| Term | Meaning |
|---|---|
| Alert | A signal from monitoring, often one symptom. |
| Incident | User-impacting operational event that may contain multiple alerts. |
| Problem | RCA analysis unit produced by grouping related alerts/events. |
| Evidence | Raw or normalized fact used by the engine. |
| Observation | Runtime fact observed from telemetry. |
| Action | Explicit system or operator change. |
| Claim | Auditable cause/effect assertion. |
| Decision | Product-facing RCA outcome. |
| Confidence | Calibrated probability-like summary after guards. |
| Diagnostic quality | Trustworthiness of the investigation process. |

## Design Conclusions

1. `RcaResult` is the product contract; `CausalClaim` is the reasoning contract.
2. Strong decisions must be backed by passed guards, not only high confidence.
3. Uncertainty should be useful: every missing evidence item should map to a next probe.
4. LLM/RAG/GraphRAG output belongs in proposals/context until deterministic validation promotes it to a claim.
