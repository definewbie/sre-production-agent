# RCA Causal Reasoning V2 Scenario Derivations

**Status:** design validation scenarios, no implementation changes  
**Last updated:** 2026-05-12  
**Companion doc:** [RCA Causal Reasoning V2 Design](./rca-causal-reasoning-v2-design.md)

## Purpose

This document validates the proposed causal model by walking through representative incident scenarios. The goal is to prove that the model avoids scenario-specific hard-coding and remains extensible across Kubernetes, EC2, deployment, operator action, and downstream dependency failures.

Each scenario follows the same reasoning structure:

```text
signals -> entities -> topology -> actions -> causal roles -> candidate ranking -> expected result
```

## Scenario 1: Downstream Payment Latency Causes Order Errors

### Setup

```text
topology:
  order-service CALLS payment-service

action:
  CHAOS_INJECTION latency on payment-service

observations:
  payment-service latency spike
  order-service timeout logs calling payment-service
  order-service error rate spike
  trace child span payment-service dominates order-service checkout latency
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Chaos latency injection | payment-service | CONTROL_PLANE |
| Latency spike | payment-service | PRIMARY_CAUSE_EVIDENCE for downstream dependency latency |
| Timeout logs | order-service | SECONDARY_CAUSE_EVIDENCE / IMPACT |
| Error rate spike | order-service | SYMPTOM |
| Trace child span dominates | payment-service in order trace | PRIMARY_CAUSE_EVIDENCE |

### Expected Ranking

```text
1. payment-service DOWNSTREAM_DEPENDENCY_LATENCY
2. order-service SERVICE_INTERNAL_ERROR
3. order-service POD_CRASH_LOOP only if primary crash evidence exists
```

### Why This Is Not Hard-Coded

The result follows from generic rules:

1. Candidate entity is downstream dependency of impacted service.
2. Candidate anomaly exists on dependency.
3. Impacted anomaly exists on caller.
4. Topology path explains propagation.
5. Control-plane evidence is context, not the only reason for the decision.

The same reasoning works without chaos evidence if telemetry still proves payment latency and order impact.

## Scenario 2: Downstream Payment Latency Causes Order Restart / Probe Failure

### Setup

```text
topology:
  order-service CALLS payment-service

observations:
  payment-service latency spike
  order-service timeout logs
  order-service liveness probe failures
  order-service pod restart count increased
  maybe order-service temporarily NotReady
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Latency spike | payment-service | PRIMARY_CAUSE_EVIDENCE |
| Timeout logs | order-service | IMPACT |
| Liveness probe failure | order-service pod | SYMPTOM / IMPACT |
| Restart count | order-service pod | SYMPTOM, not primary crash evidence |
| Pod NotReady | order-service pod | SYMPTOM |

### Expected Ranking

```text
payment-service DOWNSTREAM_DEPENDENCY_LATENCY > order-service POD_CRASH_LOOP
```

### Decision Guard

`pod_crash_loop` cannot become probable/likely root cause from restart/not_ready alone. It needs primary crash evidence:

```text
container_crash_loop_backoff
non-zero exit code
application startup failure logs
OOMKilled
```

This is a general primary-evidence gate, not a Scenario G-specific penalty.

## Scenario 3: Real Order-Service CrashLoop Is the Root Cause

### Setup

```text
topology:
  order-service CALLS payment-service

observations:
  order-service container CrashLoopBackOff
  order-service lastState.terminated.exitCode = 1
  order-service startup log: cannot bind port / missing config
  order-service error rate spike
  payment-service healthy
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| CrashLoopBackOff | order-service pod | PRIMARY_CAUSE_EVIDENCE |
| Non-zero exit code | order-service container | PRIMARY_CAUSE_EVIDENCE |
| Startup failure log | order-service container | PRIMARY_CAUSE_EVIDENCE |
| Payment healthy | payment-service | COUNTER_SIGNAL against downstream dependency |
| Order error rate | order-service | SYMPTOM |

### Expected Ranking

```text
order-service POD_CRASH_LOOP > payment-service DOWNSTREAM_DEPENDENCY_LATENCY
```

### Why This Is Correct

The model does not globally suppress crash-loop hypotheses. It only prevents weak crash symptoms from becoming root cause without primary evidence.

## Scenario 4: Manual Pod Delete Causes Restart Noise

### Setup

```text
action:
  Kubernetes audit log: user alice ran delete pod order-service-abc

