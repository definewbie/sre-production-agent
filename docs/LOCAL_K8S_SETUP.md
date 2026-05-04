# Local K8s Setup for SRE Production Agent

## Goal

Set up a local Kubernetes environment for the SRE Production Agent.

This creates:
- kind cluster (3 nodes)
- namespaces (demo, observability, sre-agent)
- smoke-test service (nginx)
- CrashLoopBackOff demo (recommend-service)

## Prerequisites

- Docker Desktop (running)
- Homebrew
- kubectl
- kind
- helm (optional, for future use)

## Recommended Docker Desktop Resources

For MacBook Pro M3 Max:

| Resource | Recommended | Minimum |
|----------|-------------|---------|
| CPU | 6-8 cores | 4 cores |
| Memory | 12-16 GB | 8 GB |
| Disk | 80-120 GB | 40 GB |

## Install Tools

```bash
brew install kubectl kind helm
```

## Verify

```bash
docker version
docker run hello-world
kubectl version --client
kind version
helm version
```

## Quick Start (Smoke Test)

```bash
make cluster-up
make kube-context
make namespaces
make deploy-smoke
make smoke-test
```

## Live CrashLoopBackOff Demo (Step K)

The live demo proves that the SRE Agent can collect real Kubernetes evidence and run RCA:

```bash
# 1. Build the project
make build

# 2. Start cluster (if not already running)
make cluster-up

# 3. Run the full live demo pipeline
make live-k8s-demo
```

Or step-by-step:

```bash
make cluster-status
make namespaces
make deploy-crashloop-demo
make wait-crashloop
make collect-k8s-evidence-live
make investigate-k8s-live
```

Expected RCA result:

```
selected hypothesis: hyp_pod_crash_loop
decision: likely_root_cause
confidence score >= 0.80
```

See [docs/live-k8s-demo.md](live-k8s-demo.md) for the full walkthrough.

## Useful Commands

```bash
kubectl get nodes -o wide
kubectl get pods -A
kubectl -n demo get events --sort-by=.lastTimestamp
kubectl -n demo describe pod <pod-name>
kubectl -n demo logs <pod-name>
kubectl -n demo logs <pod-name> --previous
```

## Cleanup

```bash
# Remove demo resources only
make clean-smoke
make clean-crashloop-demo

# Delete entire cluster
make cluster-down
```

## Next Steps

- Step L: Java Client in-cluster evidence collection
- Step M: Prometheus / Loki evidence providers
- Step N: CMDB / topology provider
