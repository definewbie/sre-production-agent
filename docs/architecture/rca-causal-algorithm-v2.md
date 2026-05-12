# RCA Causal Algorithm V2

**Status:** design proposal, no implementation changes  
**Last updated:** 2026-05-12  
**Audience:** engineers and SREs who need a clear, implementation-ready mental model without requiring algorithm background.

## Purpose

This document turns the causal reasoning design into a concrete algorithm model. The goal is to avoid two bad extremes:

1. A hand-tuned `ConfidenceScorer` that can accidentally turn weak symptoms into a high-confidence root cause.
2. An LLM-only RCA agent that sounds plausible but cannot prove causality from verified evidence.

The target model is:

```text
deterministic causal guards decide what is allowed
LLM/RAG/GraphRAG help collect and interpret context
confidence calibration ranks allowed candidates
```

In plain language: the system should first decide whether a suspect is even allowed to be considered a root cause. Only then should it talk about confidence percentages.

## Simple Mental Model

Think of RCA as answering four questions:

1. **What broke?**  
   Which service, pod, process, node, dependency, or external API showed symptoms?

2. **What could have caused it?**  
   Generate candidate root-cause entities from topology, hosting, recent actions, external dependencies, and direct local failures.

3. **Is the cause allowed by hard causal rules?**  
   A candidate must pass fault-mode-specific gates: primary evidence, topology path, explicit action when required, temporal order, counter-signals, and provider trust.

4. **How confident are we among allowed candidates?**  
   Confidence is a calibrated summary after the gates, not the mechanism that overrides the gates.

## Core Inputs

The algorithm works on normalized inputs, not raw provider strings.

```text
Problem
  window
  affectedEntities
  observations
  actions
  topologySubgraph
  providerHealth

ObservationEvent
  entity
  signal
  timestamp
  sourceKind
  strength
  trust

ActionEvent
  targetEntity
  actionType
  actor
  timestamp
  sourceKind
  trust

TopologyEdge
  fromEntity
  toEntity
  relation
  source
  confidence

FaultModeEvidenceContract
  requiredActions
  primaryEvidence
  secondaryEvidence
  symptomEvidence
  counterEvidence
  topologyRequirements
  temporalRequirements
  decisionGuards
```

## Algorithm Overview

```text
1. Normalize raw evidence
2. Normalize alerts into one problem
3. Build or retrieve the topology-time subgraph
4. Generate candidate root-cause entities
5. Generate candidate fault modes for each entity
6. Assign causal roles to evidence
7. Evaluate hard causal guards
8. Produce causal claims
9. Calibrate confidence for allowed claims
10. Explain result and recommend next probes
```

## Step 1: Normalize Evidence

Raw telemetry is converted into three kinds of facts:

| Raw input | Normalized fact |
|---|---|
| Prometheus latency spike | `ObservationEvent(LATENCY_SPIKE)` |
| Loki timeout log | `ObservationEvent(TIMEOUT)` |
| Jaeger slow child span | `ObservationEvent(LATENCY_SPIKE)` plus `TopologyEdge(CALLS)` |
| K8s `BackOff` event | `ObservationEvent(CRASH_LOOP or RESTART)` |
| K8s audit delete pod | `ActionEvent(MANUAL_POD_DELETE)` |
| GitHub deploy / ArgoCD sync | `ActionEvent(DEPLOYMENT)` |
| CloudTrail reboot | `ActionEvent(INSTANCE_REBOOT)` |

Important rule:

```text
Observation says "something happened."
Action says "someone or something changed the system."
Do not infer an ActionEvent from a generic ObservationEvent.
```

## Step 2: Normalize Alerts Into a Problem

A single failure chain can create several alerts. The system should create or update one `Problem` instead of launching one RCA run per alert.

Problem grouping uses:

1. Overlapping dynamic time window.
2. Same environment and ownership.
3. Same topology connected component.
4. Compatible causal claims.
5. Shared candidate root cause, if already known.

The dynamic window should start from alert rule semantics:

```text
dynamicWindow =
  alertEvaluationInterval
  + alertForDuration
  + providerIngestionDelay
  + expectedTopologyPropagationDelay
```

Then clamp it:

```text
dynamicWindow = min(max(dynamicWindow, minWindow), maxWindow)
```

This avoids both extremes: one-minute buckets are often too short, while unlimited merging hides repeated incidents.

## Step 3: Generate Candidate Root-Cause Entities

