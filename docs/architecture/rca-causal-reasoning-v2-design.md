# RCA Causal Reasoning V2 Design

**Status:** design proposal, no implementation changes  
**Last updated:** 2026-05-12  
**Scope:** replace scenario-specific scoring patches with a general causal model for incident grouping, evidence role inference, and root-cause ranking.

## Summary

The current RCA pipeline already has useful building blocks: evidence taxonomy, problem windows, topology paths, confidence scores, and LLM advisory reports. The recent E2E exposed a design gap: a Kubernetes crash/restart signal on the impacted service can outrank a downstream dependency latency root cause, even when topology and control-plane evidence point elsewhere.

The wrong fix is to hard-code:

```text
if chaos latency on payment-service and pod_crash_loop on order-service then penalize pod_crash_loop
```

The right fix is to model the investigation in a provider-neutral causal layer:

```text
raw evidence
  -> normalized event / observation / action
  -> correlated problem
  -> topology-time causal graph
  -> candidate cause entities
  -> fault-mode evidence contract
  -> causal role classification
  -> root-cause ranking
```

This document defines that target model.

## Design Goals

1. Avoid scenario-specific rules in `ConfidenceScorer`.
2. Distinguish root-cause evidence from symptoms, context, control-plane facts, and counter-signals.
3. Treat Kubernetes events as runtime observations whose cause may be application, platform, operator, scheduler, autoscaler, or node infrastructure.
4. Support non-Kubernetes deployments, such as EC2 instance processes or systemd services.
5. Require explicit deployment/change actions for `deployment_regression`; do not infer deployment regression from generic Kubernetes runtime events.
6. Keep deterministic RCA as the decision engine; LLM and GraphRAG may propose, explain, and retrieve context but must not invent evidence or override scores.
7. Make the model extensible to new providers: CloudTrail, GitHub Deployments, ArgoCD, Spinnaker, AWS ASG, EC2, systemd, CMDB, service catalog, feature flags.

## Non-Goals

1. Implement this design immediately.
2. Replace deterministic RCA with LLM-only reasoning.
3. Require Kubernetes for all RCA flows.
4. Make chaos evidence mandatory in production RCA.

## Industry Abstraction

Public AIOps/RCA platform material points to a common architecture:

| Platform pattern | Useful abstraction |
|---|---|
| Dynatrace Davis groups events into problems and uses topology, transactions, code/runtime context, and fault-tree style analysis. | Separate raw events from correlated problems; use topology and causality before ranking root causes. |
| BigPanda correlates alerts into incidents and classifies relationships such as source caused target, target caused source, common external cause, recurrence, and general relation. | Incident correlation needs relationship labels, not just same-time grouping. |
| Resolve AI describes agentic investigation across telemetry, infra, code, and runbooks with evidence-backed timelines. | LLM/agent works best as an investigator over structured evidence, not as the sole scorer. |

References:

- Dynatrace problem and RCA concepts: <https://docs.dynatrace.com/docs/platform/davis-ai/problem-and-root-cause/problem-overview>
- Dynatrace events and correlation: <https://docs.dynatrace.com/docs/discover-dynatrace/platform/davis-ai/root-cause-analysis/concepts/events>
- BigPanda incident intelligence: <https://docs.bigpanda.io/en/incident-intelligence>
- BigPanda incident correlation: <https://docs.bigpanda.io/en/incident-correlation.html>
- Resolve AI docs: <https://docs.resolve.ai/>

## Conceptual Model

### 1. Entity

RCA should reason over entities, not just services and evidence strings.

```text
Entity
  id: string
  type: SERVICE | ENDPOINT | PROCESS | POD | WORKLOAD | NODE | INSTANCE | DATABASE | QUEUE | EXTERNAL_API | DEPLOYMENT | FEATURE_FLAG
  platform: KUBERNETES | EC2 | BARE_METAL | SERVERLESS | MANAGED_SERVICE | UNKNOWN
  name: string
  namespaceOrAccount: string?
  owner: string?
  attributes: map
```

Examples:

```text
service:demo/order-service
service:demo/payment-service
k8s:pod/demo/order-service-7f8c9d
k8s:deployment/demo/order-service
ec2:i-0123456789abcdef0
process:i-0123456789abcdef0:demo/payment-service
deployment:order-service:git-sha-abc123
```

### 2. TopologyEdge

