# Kubernetes Evidence Provider Architecture

## Overview

The SRE Agent collects Kubernetes evidence through a **provider abstraction** that separates the RCA core from K8s-specific data sources.

```
┌──────────────────────────────────────┐
│         RCA Core (sre-agent-core)    │
│  Evidence · Patterns · Hypotheses   │
│  Verification · Scoring · Report    │
└──────────────┬───────────────────────┘
               │ Evidence (generic, provider-agnostic)
┌──────────────┴───────────────────────────────────────┐
│    K8s Provider (sre-agent-k8s-provider)             │
│  KubernetesEvidenceProvider                          │
│  KubernetesResourceReader (interface)                │
│  KubernetesJsonParser  ·  KubernetesEvidenceMapper   │
├──────────────┬──────────────────┬────────────────────┤
│ FixtureK8s   │  KubectlK8s      │  JavaClientK8s     │
│ ResourceRdr  │  ResourceReader  │  ResourceReader    │
│ (unit tests) │  (local demo)    │  (production)      │
└──────────────┴──────────────────┴────────────────────┘
```

## Reader Implementations

### FixtureKubernetesResourceReader

- **Purpose**: Deterministic unit tests and CI
- **Data source**: Pre-canned JSON fixtures
- **Availability**: Always
- **Used by**: `mvn test`, Scenario F fixture tests

### KubectlKubernetesResourceReader

- **Purpose**: Local development demo with kind/minikube
- **Data source**: Live kubectl CLI calls
- **Availability**: Requires kubectl + K8s cluster
- **Used by**: `make collect-k8s-evidence-live --reader kubectl`
- **Limitation**: Not suitable for production (subprocess calls, no auth management)

### JavaClientKubernetesResourceReader (Production)

- **Purpose**: Production-grade in-cluster and external evidence collection
- **Data source**: Official Kubernetes Java Client (`io.kubernetes:client-java:24.0.0`)
- **Availability**: Requires ServiceAccount + RBAC (in-cluster) or kubeconfig (external)
- **Used by**: `--reader java-client`, in-cluster agent deployment

#### Configuration Modes

| Mode | Config Source | Use Case |
|------|--------------|----------|
| **Kubeconfig** | `~/.kube/config` or `KUBECONFIG` env | Local dev, external access, debugging |
| **In-Cluster** | ServiceAccount token + cluster CA | Production pods, automation |

When running inside a Kubernetes Pod, the reader automatically uses the mounted
ServiceAccount credentials (`/var/run/secrets/kubernetes.io/serviceaccount/`).
When running externally, it falls back to the standard kubeconfig chain.

#### API Calls

The reader uses two K8s API groups via the official Java client:

- **`CoreV1Api`** — pods, events, services
- **`AppsV1Api`** — deployments

Each call returns the native Java client object, which is serialised to JSON and
fed through the existing `KubernetesJsonParser` → `KubernetesEvidenceMapper`
pipeline — the same path used by the fixture and kubectl readers.

#### Why This Matters

The RCA core is **completely unchanged** — it continues to receive generic
`Evidence` objects and knows nothing about which reader produced them.  Swapping
between fixture, kubectl, and java-client readers is a one-flag change at the
CLI or a single environment variable in deployment manifests.

## Evidence Collection Flow

```
1. IncidentTask created (alert or CLI args)
         │
2. KubernetesEvidenceProvider.collectEvidence(incident)
         │
3. reader.listResources("pods", namespace, labels)
   reader.readResource("deployments", service, namespace, null)
   reader.listResources("events", namespace, labels)
         │
4. KubernetesJsonParser parses JSON → ParsedPod, ParsedDeployment, ParsedEvent
         │
5. KubernetesEvidenceMapper maps → Evidence objects (generic domain model)
         │
6. Evidence list returned to RCA core
```

## Evidence Types