Candidates are generated from the graph around affected entities.

For an affected `order-service`, candidate entities may include:

1. `order-service` itself.
2. Downstream dependencies: `payment-service`, `inventory-service`, Redis, database, external API.
3. Hosting entities: pod, workload, node, EC2 instance.
4. Recent action targets: deployment, config, feature flag, manual operation.
5. Shared infrastructure or external dependency affecting multiple services.

This is the key shift:

```text
old: choose pattern first, then look for evidence
new: choose candidate entity first, then test fault modes against evidence contracts
```

## Step 4: Generate Candidate Fault Modes

Each candidate entity gets only fault modes that make sense for its type.

| Entity type | Example fault modes |
|---|---|
| service / external API | dependency latency, error spike, timeout |
| pod / workload | crash loop, image pull failure, scheduling failure |
| process | process crash, supervisor restart |
| node / instance | resource pressure, reboot, network failure |
| deployment / config / flag | deployment regression, configuration regression |
| database / queue | saturation, connection exhaustion, latency |

This prevents meaningless combinations such as `FEATURE_FLAG_CHANGE` on a Kubernetes node or `CrashLoopBackOff` on an external SaaS API.

## Step 5: Assign Causal Roles

The same evidence can play different roles depending on candidate, topology, and time.

```text
role = f(event, candidateEntity, affectedEntity, topologyPath, temporalRelation, actionContext, providerTrust)
```

Examples:

| Evidence | Candidate | Role |
|---|---|---|
| payment latency spike before order timeout | payment-service dependency latency | `PRIMARY_CAUSE_EVIDENCE` |
| order timeout log naming payment | payment-service dependency latency | `SECONDARY_CAUSE_EVIDENCE` / `IMPACT` |
| order restart after payment latency | order pod crash loop | `SYMPTOM`, unless primary crash evidence exists |
| order `CrashLoopBackOff` with exit code 1 and startup error | order pod crash loop | `PRIMARY_CAUSE_EVIDENCE` |
| deploy action near alert | deployment regression | `REQUIRED_ACTION_CONTEXT` |
| manual pod delete audit event | application crash | `COUNTER_SIGNAL` or `ACTION_EXPLAINS_OBSERVATION` |

## Step 6: Evaluate Hard Causal Guards

This is the most important part of the design. Hard guards decide the maximum decision level before any numeric confidence is calculated.

### Guard Types

| Guard | Meaning |
|---|---|
| Primary evidence guard | Does this fault mode have the evidence that actually proves this type of fault? |
| Topology guard | Can this candidate affect the impacted entity through a known or observed path? |
| Explicit action guard | Does this fault mode require a real action event, such as deploy/config/flag/reboot? |
| Temporal guard | Did the candidate anomaly or action happen before or during the impacted symptom? |
| Counter-signal guard | Is there evidence that argues against this candidate? |
| Provider trust guard | Are missing signals meaningful absence, or are providers blind/degraded? |

### Decision Caps

The output of guards is not a score. It is a cap.

```text
NOT_ROOT_CAUSE
UNCERTAIN_REQUIRES_MORE_EVIDENCE
POSSIBLE_ROOT_CAUSE
PROBABLE_ROOT_CAUSE
LIKELY_ROOT_CAUSE
```

Example caps:

| Situation | Max decision |
|---|---|
| `pod_crash_loop` has only restart count and NotReady, no crash reason/exit code/log | `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| `deployment_regression` has no explicit deploy/config/flag action | `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| `deployment_regression` has deploy action only, but no change-specific runtime evidence | `POSSIBLE_ROOT_CAUSE` |
| `downstream_dependency_latency` has no topology or observed dependency path | `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| provider required for primary evidence is blind | confidence is degraded, and next probes are required |
| strong counter-signal proves candidate healthy during problem window | `NOT_ROOT_CAUSE` or `POSSIBLE_ROOT_CAUSE`, depending on counter strength |

## Step 7: Produce Causal Claims

Instead of jumping directly to a final score, produce explicit causal claims.

```text
CausalClaim
  candidateEntity
  affectedEntity
  faultMode
  relation
  allowedDecision
  supportingEvidence
  symptomEvidence
  counterEvidence
  missingRequiredEvidence
  topologyPath
  temporalRelation
  providerTrustSummary
```

Example:

```text
payment-service DOWNSTREAM_DEPENDENCY_LATENCY LIKELY_CAUSED order-service timeout

