# Live K8s Demo — Scenario F CrashLoopBackOff

## 1. Purpose

This guide walks through the **live Kubernetes demo** for the SRE Production Agent.

Unlike the fixture-based tests (which use pre-canned JSON), this demo:

- Deploys a real CrashLoopBackOff workload into a local `kind` cluster
- Collects **live Kubernetes evidence** via `kubectl`
- Feeds the collected evidence into the same RCA workflow
- Produces a `pod_crash_loop → likely_root_cause` diagnosis

This proves the provider abstraction can handle both fixture data and real cluster data.

## 2. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker Desktop | 20+ | [docker.com](https://docker.com) |
| kind | 0.20+ | `brew install kind` |
| kubectl | 1.28+ | `brew install kubectl` |
| Java 21 | 21 | SDKMAN or brew |
| Maven | 3.9+ | brew |

Verify:

```bash
docker version
kind version
kubectl version --client
java -version
mvn -version
```

## 3. Docker Desktop Resource Recommendation (M3 Max)

| Resource | Recommended | Minimum |
|----------|-------------|---------|
| CPU | 6-8 cores | 4 cores |
| Memory | 12-16 GB | 8 GB |
| Disk | 80-120 GB | 40 GB |

Settings → Resources in Docker Desktop.

## 4. Build the Project

```bash
make build
```

This runs `mvn package -DskipTests` and produces the CLI jar.

## 5. Start kind Cluster

```bash
make cluster-up
```

This creates a 3-node kind cluster named `sre-agent` (1 control-plane + 2 workers).

If the cluster already exists:

```bash
make cluster-down
make cluster-up
```

## 6. Create Namespaces

```bash
make namespaces
```

Creates three namespaces:

| Namespace | Purpose |
|-----------|---------|
| `demo` | Demo workloads (CrashLoopBackOff, nginx smoke) |
| `observability` | Future Prometheus / Loki (placeholder) |
| `sre-agent` | Future in-cluster agent runtime (placeholder) |

## 7. Deploy CrashLoopBackOff Demo

```bash
make deploy-crashloop-demo
```

This applies `k8s/demo-services/recommend-crashloop-demo.yaml`, which creates:

- **Deployment**: `recommend-service` in namespace `demo`
- **Image**: `busybox:1.36`
- **Command**: `echo '[recommend-service] crashloop demo'; exit 1`
- The container exits immediately with code 1, triggering CrashLoopBackOff after a few restart cycles.

## 8. Wait for CrashLoopBackOff

```bash
make wait-crashloop
```

This polls the pod status every 5 seconds, up to 90 seconds, looking for:
- `CrashLoopBackOff` in container status, OR
- `restartCount >= 2`

Expected output:

```
✓ Pod is in CrashLoopBackOff (restarts: 3)
```

If timeout occurs, check:

```bash
kubectl -n demo get pods -l app=recommend-service -o wide
kubectl -n demo describe pod -l app=recommend-service
```

## 9. Collect Live K8s Evidence

```bash
make collect-k8s-evidence-live
```

This runs:

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-k8s-evidence \
  --namespace demo \
  --service recommend-service \
  --output examples/evidence/k8s_crashloop_live_evidence.json \
  --reader kubectl
```

The `--reader kubectl` flag switches from fixture mode to live kubectl mode.

Expected evidence types:

| Evidence Type | Source |
|---------------|--------|
| `container_crash_loop_backoff` | Pod container status |
| `pod_restart_count_increased` | Pod restart count |
| `pod_not_ready` | Pod readiness condition |
| `deployment_metadata` | Deployment spec |

Output written to: `examples/evidence/k8s_crashloop_live_evidence.json`

## 10. Run RCA Investigation

```bash
make investigate-k8s-live
```

This runs:

```bash
java -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  investigate \
  --alert examples/alerts/k8s_crashloop.json \
  --evidence examples/evidence/k8s_crashloop_live_evidence.json \
  --output examples/reports/k8s_crashloop_live_report.md \
  --show-trace
```

Expected RCA result:

```
selectedHypothesisId = hyp_pod_crash_loop
decisionType = likely_root_cause
confidenceScore >= 0.80
```

The generated report is at: `examples/reports/k8s_crashloop_live_report.md`

## 11. Cleanup

Remove demo resources:

```bash
make clean-crashloop-demo
```

Delete the entire cluster:

```bash
make cluster-down
```

## 12. Troubleshooting

### Check cluster state

```bash
kubectl get nodes -o wide
kubectl -n demo get pods -o wide
kubectl -n demo describe pod <pod-name>
kubectl -n demo logs <pod-name> --previous
kubectl -n demo get events --sort-by=.lastTimestamp
```

### Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| Docker Desktop not running | Docker daemon not started | Start Docker Desktop |
| kind cluster missing | Cluster not created | `make cluster-up` |
| Wrong kube context | Context points to different cluster | `make kube-context` |
| Image pull delay | busybox image pulling | Wait 30s, retry |
| Pod not yet in CrashLoopBackOff | Need more restart cycles | `make wait-crashloop` again |
| No pods found by kubectl reader | Label mismatch | Check `app=recommend-service` label |
| CLI jar not found | Project not built | `make build` |
| kubectl not found | kubectl not installed | `brew install kubectl` |

### Manual evidence collection

If `make collect-k8s-evidence-live` fails, you can manually inspect:

```bash
# Check pods
kubectl -n demo get pods -l app=recommend-service -o json | jq '.items[0].status'

# Check events
kubectl -n demo get events --sort-by=.lastTimestamp -o json | jq '.items[-5:]'

# Check deployment
kubectl -n demo get deployment recommend-service -o json | jq '.status'
```

## 13. Why Live Demo Is Not Part of Unit Tests

Live K8s demo is intentionally **optional and manual**:

- **CI stability**: Unit tests must pass without Docker, kind, or kubectl
- **Determinism**: Fixture tests produce identical results every time
- **Speed**: `mvn test` runs in seconds; live demo takes minutes
- **Environment**: Not all environments have Docker Desktop / kind available

The two paths serve different purposes:

| Path | Purpose | Run by |
|------|---------|--------|
| Fixture (default) | Deterministic CI / unit tests | `mvn test` |
| Live kind (optional) | Local demo / platform credibility | `make live-k8s-demo` |