| K8s Source | Evidence Type | Description |
|------------|---------------|-------------|
| Pod container status | `container_crash_loop_backoff` | Container in CrashLoopBackOff state (pod status.reason == "CrashLoopBackOff") |
| Pod container status | `container_oom_killed` | Container terminated by OOMKilled |
| Pod restart count | `pod_restart_count_increased` | High restart count detected |
| Pod readiness | `pod_not_ready` | Pod not passing readiness check (only when conditions indicate not ready, not just restartCount > 0) |
| Pod readiness | `pod_ready` | Pod has ready condition True (counter signal) |
| Pod container status | `k8s_runtime_healthy` | All containers in Running/Completed state (counter signal) |
| Pod restart count | `restart_count_observed` | Non-zero restart count observed (neutral observation, not scored) |
| Deployment spec | `deployment_metadata` | Deployment replica/image metadata |
| Events (Warning) | `k8s_event_unhealthy` | K8s event with reason containing Unhealthy |
| Events (Warning) | `k8s_event_failed_scheduling` | K8s event with reason FailedScheduling |
| Events (Warning) | `k8s_event_killing` | K8s event with reason Killing |
| Events (Normal) | `k8s_event_normal` | K8s event with type Normal (informational) |
| No anomalies | `k8s_no_signal` | No anomalies detected in K8s data |

### Semantic Typing (V.2-UI-4.1)

Previously, all K8s events were mapped to a generic `k8s_event` type. This has been replaced
with **semantic event mapping** via `KubernetesEvidenceMapper.mapEventsToSemanticEvidence()`:

- Warning events are classified by reason (Unhealthy, FailedScheduling, Killing)
- Normal events produce `k8s_event_normal` (informational context)
- `pod_not_ready` now requires actual readiness failure, not just restartCount > 0
- `container_crash_loop_backoff` only fires on explicit CrashLoopBackOff status
- Healthy pods produce counter signals (`k8s_runtime_healthy`, `pod_ready`)

This prevents false positives in scenarios where pods are healthy but have non-zero restart counts.

## CLI Usage

### Fixture mode (default)

```bash
java -jar sre-agent-cli.jar collect-k8s-evidence \
  --namespace demo \
  --service recommend-service \
  --output evidence.json \
  --reader fixture
```

### Live kubectl mode

```bash
java -jar sre-agent-cli.jar collect-k8s-evidence \
  --namespace demo \
  --service recommend-service \
  --output evidence.json \
  --reader kubectl
```

### Java Client mode (production)

```bash
# External — uses kubeconfig
java -jar sre-agent-cli.jar collect-k8s-evidence \
  --namespace production \
  --service recommend-service \
  --output evidence.json \
  --reader java-client

# In-cluster — uses ServiceAccount (no extra flags needed)
java -jar sre-agent-cli.jar collect-k8s-evidence \
  --namespace production \
  --service recommend-service \
  --output evidence.json \
  --reader java-client
```

### With fault detection

```bash
java -jar sre-agent-cli.jar collect-k8s-evidence \
  --namespace demo \
  --service recommend-service \
  --output evidence.json \
  --reader kubectl \
  --detect-faults
```

## RBAC Requirements

For production use (Java Client path):

```yaml
# Namespace-scoped, read-only
resources: [pods, pods/status, deployments, replicasets, services, events, namespaces]
verbs: [get, list, watch]
```

See `k8s/rbac/sre-agent-reader.yaml` for the full manifest.

## Design Decisions

1. **Provider interface in k8s module, not core**: Core has zero K8s dependency
2. **JSON-based intermediate format**: Readers return raw JSON; parser normalizes
3. **Evidence mapper decouples K8s schema from domain model**: Evidence objects are K8s-agnostic
4. **kubectl reader as local adapter**: Not a production path; exists for demo credibility
5. **Fixture reader always available**: Tests never depend on cluster availability
6. **Semantic typing over generic mapping**: Events and pod status are classified into precise evidence types that reflect the actual anomaly signal, preventing false positives in RCA ranking