because:
  payment latency started before/same-window as order timeout
  order-service calls payment-service
  traces show payment child span dominates checkout latency
  order logs timeout calling payment-service

not because:
  chaos was injected

chaos is only control-plane context
```

## Step 8: Calibrate Confidence

Only allowed claims receive numeric confidence.

```text
allowedDecision = evaluateHardGuards(...)

if allowedDecision == NOT_ROOT_CAUSE:
  confidence = 0
else:
  confidence = calibrate(
    primaryEvidenceStrength,
    secondaryEvidenceStrength,
    propagationStrength,
    temporalStrength,
    actionContextStrength,
    counterEvidenceStrength,
    providerTrust,
    historicalPrior
  )

confidence = min(confidence, allowedDecision.maxConfidence)
```

Simple mapping:

| Allowed decision | Max confidence |
|---|---|
| `NOT_ROOT_CAUSE` | 0.00 |
| `UNCERTAIN_REQUIRES_MORE_EVIDENCE` | 0.49 |
| `POSSIBLE_ROOT_CAUSE` | 0.69 |
| `PROBABLE_ROOT_CAUSE` | 0.84 |
| `LIKELY_ROOT_CAUSE` | 0.95 |

The exact numbers can be tuned later. The important point is that tuning cannot bypass the cap.

## Pseudocode

```text
runRca(problem):
  normalized = normalize(problem.rawEvidence)
  problem = correlateIntoProblem(normalized)
  graph = buildTopologyTimeGraph(problem)
  candidates = generateCandidateEntities(problem, graph)

  claims = []

  for candidate in candidates:
    faultModes = allowedFaultModes(candidate.entityType)

    for faultMode in faultModes:
      contract = getEvidenceContract(faultMode)
      roles = classifyEvidenceRoles(problem.events, candidate, faultMode, graph)
      guardResult = evaluateHardGuards(contract, roles, graph, problem.providerHealth)

      claim = buildCausalClaim(candidate, faultMode, roles, guardResult)

      if guardResult.allowedDecision != NOT_ROOT_CAUSE:
        claim.confidence = calibrateConfidence(claim, guardResult)

      claims.add(claim)

  return rankAndExplain(claims)
```

## Can LLM Participate in Judgement?

Yes, but only inside a bounded role.

The safe position is:

```text
LLM may judge what evidence appears to mean.
LLM must not be the final authority on whether causality is proven.
```

### LLM Allowed Roles

| Role | Example | Required guardrail |
|---|---|---|
| Evidence extraction | Parse log text into `startup failure`, `timeout to payment`, `connection refused`. | Output must cite raw evidence IDs. |
| Entity resolution | Map `pay-svc`, `payment`, `payment-service.default.svc` to same entity. | Deterministic resolver confirms or marks low confidence. |
| Causal role proposal | Suggest that order restart is a symptom of payment latency. | Contract evaluator validates against topology/time. |
| Missing probe suggestion | Recommend checking K8s audit log, CloudTrail, or rollback result. | Probe must be executable or clearly manual. |
| Historical similarity | Retrieve similar incident and likely fix. | Historical result is context, not current proof. |
| Report explanation | Explain why a claim is probable vs uncertain. | Explanation must only use verified claims/evidence. |

### LLM Forbidden Roles

1. Inventing evidence not present in telemetry, action logs, runbooks, or retrieved docs.
2. Raising a hypothesis above its guard cap.
3. Treating "recent deploy" as proof of deployment regression.
4. Treating absence of logs as counter evidence when the log provider is blind.
5. Producing a final root cause without evidence IDs and causal guard results.

### Recommended LLM Contract

LLM output should be structured:

```text
LlmCausalProposal
  proposedEntityId
  proposedFaultMode
  proposedRelation
  citedEvidenceIds
  reasoningSummary
  missingEvidence
  suggestedProbes
  confidenceHint: LOW | MEDIUM | HIGH
