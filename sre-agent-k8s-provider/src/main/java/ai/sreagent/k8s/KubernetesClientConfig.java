package ai.sreagent.k8s;

/**
 * Configuration for Kubernetes Java Client connection.
 * Supports kubeconfig (local/external) and in-cluster (ServiceAccount) modes.
 */
public record KubernetesClientConfig(
    KubernetesClientMode mode,
    String kubeconfigPath,
    String context,
    int requestTimeoutMs
) {
    public enum KubernetesClientMode {
        KUBECONFIG,
        IN_CLUSTER
    }

    /** Default config: kubeconfig mode, default path, 10s timeout. */
    public static KubernetesClientConfig defaults() {
        return new KubernetesClientConfig(
            KubernetesClientMode.KUBECONFIG,
            null,   // null = use default ~/.kube/config
            null,   // null = use current context
            10_000
        );
    }

    /** In-cluster config for production deployment inside Kubernetes. */
    public static KubernetesClientConfig inCluster() {
        return new KubernetesClientConfig(
            KubernetesClientMode.IN_CLUSTER,
            null,
            null,
            10_000
        );
    }

    /** Kubeconfig mode with explicit path. */
    public static KubernetesClientConfig kubeconfig(String path) {
        return new KubernetesClientConfig(
            KubernetesClientMode.KUBECONFIG,
            path,
            null,
            10_000
        );
    }
}
