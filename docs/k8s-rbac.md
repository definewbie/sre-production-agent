# Kubernetes RBAC Security — SRE Agent

## Overview

The SRE Agent requires **read-only access** to Kubernetes resources for evidence collection during incident investigation. It observes pods, deployments, services, events, and namespaces — it never writes, deletes, or modifies any cluster state.

This document describes the RBAC model and security considerations for deploying the agent.

---

## Three Reader Paths and Their Access Models

| Reader | Class | Cluster Access | Use Case |
|--------|-------|---------------|----------|
| **Fixture** | `FixtureKubernetesResourceReader` | None | Unit tests, CI pipelines, deterministic scenarios |
| **Kubectl** | `KubectlKubernetesResourceReader` | User's kubeconfig via `kubectl` CLI | Local demo with kind/minikube — **NOT for production** |
| **Java Client** | `JavaClientKubernetesResourceReader` | ServiceAccount + RBAC (in-cluster) or kubeconfig (external) | **Production path** — in-cluster or authenticated external access |

### Fixture

- Reads bundled JSON fixtures from the classpath (`/fixtures/*.json`).
- **Zero cluster access.** Always available, fully deterministic.
- Default reader mode (`--reader fixture`).

### Kubectl

- Shells out to `kubectl` using the user's current kubeconfig context.
- Inherits whatever permissions the local user has — could be cluster-admin.
- **Not suitable for production:** subprocess-based, no credential scoping, no auditability of the agent's own identity.
- Use only for local demo and development (`--reader kubectl`).

### Java Client (Production)

- Uses the official Kubernetes Java Client (`io.kubernetes:client-java`).
- Two configuration modes:
  - **Kubeconfig** (`--client-mode kubeconfig`): reads `~/.kube/config` or `--kubeconfig <path>`. For local dev / external debugging.
  - **In-cluster** (`--client-mode in-cluster`): uses mounted ServiceAccount token. For production deployment inside a Kubernetes Pod.
- RBAC is enforced by the cluster based on the ServiceAccount identity.

---

## ServiceAccount and RBAC

The canonical RBAC manifest is at [`k8s/rbac/sre-agent-reader.yaml`](../k8s/rbac/sre-agent-reader.yaml).

### What It Creates

| Resource | Name | Namespace | Purpose |
|----------|------|-----------|---------|
| ServiceAccount | `sre-agent` | `sre-agent` | Identity for in-cluster agent pods |
| Role | `sre-agent-reader` | `demo` (target namespace) | Read-only permissions for evidence collection |
| RoleBinding | `sre-agent-reader-binding` | `demo` | Binds ServiceAccount → Role |

### Architecture

```
┌─────────────────────────────────┐
│  sre-agent namespace            │
│  ┌───────────────────────┐      │
│  │  Pod (sre-agent)      │      │
│  │  ServiceAccount token │      │
│  └───────────┬───────────┘      │
└──────────────┼──────────────────┘
               │ API calls (read-only)
┌──────────────┼──────────────────┐
│  demo namespace (observed)      │
│  ┌───────────────────────┐      │
│  │ Role: sre-agent-reader│      │
│  │ (get, list, watch)    │      │
│  └───────────────────────┘      │
│  ┌───────────────────────┐      │
│  │ RoleBinding           │      │
│  │ SA: sre-agent         │      │
│  └───────────────────────┘      │
└─────────────────────────────────┘
```

### Applying the Manifest

```bash
kubectl apply -f k8s/rbac/sre-agent-reader.yaml
```

To observe additional namespaces, create the same Role + RoleBinding in each target namespace, or use the ClusterRole defined in `k8s/rbac-sre-agent.yaml` (bind with caution).

---

## Required Permissions

| API Group | Resources | Verbs | Why |
|-----------|-----------|-------|-----|
| `""` (core) | `pods`, `pods/status` | `get`, `list`, `watch` | Pod health, restart counts, container statuses |
| `""` (core) | `services` | `get`, `list`, `watch` | Service discovery and endpoint correlation |
| `""` (core) | `events` | `get`, `list`, `watch` | Cluster-level event context for incidents |
| `""` (core) | `namespaces` | `get`, `list` | Namespace discovery |
| `apps` | `deployments`, `deployments/status` | `get`, `list`, `watch` | Deployment replica status, rollout state |
| `apps` | `replicasets` | `get`, `list`, `watch` | ReplicaSet health for deployment correlation |

**No write verbs.** No `create`, `update`, `patch`, or `delete`.

---

## Security Considerations

### Read-Only by Design

- All RBAC rules grant only `get`, `list`, and `watch` verbs.
- The agent **never** writes, deletes, or modifies Kubernetes resources.
- The `KubernetesResourceReader` interface has no mutation methods.

### Namespace-Scoped

- The Role and RoleBinding are scoped to a specific target namespace (e.g., `demo`).
- The agent cannot read resources in other namespaces unless explicitly granted.
- For cross-namespace observation, use a ClusterRole + per-namespace RoleBindings (see `k8s/rbac-sre-agent.yaml`).

### No Cluster-Admin

- The ServiceAccount `sre-agent` has **no cluster-admin** or wide-scoped privileges.
- Permissions are the minimum required for evidence collection.
- The agent identity is isolated to the `sre-agent` namespace.

### No Secrets Access

- The RBAC manifest does **not** grant access to `secrets` or `configmaps`.
- No sensitive data is read or stored by the agent.

### In-Cluster Mode Requirements

When running as an in-cluster pod with `--client-mode in-cluster`:

1. **`KUBERNETES_SERVICE_HOST`** environment variable must be present (injected automatically by Kubernetes).
2. The pod must have a ServiceAccount with the appropriate RoleBinding.
3. The ServiceAccount token is mounted at `/var/run/secrets/kubernetes.io/serviceaccount/`.

If `KUBERNETES_SERVICE_HOST` is not set, the agent will fail with a clear error message:

```
In-cluster Kubernetes config is not available.
Ensure the agent is running inside a Kubernetes pod with a ServiceAccount.
```

---

## Production Deployment Checklist

- [ ] **ServiceAccount created** in the agent's namespace (`sre-agent`)
- [ ] **Role created** in each target namespace with read-only permissions (`get`, `list`, `watch`)
- [ ] **RoleBinding created** linking ServiceAccount → Role in each target namespace
- [ ] **Pod spec** references the ServiceAccount: `serviceAccountName: sre-agent`
- [ ] **No cluster-admin** or wildcard (`*`) permissions granted
- [ ] **No write verbs** (`create`, `update`, `patch`, `delete`) in any Role
- [ ] **No secrets/configmaps access** in RBAC rules
- [ ] **Reader mode** set to `java-client` (not `fixture` or `kubectl`)
- [ ] **Client mode** set to `in-cluster` for in-pod deployment
- [ ] **Network policy** allows egress from agent namespace to Kubernetes API server (port 443)
- [ ] **RBAC manifest** applied and verified: `kubectl auth can-i list pods -n demo --as=system:serviceaccount:sre-agent:sre-agent`