```

Then deterministic validation decides:

```text
validatedClaim = CausalGuardEngine.validate(llmProposal)
```

The LLM's `confidenceHint` is never used as final confidence. It can only prioritize which probes to run first.

## RAG, GraphRAG, and Graph Database Evaluation

These are related but solve different problems.

### Plain RAG

Plain RAG retrieves text chunks.

Useful for:

1. Runbooks.
2. Postmortems.
3. Service docs.
4. Deployment notes.
5. Known error explanations.
6. Code ownership docs.

Weak for:

1. Exact topology traversal.
2. Time-window joins.
3. Multi-hop impact analysis.
4. Proving that one entity can affect another.

Recommendation:

```text
Use RAG for explanatory context and next probes, not as the source of topology truth.
```

### GraphRAG

GraphRAG retrieves a subgraph plus related text.

Useful for:

1. Pulling the relevant service, owner, dependency, runbook, previous incidents, and deployment history together.
2. Explaining why the engine considered payment-service as a candidate for order-service.
3. Finding historical incidents with similar entity/fault-mode paths.
4. Expanding candidates beyond what traces saw, such as documented external dependencies.

Weak for:

1. Final causality unless backed by current telemetry and actions.
2. Real-time high-cardinality telemetry storage.
3. Low-latency alert processing if every RCA needs large retrieval.

Recommendation:

```text
Use GraphRAG as an investigation context layer.
Do not make GraphRAG the causal decision engine.
```

### Graph Database

A graph database stores entities and relationships explicitly.

Useful for:

1. Multi-hop topology traversal.
2. Shared dependency detection.
3. Common-cause grouping.
4. Impact radius queries.
5. Historical recurrence and similarity.
6. Ownership and service catalog integration.

Weak for:

1. Simple demo topology where an in-memory graph is enough.
2. Raw metric/log/trace storage.
3. Temporal reasoning unless the graph model includes validity windows.

Recommendation:

```text
Start with an in-memory typed graph.
Move to a graph database when topology, ownership, historical incidents, and external dependencies become too large or dynamic for local structures.
```

### Architecture Option Comparison

| Option | Pros | Cons | Recommendation |
|---|---|---|---|
| In-memory graph only | Simple, testable, fast for demo | Harder to query historical topology and cross-service context | Best first implementation |
| SQL + JSON topology | Good persistence, fewer dependencies | Multi-hop graph queries become awkward | Good intermediate step |
| Graph database | Natural topology and impact traversal | Adds operational complexity and data modeling burden | Use when service graph grows |
| Vector RAG | Great for runbooks/postmortems | Poor for exact causal graph logic | Add as advisory layer |
| GraphRAG | Best context retrieval for entity relationships + docs | Needs clean graph and retrieval discipline | Strong long-term fit |

## Proposed Architecture With Optional GraphRAG

```text
Telemetry Providers
  -> Evidence Normalizer
  -> Problem Correlator
  -> Causal Graph Builder
  -> Candidate Generator
  -> Evidence Contract Evaluator
  -> Causal Claim Engine
  -> Confidence Calibrator
  -> RCA Report

Knowledge Providers
  -> Service Catalog / CMDB
  -> Deployment History
  -> Runbooks / Postmortems / Docs
  -> Historical RCA Claims
  -> RAG / GraphRAG Context Retriever
  -> LLM Causal Proposal Generator
  -> Deterministic Validator
