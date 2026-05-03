# Local K8s Setup for SRE Production Agent
## Goal
Set up a local Kubernetes environment for future real-ish evidence providers.
This step only creates:
- kind cluster
- namespaces
- smoke-test service

It does not install Prometheus, Loki, Grafana, or real evidence providers yet.

## Prerequisites
- Docker Desktop
- Homebrew
- kubectl
- kind
- helm

## Recommended Docker Desktop Resources
For MacBook Pro M3 Max:
- CPU: 6-8 cores
- Memory: 12-16 GB
- Disk: 80-120 GB

For basic Step H smoke test:
- CPU: 4 cores
- Memory: 8 GB
- Disk: 40 GB

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

## Create Cluster
```bash
make cluster-up
make kube-context
make namespaces
make cluster-status
```

## Deploy Smoke Service
```bash
make deploy-smoke
make smoke-test
```

## Useful Commands
```bash
kubectl get nodes -o wide
kubectl get pods -A
kubectl -n demo get events --sort-by=.lastTimestamp
kubectl -n demo describe pod <pod-name>
kubectl -n demo logs <pod-name>
```

## Cleanup
```bash
make clean-smoke
make cluster-down
```

## Next Step

Step I will add real-ish evidence providers for:
* Kubernetes pod status
* Kubernetes events
* deployment metadata
* service topology
* optionally Prometheus / Loki later