observations:
  order-service pod terminated
  new pod created
  restart/replacement observed
  service remains healthy or brief blip
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Audit delete pod | order-service pod | CONTROL_PLANE / ACTION_CONTEXT |
| Pod termination | order-service pod | IMPACT of operator action |
| New pod created | order-service workload | CONTEXT |
| Brief error spike | order-service | SYMPTOM |

### Expected Result

```text
No application root cause.
If incident exists, likely operator action / expected disruption.
pod_crash_loop should not be probable root cause.
```

### Required Extension

This requires Kubernetes audit logs or a provider that can produce:

```text
ActionEvent(actionType=MANUAL_POD_DELETE, actorType=HUMAN)
```

Without audit logs, the model should mark the result as uncertain and recommend checking audit events.

## Scenario 5: Node Pressure Evicts Pods

### Setup

```text
topology:
  order-service pod HOSTED_ON node-a
  payment-service pod HOSTED_ON node-a

observations:
  node-a MemoryPressure
  pod eviction events on multiple services
  order-service NotReady
  payment-service NotReady
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Node MemoryPressure | node-a | PRIMARY_CAUSE_EVIDENCE for infrastructure/resource pressure |
| Pod eviction | pods on node-a | IMPACT |
| Service NotReady | services on node-a | SYMPTOM |
| Multiple services same node affected | node-a topology group | SECONDARY_CAUSE_EVIDENCE |

### Expected Ranking

```text
node-a INFRA_RESOURCE_PRESSURE > individual service POD_CRASH_LOOP
```

### Why This Matters

Kubernetes events may point to infrastructure rather than application bugs. The candidate entity should be node/infrastructure, not each service independently.

## Scenario 6: FailedScheduling Due to Cluster Capacity

### Setup

```text
observations:
  Kubernetes FailedScheduling: insufficient cpu
  deployment replicas desired > ready
  service availability degraded
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| FailedScheduling | workload / cluster | PRIMARY_CAUSE_EVIDENCE for scheduling/capacity |
| Desired replicas not ready | workload | SYMPTOM |
| Service degraded | service | IMPACT |

### Expected Ranking

```text
cluster/workload SCHEDULING_CAPACITY > service_internal_error
```

### Required Data

1. Scheduler event reason/message.
2. Node allocatable/resource requests.
3. Pending pod details.

## Scenario 7: Deployment Regression With Explicit Deploy Action

### Setup

```text
action:
  GitHub Deployment / ArgoCD sync deploys order-service image sha abc123 at 10:05

observations:
  order-service error rate starts at 10:07
  order-service exception logs mention new code path
  payment-service healthy
  no resource pressure
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Deployment action | order-service deployment | PRIMARY_CAUSE_EVIDENCE / CONTROL_PLANE |
| Error starts after deploy | order-service | PRIMARY_CAUSE_EVIDENCE |
| Exception logs | order-service | SECONDARY_CAUSE_EVIDENCE |
| Payment healthy | payment-service | COUNTER_SIGNAL against dependency latency |

### Expected Ranking

```text
order-service DEPLOYMENT_REGRESSION > downstream_dependency_latency
```

### Key Rule

Deployment regression requires explicit action evidence. It should not be inferred only from Kubernetes deployment metadata.

## Scenario 8: Kubernetes Rollout Metadata Without Explicit Deploy Action

### Setup

```text
observations:
  deployment_metadata exists
  ReplicaSet metadata exists
  service error rate spike
  no GitHub/ArgoCD/CodeDeploy/K8s audit deploy action
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Deployment metadata | workload | CONTEXT |
| ReplicaSet metadata | workload | CONTEXT |
| Error spike | service | SYMPTOM |

### Expected Ranking

```text
deployment_regression maxDecision = uncertain
```

### Why This Matters

Runtime metadata tells us what exists now. It does not prove a change happened in the problem window.

## Scenario 9: Feature Flag Regression

### Setup

```text
action:
  feature flag payment.v2.enabled changed from false to true at 10:00

observations:
  payment-service error rate starts at 10:02
  order-service upstream errors follow
  no image deployment
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Feature flag action | payment-service / flag entity | PRIMARY_CAUSE_EVIDENCE for config/change regression |
| Payment errors | payment-service | PRIMARY_CAUSE_EVIDENCE |
| Order errors | order-service | IMPACT |
| Topology order -> payment | service graph | TOPOLOGY_CONTEXT |

### Expected Ranking

```text
payment-service CONFIGURATION_REGRESSION > deployment_regression
```

### Extension Point

The action model supports this without changing the core RCA pipeline. Add a feature flag provider that emits `ActionEvent(FEATURE_FLAG_CHANGE)`.

## Scenario 10: EC2 Process CrashLoop

### Setup

```text
platform:
  payment-service runs as systemd service on EC2 i-0123

