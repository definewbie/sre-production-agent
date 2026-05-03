# Step I: Kubernetes Evidence Provider

## Overview

The `sre-agent-k8s-provider` module provides Kubernetes evidence collection
capabilities for the SRE Production Agent. It acts as an adapter layer between
the Kubernetes API and the core RCA engine.

## Architecture

```
┌─────────────────────────────────────────────┐
│               CLI / Server                   │
├─────────────────────────────────────────────┤
│          KubernetesEvidenceProvider          │  ← Entry point
│  (orchestrates read → parse → map)          │
├──────────────────┬──────────────────────────┤
│  KubernetesJson  │  KubernetesEvidence      │
│  Parser          │  Mapper                  │
│  (parse K8s JSON)│  (K8s → Evidence domain) │
├──────────────────┴──────────────────────────┤
│       KubernetesResourceReader (interface)   │
├──────┬──────────┬───────────────────────────┤
│Fixture│ Kubectl  │  JavaClient               │
│Reader │ Reader   │  Reader (skeleton)         │
└──────┴──────────┴───────────────────────────┘
         ▲                  ▲
    test fixtures      kubectl CLI     (future: K8s Java Client)
```

## Module Structure

```
sre-agent-k8s-provider/
├── pom.xml
├── src/main/java/ai/sreagent/k8s/
│   ├── KubernetesResourceReader.java       # Interface
│   ├── FixtureKubernetesResourceReader.java # Test fixtures
│   ├── KubectlKubernetesResourceReader.java # kubectl adapter
│   ├── KubectlCommandRunner.java           # kubectl execution interface
│   ├── ProcessKubectlCommandRunner.java    # Process-based execution
│   ├── JavaClientKubernetesResourceReader.java # Production skeleton
│   ├── KubernetesResourceType.java         # K8s resource type enum
│   ├── KubernetesFaultMode.java            # Fault mode enum
│   ├── KubernetesJsonParser.java           # K8s JSON → parsed records
│   ├── KubernetesEvidenceMapper.java       # Parsed → Evidence domain
│   └── KubernetesEvidenceProvider.java     # Main orchestrator
└── src/test/
    ├── java/ai/sreagent/k8s/              # 24 unit tests
    └── resources/fixtures/                 # K8s fixture JSON files
```

## Reader Implementations

| Reader | Use Case | Cluster Required | Production Ready |
|--------|----------|-------------------|-----------------|
| FixtureKubernetesResourceReader | Testing & demos | No | N/A |
| KubectlKubernetesResourceReader | Local dev (kind/minikube) | Yes (local) | No |
| JavaClientKubernetesResourceReader | In-cluster production | Yes (remote) | Skeleton only |

## Fault Modes Detected

| Fault Mode | Signal | Default Strength |
|------------|--------|-----------------|
| pod_oom_killed | lastState.terminated.reason = "OOMKilled" | 0.90 |
| crash_loop_back_off | state.waiting.reason = "CrashLoopBackOff" | 0.85 |
| image_pull_back_off | state.waiting.reason = "ImagePullBackOff" | 0.80 |
| pod_not_ready | Phase ≠ Running or readiness probe failing | 0.70 |
| restart_count_increased | restartCount > 3 | 0.60 |
| pending_scheduling | phase = "Pending" | 0.65 |

## CLI Usage

```bash
# Collect evidence using test fixtures
java -jar sre-agent.jar collect-k8s-evidence \
  --service payment-service \
  --namespace production \
  --output /tmp/evidence.json \
  --reader fixture

# Collect from real cluster via kubectl
java -jar sre-agent.jar collect-k8s-evidence \
  --alert alert.json \
  --output /tmp/evidence.json \
  --reader kubectl \
  --detect-faults
```

## Dependencies

- `sre-agent-core` — Evidence/IncidentTask domain objects, zero K8s dependency
- `jackson-databind` — JSON parsing of K8s API responses
- No Spring dependency
- No Kubernetes client library (future: `io.kubernetes:client-java`)

## Security Notes

- RBAC manifest follows least-privilege: read-only access to pods, deployments, services, events
- Separate Role (single namespace) vs ClusterRole (cross-namespace) options
- No write permissions — this is a pure observer
- See `k8s/rbac-sre-agent.yaml` for full RBAC configuration