```

Key boundary:

```text
Knowledge layer can propose and enrich.
Causal engine must validate and decide.
```

## Scenario Walkthrough: Payment Latency vs Order Crash

Input:

```text
order-service alert: 5xx and timeout
payment-service observation: latency spike
order-service observation: restart count increased
topology: order-service CALLS payment-service
no order container exit code / startup failure log
```

Algorithm result:

1. Candidate `payment-service` with `DOWNSTREAM_DEPENDENCY_LATENCY`.
2. Candidate `order-service pod` with `CRASH_LOOP`.
3. Payment candidate passes topology guard.
4. Payment candidate has primary latency evidence.
5. Order crash candidate lacks primary crash evidence.
6. Order restart is classified as symptom/impact.

Expected claims:

```text
payment-service latency LIKELY_CAUSED order-service timeout
order-service restart EXPLAINS_AS_SYMPTOM of order-service impact
order-service pod crash loop INSUFFICIENT_EVIDENCE
```

The result is not hard-coded to chaos. It follows from generic gates.

## Scenario Walkthrough: Real Order Crash

Input:

```text
order-service CrashLoopBackOff
last termination exit code = 1
startup log: missing config
payment-service healthy
```

Algorithm result:

1. Candidate `order-service pod` with `CRASH_LOOP`.
2. Crash candidate has primary crash evidence.
3. Payment dependency has counter-signal: healthy.
4. Local crash does not require downstream topology.

Expected claim:

```text
order-service pod CRASH_LOOP LIKELY_CAUSED order-service availability drop
```

## Scenario Walkthrough: Deploy Near Alert But Not Root Cause

Input:

```text
order-service deploy at 10:00
payment-service latency starts at 09:58
order-service timeout starts at 10:02
rollback order-service does not improve
```

Algorithm result:

1. Deployment action creates a deployment-regression candidate.
2. But payment latency starts before order timeout and explains propagation.
3. Rollback no-improvement is counter evidence against deployment regression.
4. Deploy action alone caps deployment regression at possible.

Expected claims:

```text
payment-service latency LIKELY_CAUSED order-service timeout
order-service deployment regression POSSIBLE_ROOT_CAUSE or UNRELATED depending on runtime evidence
```

## Implementation Phasing

No implementation is required yet, but the design suggests this order:

### Phase 0: Fixtures and Docs

1. Keep documents and scenario fixtures.
2. Define expected causal claims for each scenario.
3. No production behavior change.

### Phase 1: Contract Evaluator

1. Add evidence contract data structures.
2. Add hard decision caps.
3. Keep current `ConfidenceScorer` as downstream calibrator.

### Phase 2: Causal Role Classifier

1. Classify evidence roles per candidate/fault mode.
2. Add provider trust semantics.
3. Produce `CausalClaim` output.

### Phase 3: Graph Model

1. Build in-memory typed graph from topology config, traces, K8s owner refs, and deployment actions.
2. Add multi-hop traversal and shared dependency detection.
3. Persist graph later only if needed.

### Phase 4: LLM/RAG Advisory

1. Use LLM to extract structured facts from logs/runbooks.
2. Use RAG for runbooks/postmortems.
3. Use GraphRAG only after entity graph quality is good enough.
4. Validate all LLM proposals through deterministic guards.

## Verification and Validation Strategy

This algorithm cannot be proven "always correct" in the mathematical sense. Production RCA is only partially observable: telemetry can be missing, sampled, delayed, mislabeled, or interpreted through stale topology.

The correct verification goal is narrower and more useful:

```text
prove the engine does not exceed evidence-backed causal boundaries
prove expected scenarios produce expected claims under stated assumptions
measure confidence calibration with historical and synthetic data
```

### 1. Soundness: Prove the Engine Does Not Overclaim

Soundness means the engine should not produce a stronger claim than the evidence allows.

Examples:

| Missing / conflicting condition | Required behavior |
|---|---|
| No primary crash evidence for `CRASH_LOOP` | Cannot exceed `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| No topology or observed dependency path for dependency latency | Cannot exceed `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| No explicit deploy/config/flag action for deployment regression | Cannot exceed `UNCERTAIN_REQUIRES_MORE_EVIDENCE` |
| Deploy action exists but no change-specific runtime evidence | Cannot exceed `POSSIBLE_ROOT_CAUSE` |
| Provider required for primary evidence is blind | Mark diagnostic quality degraded and recommend probes |
| Strong counter-signal proves candidate healthy | Lower cap or reject candidate depending on counter strength |

This can be tested with deterministic unit tests. It is the most important correctness property because it prevents high-confidence false positives.

### 2. Conditional Completeness: Prove Expected Results Under Assumptions

Completeness means the engine finds the expected cause when the required evidence is available.

This is conditional, not absolute:

```text
if topology is correct
and providers are healthy
and primary evidence exists
and temporal order is known
and counter-signals do not contradict the claim
then the expected root-cause candidate should rank first
```

Example:

```text
order-service CALLS payment-service
payment-service latency spike
order-service timeout after payment latency
trace child span dominated by payment-service
order logs timeout calling payment-service
```

Expected claim:

```text
payment-service DOWNSTREAM_DEPENDENCY_LATENCY LIKELY_CAUSED order-service timeout
```

The test should not rely on chaos evidence. Chaos may be action context, but the proof should come from topology, time, and telemetry.

### 3. Golden Scenario Fixtures

The scenario derivation document should become a fixture suite. Each fixture should define:

```text
input:
  topology
  observations
  actions
  provider health
  problem window

expected:
  leading causal claim
  allowedDecision cap per candidate
  evidence roles
  missing evidence
  counter-signals
  diagnostic quality