observations:
  systemd payment-service failed and restarted repeatedly
  process exit code 1
  journald startup failure log
  ALB target 5xx / unhealthy
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| systemd failed | process:payment-service | PRIMARY_CAUSE_EVIDENCE |
| exit code 1 | process:payment-service | PRIMARY_CAUSE_EVIDENCE |
| startup failure log | process:payment-service | PRIMARY_CAUSE_EVIDENCE |
| ALB unhealthy | service target | SYMPTOM |

### Expected Ranking

```text
payment-service PROCESS_CRASH_LOOP
```

### Why This Validates Non-K8s Support

The model uses `Entity` and `ObservationEvent`, not Kubernetes-only Pod objects. Kubernetes and EC2 are just different provider mappings into the same causal model.

## Scenario 11: EC2 Instance Reboot Caused Service Errors

### Setup

```text
action:
  CloudTrail StopInstances or RebootInstances for i-0123

topology:
  payment-service process HOSTED_ON ec2:i-0123

observations:
  payment-service unavailable
  order-service timeout to payment-service
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| EC2 reboot action | ec2:i-0123 | CONTROL_PLANE / PRIMARY_CAUSE_EVIDENCE |
| Payment unavailable | payment-service | IMPACT |
| Order timeout | order-service | IMPACT |

### Expected Ranking

```text
ec2:i-0123 INSTANCE_REBOOT / INFRA_ACTION > payment-service internal error
```

### Required Extension

CloudTrail or EC2 provider emits:

```text
ActionEvent(actionType=INSTANCE_REBOOT, sourceKind=CLOUDTRAIL)
```

## Scenario 12: External Payment Gateway Latency

### Setup

```text
topology:
  payment-service CALLS external payment-gateway
  order-service CALLS payment-service

observations:
  payment-gateway spans slow
  payment-service latency spike
  order-service timeout/error
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Gateway slow span | external payment-gateway | PRIMARY_CAUSE_EVIDENCE |
| Payment latency | payment-service | IMPACT |
| Order timeout | order-service | IMPACT |

### Expected Ranking

```text
external payment-gateway DOWNSTREAM_DEPENDENCY_LATENCY
```

### Why This Matters

Candidate root cause entity may be outside the application service set.

## Scenario 13: Common External Cause Affects Multiple Services

### Setup

```text
topology:
  order-service DEPENDS_ON redis
  payment-service DEPENDS_ON redis
  inventory-service DEPENDS_ON redis

observations:
  all services timeout to redis
  redis latency spike
  no direct order -> payment causal path explains all symptoms
```

### Causal Role Classification

| Signal | Entity | Role |
|---|---|---|
| Redis latency | redis | PRIMARY_CAUSE_EVIDENCE |
| Service timeouts | services | IMPACT |
| Multi-service same dependency | topology | SECONDARY_CAUSE_EVIDENCE |

### Expected Relationship

```text
redis COMMON_EXTERNAL_CAUSE for order/payment/inventory
```

### Incident Correlation

Multiple alerts should normalize into one incident, but the relationship label is not direct propagation between services. It is common external cause.

## Scenario 14: Provider Blindness

### Setup

```text
Prometheus available
Loki no_signal
Trace available
Kubernetes unavailable

observations:
  Prometheus latency spike
  Trace slow downstream span
  no logs
```

### Expected Behavior

1. Missing Loki logs should be a reduced penalty, not proof that no timeout occurred.
2. Kubernetes-unavailable should not make pod hypotheses impossible, but should cap confidence if primary evidence requires K8s.
3. Report should mark diagnostic quality degraded.

### Design Rule

Distinguish:

```text
not observed because provider blind
not observed even though provider healthy
```

## Scenario 15: No Topology Path

### Setup

```text
observations:
  order-service error rate spike
  payment-service latency spike

topology:
  no known order -> payment dependency
  no trace path
```

### Expected Behavior

`downstream_dependency_latency` cannot become probable/likely root cause without topology or observed dependency evidence.

Expected result:

```text
competing_hypotheses or uncertain_requires_more_evidence
next probe: collect trace topology / dependency graph
```

## Scenario 16: Multi-Hop Dependency Propagation

### Setup