Topology is not only service calls. It must also express hosting, ownership, deployment, and infrastructure containment.

```text
TopologyEdge
  fromEntityId: string
  toEntityId: string
  relation:
    CALLS | DEPENDS_ON | HOSTED_ON | OWNS | MANAGES | DEPLOYED_BY | ROUTES_TO | BACKED_BY
  source: TRACE | CONFIG | CMDB | KUBERNETES_OWNER_REF | CLOUD_API | DISCOVERED | MANUAL
  confidence: HIGH | MEDIUM | LOW
  observedAt: instant?
  validFrom: instant?
  validTo: instant?
```

Examples:

```text
service:order-service CALLS service:payment-service
k8s:deployment/order-service OWNS k8s:pod/order-service-7f8c9d
k8s:pod/order-service-7f8c9d HOSTED_ON k8s:node/kind-worker
process:payment-service HOSTED_ON ec2:i-0123
deployment:order-service:abc123 DEPLOYED_BY github-actions:run-42
```

### 3. ObservationEvent

Observation events are what monitoring systems saw. They may be symptoms, cause candidates, context, or counter-signals depending on topology and time.

```text
ObservationEvent
  id: string
  entityId: string
  signal:
    LATENCY_SPIKE | ERROR_RATE_SPIKE | TIMEOUT | CRASH_LOOP | RESTART |
    OOM | POD_NOT_READY | SCHEDULING_FAILURE | NODE_NOT_READY |
    CPU_HIGH | MEMORY_HIGH | NO_SIGNAL | HEALTHY | UNKNOWN
  sourceKind: PROMETHEUS | LOKI | TRACE | KUBERNETES | ALERTMANAGER | EC2 | SYSTEMD | CLOUDWATCH | UNKNOWN
  timestamp: instant
  severity: INFO | WARNING | CRITICAL
  observedValue: any?
  attributes: map
  rawEvidenceIds: list<string>
```

### 4. ActionEvent

Action events are explicit changes or interventions. They are not inferred from generic runtime observations.

```text
ActionEvent
  id: string
  targetEntityId: string
  actionType:
    DEPLOYMENT | ROLLBACK | CONFIG_CHANGE | FEATURE_FLAG_CHANGE |
    MANUAL_POD_DELETE | NODE_DRAIN | AUTOSCALER_EVICTION |
    INSTANCE_REBOOT | PROCESS_RESTART | CHAOS_INJECTION |
    CREDENTIAL_ROTATION | NETWORK_POLICY_CHANGE | UNKNOWN
  actorType: HUMAN | CI_CD | CONTROLLER | AUTOSCALER | CHAOS_TOOL | CLOUD_PROVIDER | UNKNOWN
  actor: string?
  timestamp: instant
  sourceKind: GIT | ARGOCD | SPINNAKER | KUBERNETES_AUDIT | CLOUDTRAIL | CHAOS | MANUAL | UNKNOWN
  changeId: string?
  confidence: HIGH | MEDIUM | LOW
  attributes: map
```

This directly addresses deployment regression:

```text
deployment_regression requires ActionEvent(actionType=DEPLOYMENT or CONFIG_CHANGE)
```

A Kubernetes event like `Killing` or `BackOff` is not a deployment action. It may be a symptom of a deployment, but it is not itself sufficient to claim deployment regression.

### 5. CausalRole

The same raw evidence type can have different causal roles in different incidents.

```text
CausalRole
  PRIMARY_CAUSE_EVIDENCE
  SECONDARY_CAUSE_EVIDENCE
  SYMPTOM
  IMPACT
  CONTEXT
  CONTROL_PLANE
  COUNTER_SIGNAL
  NO_SIGNAL
  UNKNOWN
```

Examples:

| Evidence | Role depends on context |
|---|---|
| `pod_restart_count_increased` | `PRIMARY_CAUSE_EVIDENCE` only if paired with crash reason / exit code / app startup failure; otherwise `SYMPTOM` or `CONTEXT`. |
| `container_crash_loop_backoff` | Stronger than restart count, but still entity-scoped. It is primary for that pod/workload, not automatically the service-level root cause if upstream/downstream propagation explains it. |
| `metric_latency_p95_spike` | Usually `SYMPTOM`; becomes `PRIMARY_CAUSE_EVIDENCE` for a dependency latency hypothesis when it occurs on candidate dependency before impacted caller. |
| `chaos_fault_injected` | `CONTROL_PLANE`; strong intervention context in demo, but production scoring should work without it. |
| `deploy_event_near_alert_window` | `CONTROL_PLANE` / `PRIMARY_CAUSE_EVIDENCE` for deployment regression only if it is an explicit deploy/change action. |

