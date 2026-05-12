# RCA Causal Design Review

**Status:** architecture and product review, no implementation changes  
**Last updated:** 2026-05-12  
**Review role:** SRE expert + RCA/AIOps product architect  
**Reviewed docs:**

1. [RCA Causal Model](./rca-causal-model.md)
2. [RCA Causal Reasoning V2 Design](./rca-causal-reasoning-v2-design.md)
3. [RCA Causal Algorithm V2](./rca-causal-algorithm-v2.md)
4. [RCA Causal Reasoning V2 Scenario Derivations](./rca-causal-reasoning-v2-scenarios.md)
5. [RCA Interaction PRD](../prd/rca_interaction_prd.md)
6. [RCA Interaction Prototype HTML](../prd/rca_interaction_prototype.html)

## Executive Verdict

The overall direction is sound and is now much closer to how a serious RCA product should work:

```text
incident normalization
  -> typed evidence/action/topology model
  -> candidate entity generation
  -> evidence contracts and hard causal guards
  -> causal claims
  -> calibrated confidence
  -> explainable operator output
```

The most important design correction is already present: **numeric confidence should not decide causality by itself**. Root-cause decisions should be bounded by primary evidence, topology, explicit actions, temporal order, counter-signals, and provider trust.

The main remaining gap is productization. The documents are strong on causal mechanics, but still need clearer answers to:

1. What should an on-call SRE see first?
2. What decisions are actionable vs informational?
3. How does the system explain uncertainty without looking broken?
4. How does human feedback improve future RCA without letting LLMs mutate production rules?
5. What is the smallest implementation slice that proves the model without a large rewrite?

## Product Review

Detailed UI / interaction review lives in [RCA Interaction PRD](../prd/rca_interaction_prd.md). The short version is that the current product should evolve from an RCA-run list into an incident investigation workspace: incident feed, diagnostic decision strip, root cause / critical failure / impact separation, causal path, evidence timeline, claim guard cards, next probes, mitigation, and a bounded AI investigation assistant.

### What Is Strong

1. **The decision-cap model matches SRE expectations.**  
   SREs prefer "uncertain, here is the missing evidence" over a confident but weakly supported root cause.

2. **Incident normalization is correctly elevated.**  
   A production RCA product should not run independent RCA for every alert in the same topology chain.

3. **Provider blindness is treated as a first-class product concern.**  
   This is important. Real observability stacks are often partially broken during incidents.

4. **LLM is framed as bounded analyst, not autonomous judge.**  
   This is the right trust posture for production SRE workflows.

5. **Scenarios cover meaningful user-facing failure modes.**  
   The scenario set includes dependency latency, real crash loops, manual operations, node pressure, EC2, feature flags, external dependencies, provider blindness, and recurrence.

### Product Gaps

#### 1. RCA Output Contract Needs to Be More Explicit

A useful RCA result should not only say "root cause = X." It should produce an operator-facing package:

```text
RcaResult
  decision
  leadingClaim
  confidence
  diagnosticQuality
  impactSummary
  evidenceTimeline
  propagationPath
  whyThisClaim
  whyNotOtherClaims
  missingEvidence
  recommendedNextProbes
  recommendedMitigation
  ownership / escalation target
  auditTrail
```

Recommended product decisions:

| Situation | Product output |
|---|---|
| Strong evidence | "Likely root cause" plus mitigation and evidence path |
| Missing primary evidence | "Needs more evidence" plus specific probes |
| Provider blind | "Diagnostic quality degraded" plus observability repair probe |
| Multiple plausible causes | "Competing hypotheses" with differentiating probes |
| Operator action explains symptoms | "Explained by control-plane action" rather than application root cause |
| External dependency | "External cause suspected" plus ownership/escalation guidance |

#### 2. Human Feedback Loop Is Missing

RCA products improve through review. The design should define feedback events:

```text
HumanFeedback
  acceptedClaimId?
  rejectedClaimId?
  actualRootCauseEntity?
  actualFaultMode?
  missingEvidenceNotes?
  remediationTaken?
  postmortemLink?
```

This feedback should feed historical replay and calibration, not directly mutate production rules.

#### 3. Trust UX Needs First-Class Design

The product should show why it is confident or uncertain:

```text
Confidence explanation:
  passed guards:
    - primary evidence present
    - topology path present
    - temporal order supports claim
  degraded dimensions:
    - Loki blind
    - trace sampling low
  missing evidence:
    - no deploy action source configured
```

This is how users learn to trust the system.

#### 4. Recommended Mitigation Is Not Yet Modeled

SRE workflows are action-oriented. The RCA report should distinguish:

1. **Mitigation now:** rollback, disable flag, fail over, scale up, drain node.
2. **Probe next:** check audit logs, query traces, inspect exit code.
3. **Repair observability:** fix Loki ingestion, enable audit logs.
4. **Escalate:** service owner, platform owner, external provider.

The current docs emphasize diagnosis. Product completeness requires next action.

## Architecture Review

### What Is Strong