```text
topology:
  checkout-service CALLS order-service
  order-service CALLS payment-service
  payment-service CALLS risk-control-service

observations:
  risk-control-service latency spike
  payment-service latency spike
  order-service timeout
  checkout-service error rate spike
```

### Expected Ranking

```text
risk-control-service DOWNSTREAM_DEPENDENCY_LATENCY
```

### Scoring Rule

Propagation score should be path-length aware:

```text
direct path > two-hop path > three-hop path
```

But a multi-hop path with strong trace evidence can still outrank a direct but weak hypothesis.

## Scenario 17: Multiple Independent Incidents

### Setup

```text
incident A:
  payment-service latency affects order-service

incident B:
  inventory-service OOMKilled affects stock reservation

same broad time window
same namespace
```

### Expected Behavior

The system should not over-normalize by namespace or time alone.

Expected:

```text
two incidents if topology components or causal claims are independent
```

### Design Rule

Incident normalization should use:

1. Time overlap.
2. Topology connected component.
3. Causal relation labels.
4. Shared candidate root cause.

Time alone is insufficient.

## Scenario 18: Repeated Same Incident Within Normalization Window

### Setup

```text
same topology component
same candidate root cause
alerts:
  payment-service latency alert
  order-service 5xx alert
  order-service pod restart alert
within 5 minutes
```

### Expected Behavior

One incident/problem, not three RCA runs.

The event relationship labels may include:

```text
payment latency LIKELY_CAUSED order 5xx
payment latency LIKELY_CAUSED order restart/probe symptom
```

## Scenario 19: Recurrence After Normalization Window

### Setup

```text
same payment latency issue appears at 10:00
resolved
appears again at 11:00
```

### Expected Behavior

New problem instance with recurrence linkage:

```text
incident relation = RECURRENCE
```

The system should not merge indefinitely, but should surface historical similarity.

GraphRAG can help retrieve the earlier incident and its fix.

## Scenario 20: LLM/GraphRAG Proposal, Deterministic Verification

### Setup

```text
LLM sees logs:
  "connection refused to redis-master:6379"

GraphRAG retrieves:
  order-service depends on redis for cart lock
  similar incident last month caused by redis maxclients
```

### Expected Behavior

LLM/GraphRAG may propose:

```text
UNVERIFIED_PROPOSAL: redis connection saturation
next probes:
  query Redis connected_clients
  query maxclients
  inspect redis slowlog
```

It must not directly change final RCA until probes produce verified evidence.

## Scenario Matrix

| Scenario | Root cause entity | Key guard |
|---|---|---|
| Payment latency -> order errors | payment-service | topology + candidate latency primary evidence |
| Payment latency -> order restart | payment-service | restart is symptom without crash primary evidence |
| Real order CrashLoop | order pod/workload | crash primary evidence exists |
| Manual pod delete | operator action | audit ActionEvent explains restart |
| Node pressure eviction | node | shared hosting topology |
| FailedScheduling | cluster/workload | scheduler event primary evidence |
| Deployment regression | deployment action target | explicit deploy/config action required |
| Metadata only | none/uncertain | metadata is context, not action |
| Feature flag regression | flag/service | explicit feature flag action |
| EC2 process crash | process | systemd/exit-code primary evidence |
| EC2 reboot | instance | CloudTrail action evidence |
| External gateway latency | external dependency | external entity candidate |
| Common external cause | shared dependency | common-cause relation |
| Provider blindness | uncertain/degraded | missing evidence penalty depends on provider health |
| No topology path | uncertain | topology guard |
| Multi-hop propagation | deepest dependency | path length + trace confidence |
| Independent incidents | separate candidates | avoid time-only merging |
| Same incident alerts | same candidate | normalize to one problem |
| Recurrence | linked new incident | recurrence relation |
| LLM proposal | unverified | probes required before scoring |

## Design Conclusions

1. The correct abstraction is not "penalize pod crash when chaos latency exists." It is "pod crash loop requires primary crash evidence; restart/not_ready may be symptoms depending on topology and time."
2. Kubernetes events require source classification. A K8s event may represent an application failure, operator action, scheduler decision, autoscaler action, or node problem.
3. EC2 and Kubernetes can share the same RCA model if both map into `Entity`, `ObservationEvent`, `ActionEvent`, and `TopologyEdge`.
4. Deployment regression must be based on explicit action evidence, not guessed from runtime metadata.
5. LLM/GraphRAG should expand investigation context and propose probes, but deterministic evidence contracts and causal role classification should decide final RCA.