```

Recommended fixture categories:

1. Happy path root cause: enough evidence, clear topology, clear time order.
2. Counterfactuals: recent deploy but dependency root cause; restart but no crash evidence.
3. Provider blindness: missing logs/traces/k8s should degrade quality, not become negative proof.
4. Multi-alert normalization: one topology chain should update one problem.
5. Common cause: several services affected by the same dependency or node.
6. Independent incidents: unrelated topology components should not merge.
7. LLM adversarial proposals: plausible but unsupported LLM claims must be capped or rejected.
8. GraphRAG ablation: GraphRAG should improve candidate recall/context, not override guards.

### 4. Metrics for Algorithm Quality

Correctness should be measured at several levels.

| Metric | Meaning |
|---|---|
| Top-1 accuracy | Did the leading claim match the labeled root cause? |
| Top-3 recall | Was the labeled root cause in the top candidates? |
| False high-confidence rate | How often did `PROBABLE`/`LIKELY` claims turn out wrong? |
| Guard violation count | Did any result exceed its evidence-based cap? This should be zero. |
| Evidence role accuracy | Were primary/symptom/counter roles classified correctly? |
| Incident dedup accuracy | Did related alerts merge and independent alerts stay separate? |
| Diagnostic quality accuracy | Did blind/degraded provider labels match reality? |
| Calibration error | Does 80% confidence mean roughly 80% correctness over time? |
| Time to useful claim | How quickly does the engine produce a useful bounded claim? |

The most important early metric is:

```text
false high-confidence rate for weak-evidence hypotheses
```

The algorithm should prefer "uncertain, need probe" over a confident but weakly supported root cause.

### 5. Calibration Validation

Confidence values are not proof. They are calibrated summaries.

Validation should use:

1. Historical incident replay with known postmortems.
2. Synthetic/chaos experiments with known injection points.
3. Manually labeled golden datasets.
4. Regression tests for known false positives.

Useful calibration metrics:

```text
Brier score
expected calibration error
precision/recall by confidence band
false positive rate above 0.70
false positive rate above 0.85
```

If 0.80 confidence claims are correct only 55% of the time, the calibrator is wrong. The fix should adjust calibration, not weaken causal guards.

### 6. LLM Safety Validation

LLM participation requires specific tests.

| LLM behavior | Expected validator response |
|---|---|
| Proposes root cause without cited evidence IDs | Reject proposal |
| Treats recent deploy as sufficient proof | Cap at `POSSIBLE_ROOT_CAUSE` or reject if no explicit action |
| Treats blind Loki no-signal as counter evidence | Reject interpretation |
| Correctly extracts timeout target from logs | Accept as structured observation if raw evidence is cited |
| Suggests missing K8s audit/CloudTrail probe | Accept as next probe |
| Uses historical similar incident as current proof | Reject as proof; keep as context |

The LLM can increase investigation speed and candidate recall. It should not increase final confidence unless deterministic evidence confirms the proposal.

### 7. GraphRAG and Graph Database Validation

GraphRAG and graph databases should be validated by ablation:

```text
run RCA without GraphRAG
run RCA with GraphRAG
compare candidate recall, explanation quality, and false positive rate
```

Expected improvement:

1. More complete candidate generation.
2. Better next-probe suggestions.
3. Better historical similarity and ownership context.
4. Better explanation of multi-hop topology.

Not acceptable:

1. GraphRAG raises final confidence without verified current evidence.
2. Historical similarity overrides current counter-signals.
3. Stale service catalog topology creates high-confidence wrong claims.

For graph databases, validate:

1. Topology traversal correctness.
2. Temporal validity windows for edges.
3. Entity identity resolution.
4. Common-cause component detection.
5. Impact-radius query accuracy.

### 8. Review Gates Before Production Use

Before this algorithm should replace current scoring behavior, require:

1. All hard guard tests pass.
2. All golden scenario fixtures pass.
3. No guard violations in historical replay.
4. False high-confidence rate is below an agreed threshold.
5. Provider blindness tests pass.
6. LLM proposal validator rejects unsupported claims.
7. Human review confirms explanations are understandable and actionable.

## Design Conclusions

1. LLM can participate in RCA judgement as a bounded analyst, not as final authority.
2. GraphRAG can make investigation richer, but verified telemetry/action evidence must still prove current causality.
3. A graph database is likely useful later, but the first correct implementation should be an in-memory typed graph plus deterministic contract evaluator.
4. `ConfidenceScorer` should evolve into `ConfidenceCalibrator`: useful for ranking allowed claims, not for deciding whether weak evidence can become a root cause.
5. The core algorithm is a decision-cap model: hard causal guards first, numeric confidence second.