1. **Entity-first is correct.**  
   The shift from pattern-first to candidate entity + fault mode is essential.

2. **ObservationEvent vs ActionEvent is a major architectural improvement.**  
   It prevents deployment regression, manual operations, and cloud actions from being inferred from runtime symptoms.

3. **EvidenceTrust is necessary.**  
   Without provider health and query coverage, `NO_SIGNAL` semantics will remain wrong.

4. **CausalClaim is the right intermediate product.**  
   It makes the engine auditable and gives the UI/report layer something meaningful to explain.

5. **GraphRAG and graph database are kept out of the critical decision path.**  
   This avoids architectural overreach.

### Architecture Gaps

#### 1. Need a Clear Bounded Core

The full target model is broad. The first implementation should define a bounded core:

```text
Core v1:
  Entity
  ObservationEvent
  ActionEvent
  EvidenceTrust
  FaultModeEvidenceContract
  CausalRoleAssignment
  CausalGuardResult
  CausalClaim
```

Everything else should adapt into or out of this core.

#### 2. Need Versioned Evidence Contracts

Evidence contracts should be data/config, not scattered imperative logic.

Recommended shape:

```text
FaultModeEvidenceContract
  id
  version
  candidateEntityTypes
  requiredActions
  primaryEvidence
  secondaryEvidence
  symptomEvidence
  counterEvidence
  topologyRequirements
  temporalRequirements
  decisionCaps
  providerRequirements
```

Contracts should be versioned so historical replay can explain which rules produced a past decision.

#### 3. Need Entity Identity and Alias Resolution

This is usually where RCA products get messy:

```text
payment
payment-service
payment.default.svc.cluster.local
deployment/payment-service
pod/payment-service-abc
process:payment-service
```

The design should make identity resolution explicit:

```text
EntityResolver
  aliases
  canonicalEntityId
  confidence
  source
```

LLM may suggest aliases, but deterministic resolver should confirm them.

#### 4. Need Temporal Validity on Topology

Topology is time-sensitive. A trace edge observed yesterday may not be valid during today's incident. The design mentions validity windows, but implementation planning should treat them as mandatory for serious RCA:

```text
TopologyEdge.validFrom
TopologyEdge.validTo
TopologyEdge.observedAt
TopologyEdge.sourceConfidence
```

#### 5. Need an Event Store Boundary

The model implies replayability. That requires storing normalized facts and decisions:

```text
NormalizedEventStore:
  ObservationEvent
  ActionEvent
  TopologyEdge snapshot
  EvidenceTrust snapshot
  CausalClaim
  GuardResult
  RcaDecision
```

Without this, debugging "why did RCA say that?" will be difficult.

## Algorithm Review

### What Is Strong

1. **Decision-cap model is the right core algorithm.**
2. **Soundness / conditional completeness / calibration split is correct.**
3. **LLM safety validation is explicit.**
4. **GraphRAG ablation is the correct way to evaluate knowledge-layer value.**
5. **Golden fixtures create a path from design to executable proof.**

### Algorithm Gaps

#### 1. Need Conflict Resolution Rules

The algorithm explains guards well, but conflict handling should be explicit:

```text
Conflict examples:
  primary evidence exists but strong counter-signal exists
  deploy action exists but anomaly predates deploy
  topology path exists but candidate is healthy
  trace says path exists but config topology disagrees
```

Recommended rule:

```text
counter-signal strength can lower allowedDecision before confidence calibration
```

#### 2. Need Multi-Root and Common-Cause Output

Real incidents can have:

1. One root cause with many symptoms.
2. Shared external cause.
3. Two independent simultaneous incidents.
4. A chain where an upstream issue reveals a latent local bug.

The output should support:

```text
RcaDecision:
  SINGLE_ROOT
  COMMON_CAUSE
  MULTI_ROOT
  COMPETING_HYPOTHESES
  EXPLAINED_BY_ACTION
  INSUFFICIENT_EVIDENCE
```

#### 3. Need Probe Planning Algorithm

When the result is uncertain, the product should not just list missing evidence. It should rank the next probes:

```text
probe priority = expectedDecisionLift * providerAvailability * costFactor * urgency
```

This is a good place for LLM assistance, but the final probe list should cite the missing guard.

#### 4. Need Calibration Data Strategy

The docs correctly name calibration metrics, but should later define the dataset:

1. Synthetic chaos runs with known injection.
2. Historical incidents with postmortems.
3. Manually reviewed RCA outputs.
4. Negative cases where tempting hypotheses are wrong.

Without labeled data, confidence values should be conservative.

## Implementation Review

### What Is Strong

1. The current docs correctly avoid a big-bang rewrite.
2. The recommended first implementation can reuse existing `Evidence`, `DiagnosticPattern`, `ServiceTopology`, and `ConfidenceScorer`.
3. The next step is correctly documentation/fixtures before production behavior changes.

### Implementation Risks

#### 1. Too Many New Types at Once

The proposed model has many types. Implementation should start with adapters around current classes:

```text
Evidence -> ObservationEvent / ActionEvent adapter
DiagnosticPattern -> FaultModeEvidenceContract adapter
ConfidenceScorer -> ConfidenceCalibrator facade
Hypothesis -> CausalClaim adapter
```

This avoids destabilizing the existing test suite.

#### 2. Evidence Contracts Must Start Small

Start with only three fault modes:

1. `DOWNSTREAM_DEPENDENCY_LATENCY`
2. `CRASH_LOOP`
3. `DEPLOYMENT_REGRESSION`

These cover the current E2E pain and validate the model.

#### 3. Golden Fixtures Should Be Implemented Before Engine Changes

The first executable artifact should be fixture tests that fail under the current weak spots:

1. Pod restart without crash primary evidence must not become high-confidence crash loop.
2. Deploy action alone must not become high-confidence deployment regression.
3. Provider blind no-signal must not become counter evidence.
4. Same topology chain alerts should map to one problem.

#### 4. Keep Graph Database Out of Phase 1

Use an in-memory typed graph first. A graph database is justified only when:

1. Entity count and topology churn exceed local graph practicality.
2. Multi-hop and historical queries become common.
3. Service catalog / CMDB / ownership / historical incident data are mature.

## Documentation Review

### What Is Strong

1. The four-doc split now makes sense.
2. The total direction is internally consistent.
3. The scenario document is valuable and should become executable fixtures.
4. The algorithm document is readable for non-algorithm engineers.

### Documentation Gaps

1. `rca-causal-model.md` is still long and mixes current implementation, target model, and history. It is acceptable as a living overview, but may eventually need a shorter "current target architecture" section at the top.
2. `rca-causal-reasoning-v2-scenarios.md` has fixture expectations for some scenarios, but not all. The next pass should make every scenario fixture-complete.
3. The design should eventually include a UI/report example so product behavior is visible.
4. The docs need a glossary for overloaded terms: incident, problem, alert, claim, hypothesis, evidence, observation, action, confidence, decision.
5. The docs should define "diagnosticQuality" levels consistently.

## Recommended Product Contract

The product should expose these top-level decisions:

| Decision | Meaning |
|---|---|
| `LIKELY_ROOT_CAUSE` | Strong evidence and guards passed. User can act, with audit trail. |
| `PROBABLE_ROOT_CAUSE` | Good evidence, some uncertainty remains. Recommend mitigation plus validation. |
| `POSSIBLE_ROOT_CAUSE` | Plausible, but key evidence is incomplete. Recommend probes first. |
| `COMPETING_HYPOTHESES` | Multiple candidates remain. Show differentiating probes. |
| `EXPLAINED_BY_ACTION` | Manual/control-plane action explains symptoms; may not be app defect. |
| `COMMON_CAUSE` | Shared dependency/infrastructure/provider better explains multiple alerts. |
| `INSUFFICIENT_EVIDENCE` | Cannot decide safely. Show missing evidence. |
| `OBSERVABILITY_DEGRADED` | RCA quality limited by provider blindness or incomplete telemetry. |

## Recommended Next Steps

### P0: Finish Design-to-Test Bridge

1. Convert all scenarios into golden fixture schemas.
2. Define `RcaResult` output contract.
3. Define `diagnosticQuality` enum and meaning.
4. Define `CausalClaim` JSON shape.

Companion contracts:

- [RCA Product Output Contract](./rca-product-output-contract.md)
- [RCA Golden Fixture Contract](./rca-golden-fixture-contract.md)
- [RCA Interaction PRD](../prd/rca_interaction_prd.md)
- [RCA Interaction Prototype HTML](../prd/rca_interaction_prototype.html)

### P1: Implement Minimal Causal Guard Slice

1. Adapter from existing `Evidence` to normalized observation/action facts.
2. Contract evaluator for three fault modes:
   - downstream dependency latency
   - crash loop
   - deployment regression
3. Decision cap output.
4. Current `ConfidenceScorer` called only after cap.

### P2: Incident Lifecycle and Provider Trust

1. Dynamic incident window.
2. Open/update/merge/split lifecycle.
3. Provider health and no-signal semantics.

### P3: Knowledge Layer

1. RAG for runbooks and postmortems.
2. LLM structured proposal with evidence citations.
3. GraphRAG only after entity graph quality is sufficient.

### P4: Calibration

1. Historical replay.
2. Chaos replay.
3. Human feedback loop.
4. Confidence calibration report.

## Final Assessment

The design is now conceptually strong. It is aligned with SRE practice because it prefers bounded, explainable, evidence-backed conclusions over confident guesses. It is aligned with RCA product practice because it separates alert grouping, topology context, candidate generation, evidence validation, and explanation.

The key product risk is not the causal model itself. The risk is implementing too much at once and losing the simple user promise:

```text
Tell me what probably caused this,
show me why,
show me what evidence is missing,
and tell me what to do next.
```

The next useful work is not more scoring. It is turning the scenarios into executable golden fixtures and defining the `RcaResult` / `CausalClaim` output contract.