### 6. Evidence Contract

Each fault mode should define evidence contracts, not just a flat list of supporting types.

```text
FaultModeEvidenceContract
  faultMode: string
  candidateEntityTypes: list<EntityType>
  primaryEvidence: list<EvidenceRequirement>
  secondaryEvidence: list<EvidenceRequirement>
  symptomEvidence: list<EvidenceRequirement>
  counterEvidence: list<EvidenceRequirement>
  requiredActions: list<ActionRequirement>
  topologyRequirements: list<TopologyRequirement>
  temporalRequirements: list<TemporalRequirement>
  decisionGuards: list<DecisionGuard>
```

`pod_crash_loop` example:

```text
faultMode: CRASH_LOOP
candidateEntityTypes: POD, WORKLOAD, PROCESS
primaryEvidence:
  - CRASH_LOOP state on candidate container
  - terminated state with non-zero exit code
  - app startup failure log for candidate container
secondaryEvidence:
  - restart count increased
  - pod not ready
symptomEvidence:
  - service error rate spike
counterEvidence:
  - pod ready
  - container running normal
decisionGuards:
  - without primaryEvidence, max decision = uncertain
  - restart/not_ready alone cannot produce probable_root_cause
```

`deployment_regression` example:

```text
faultMode: DEPLOYMENT_REGRESSION
requiredActions:
  - DEPLOYMENT | ROLLBACK | CONFIG_CHANGE | FEATURE_FLAG_CHANGE on candidate entity
primaryEvidence:
  - explicit deployment action inside problem window
  - anomaly starts after action
secondaryEvidence:
  - new image/version/commit
  - deployment rollout status changed
counterEvidence:
  - no action in window
  - anomaly predates action
decisionGuards:
  - no explicit action event => cannot be probable_root_cause
```

`downstream_dependency_latency` example:

```text
faultMode: DOWNSTREAM_DEPENDENCY_LATENCY
candidateEntityTypes: SERVICE, EXTERNAL_API, DATABASE, QUEUE
topologyRequirements:
  - impacted service CALLS/DEPENDS_ON candidate entity
primaryEvidence:
  - candidate latency spike before or concurrent with impacted timeout/error
  - trace child span dominates impacted request latency
secondaryEvidence:
  - impacted service timeout logs naming candidate
  - downstream latency metric from caller
symptomEvidence:
  - impacted service error rate spike
  - impacted pod restart/probe failure after latency spike
counterEvidence:
  - candidate latency normal
  - no topology path
decisionGuards:
  - no topology path => max decision = uncertain
```

### 7. CausalClaim

Instead of immediately producing a final score, the engine should produce causal claims.

```text
CausalClaim
  id: string
  causeEntityId: string
  effectEntityId: string
  relation:
    CAUSED | LIKELY_CAUSED | MAY_HAVE_CAUSED | COMMON_CAUSE | CAUSED_BY_EXTERNAL | UNRELATED
  faultMode: string?
  confidence: double
  evidenceIds: list<string>
  topologyPath: list<EntityId>
  temporalRelation: BEFORE | SAME_WINDOW | AFTER | UNKNOWN
  explanation: string
```

Example:

```text
payment-service latency LIKELY_CAUSED order-service timeout
  topology: order-service CALLS payment-service
  temporal: payment latency same/before order timeout
  evidence: metric_latency_p95_spike, trace_child_span_dominates_latency, log_downstream_timeout
```

## Proposed Pipeline

### Phase 1: Normalize

Input:

```text
Evidence from Prometheus, Loki, Trace, Kubernetes, Alertmanager, EC2, systemd, CI/CD, audit logs
```

Output:

```text
ObservationEvent[]
ActionEvent[]
TopologyEdge[]
```

Rules:

1. Metrics/logs/traces usually become `ObservationEvent`.
2. Deploys, rollouts, manual operations, cloud API changes, and chaos injections become `ActionEvent`.
3. Trace, CMDB, K8s owner refs, cloud inventory, and config become `TopologyEdge`.

### Phase 2: Correlate Problem

Group events into a single `Problem` using:

1. Problem window.
2. Same topology connected component.
3. Alertmanager grouping labels.
4. Similar service ownership / namespace / environment.
5. High-confidence causal claims from previous incidents.

Output:

```text
Problem
  id
  window
  affectedEntities
  events
  topologySubgraph
```

### Phase 3: Generate Candidate Cause Entities

Candidate entities come from:

1. Direct alert entity.
2. Downstream dependencies of alert entity.
3. Hosting infrastructure for alert entity.
4. Recent explicit action targets.
5. External dependencies in traces/logs.

This is the key shift from pattern-first to entity-first.

### Phase 4: Classify Causal Roles

For each event, classify role per candidate entity and fault mode:

```text
role = f(event, candidateEntity, impactedEntity, topologyPath, temporalRelation, actionContext)
```

Examples:

1. `pod_restart_count_increased` on impacted service after downstream latency is `SYMPTOM`.
2. `container_crash_loop_backoff` on candidate service before service errors is `PRIMARY_CAUSE_EVIDENCE`.
3. `deploy action` on candidate service before errors is `CONTROL_PLANE` plus possible `PRIMARY_CAUSE_EVIDENCE`.
4. `manual pod delete` is `ACTION_CONTEXT`; it may explain restarts without application bug.

### Phase 5: Apply Evidence Contracts

Each candidate fault mode is scored by:

1. Primary evidence coverage.
2. Secondary evidence coverage.
3. Counter evidence coverage.
4. Topology path strength.
5. Temporal ordering.
6. Explicit action evidence.
7. Provider health / blind spots.

Primary evidence gates should cap the decision:

```text
if primaryEvidence missing:
  maxDecision = uncertain
```

This prevents weak symptoms from becoming a high-confidence root cause.

### Phase 6: Rank and Explain

Output should include:

1. Root cause candidate.
2. Impact chain.
3. Symptom list.
4. Counter-signals.
5. Missing primary evidence.
6. Provider blind spots.
7. Next probes.

## Kubernetes Event Extensibility

Kubernetes events are observations emitted by controllers or kubelet. They are not automatically application root causes.

### K8s Event Source Classification

```text
KubernetesEventClassification
  eventReason: string
  eventType: Normal | Warning
  involvedObjectKind: Pod | Deployment | ReplicaSet | Node | ...
  reportingComponent: kubelet | scheduler | deployment-controller | ...
  actionLike: boolean
  probableSource:
    APPLICATION | OPERATOR | CONTROLLER | SCHEDULER | AUTOSCALER | NODE | IMAGE_REGISTRY | STORAGE | NETWORK | UNKNOWN
```

Examples:

| K8s event | Likely classification | RCA meaning |
|---|---|---|
| `BackOff` / `CrashLoopBackOff` | application or process crash observation | Primary evidence only if paired with container exit reason/logs. |
| `Killing` with liveness probe failure | kubelet action caused by health check failure | Often symptom/effect; need probe failure reason. |
| `FailedScheduling` | scheduler/platform capacity issue | Candidate infrastructure/platform root cause. |
| `Evicted` | node pressure / kubelet eviction | Candidate node/resource root cause, not app crash. |
| `PullBackOff` / `ErrImagePull` | image registry or image config | Candidate deployment/config/image root cause. |
| `ScalingReplicaSet` | controller rollout action context | Context for deployment regression; not enough alone. |
| `SuccessfulDelete` after human `kubectl delete pod` | operator action if audit log confirms | ActionEvent `MANUAL_POD_DELETE`; restart is expected effect. |

### Required Extension

To distinguish operator activity from application failures, Kubernetes evidence should eventually ingest:

1. Kubernetes audit logs.
2. Owner references.
3. Pod `lastState.terminated.reason`, `exitCode`, `startedAt`, `finishedAt`.
4. Container logs around restart.
5. Deployment rollout history.
6. HPA / VPA / Cluster Autoscaler events.
7. Node condition and eviction events.

## Non-Kubernetes / EC2 Extensibility

The model must work when demo-services run directly on EC2 instances.

### Entity Mapping

```text
SERVICE -> PROCESS -> INSTANCE -> ASG/VPC/SUBNET/AZ
```

Examples:

```text
service:payment-service
process:i-0123:payment-service
ec2:i-0123
asg:demo-payment-asg
az:ap-southeast-1a
```

### Provider Inputs

| Provider | Observation / action |
|---|---|
| CloudWatch metrics | CPU, memory if agent installed, disk, network, status check failures |
| systemd / process supervisor | process restart, exit code, service failed |
| journald / application logs | exceptions, timeout, startup failure |
| AWS CloudTrail | instance reboot, stop/start, ASG activity, security group changes |
| ALB/NLB logs | target 5xx, target response time, target health |
| SSM / CodeDeploy / GitHub Actions | explicit deployment actions |

### EC2 Example Contracts

`process_crash_loop`:

```text
primaryEvidence:
  - systemd service failed
  - process exit code non-zero
  - repeated restart by supervisor
secondaryEvidence:
  - application error logs
symptomEvidence:
  - ALB target unhealthy
counterEvidence:
  - process running normal
```

`instance_infra_failure`:

```text
primaryEvidence:
  - EC2 status check failed
  - instance reboot/stop action from CloudTrail
  - disk full / network unreachable
secondaryEvidence:
  - multiple services on same instance affected
counterEvidence:
  - instance healthy, only one process affected
```

## Deployment Regression Requires Explicit Action

`deployment_regression` should not be inferred from:

1. Pod restart alone.
2. Kubernetes `BackOff` alone.
3. New ReplicaSet metadata alone.
4. Generic deployment metadata.

It requires an explicit action:

```text
ActionEvent(actionType in DEPLOYMENT, ROLLBACK, CONFIG_CHANGE, FEATURE_FLAG_CHANGE)
```

Valid sources:

1. ArgoCD application sync.
2. Spinnaker pipeline execution.
3. GitHub Deployment API.
4. GitLab deployment.
5. Kubernetes Deployment rollout with revision and image digest, ideally enriched by audit/event source.
6. CodeDeploy deployment.
7. Feature flag audit event.
8. ConfigMap/Secret update audit event.

Decision guard:

```text
no explicit ActionEvent => deployment_regression maxDecision = uncertain
```

## LLM and GraphRAG Role

LLM and GraphRAG can help, but should not become the root-cause authority.

### Useful LLM Tasks

1. Extract structured candidate facts from unstructured logs and runbooks.
2. Propose missing probes.
3. Explain why an event is considered symptom vs cause.
4. Compare the current problem with historical incidents.
5. Convert causal graph output into readable reports.

### Useful GraphRAG Tasks

1. Retrieve service ownership, topology, runbooks, historical incidents, deployment history, and known failure modes.
2. Expand candidate causes using graph paths.
3. Provide context for external dependencies not visible in traces.
4. Suggest action/event sources to query.

### Guardrail

LLM/GraphRAG output must be typed as:

```text
UNVERIFIED_PROPOSAL
```

It can create candidate hypotheses or probe suggestions, but verified evidence and final scoring remain deterministic.

## Migration Plan

### Phase A: Documentation and Test Fixtures

1. Document causal roles and evidence contracts.
2. Add scenario fixtures for K8s, EC2, deployment, operator action, and downstream propagation.
3. No production behavior change.

### Phase B: Domain Model

Add internal domain types:

1. `Entity`
2. `TopologyEdgeV2`
3. `ObservationEvent`
4. `ActionEvent`
5. `CausalRoleAssignment`
6. `FaultModeEvidenceContract`
7. `CausalClaim`

Existing `Evidence` stays as provider input.

### Phase C: Primary Evidence Gates

Introduce contract-level guards:

1. `pod_crash_loop` requires primary crash evidence for probable/likely decisions.
2. `deployment_regression` requires explicit action event.
3. `downstream_dependency_latency` requires topology path plus candidate latency evidence.

### Phase D: Causal Role Classifier

Add deterministic classifier:

```text
classify(event, candidateEntity, impactedEntity, topology, problemWindow, actions) -> CausalRole
```

### Phase E: LLM/GraphRAG Advisory Layer

Let LLM/GraphRAG propose:

1. Missing action sources.
2. Similar incidents.
3. Candidate external dependencies.
4. Next probes.

Do not let it mutate final decisions.

## Open Questions

1. Should control-plane action evidence have a separate score dimension from observational evidence?
2. How strict should primary evidence gates be when providers are blind?
3. Should each fault mode define max decision levels when required evidence is missing?
4. How should multi-root incidents be represented?
5. Should incident normalization use connected component only, or causal relation labels like common external cause vs direct cause?

