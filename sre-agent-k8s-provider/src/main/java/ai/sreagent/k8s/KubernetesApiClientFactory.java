package ai.sreagent.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.credentials.Authentication;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory for creating Kubernetes ApiClient instances.
 * Supports kubeconfig and in-cluster configuration modes.
 */
public class KubernetesApiClientFactory {

    /**
     * Create an ApiClient from the given configuration.
     *
     * @param config client configuration
     * @return configured ApiClient
     * @throws KubernetesEvidenceCollectionException if configuration fails
     */
    public ApiClient create(KubernetesClientConfig config) {
        try {
            ApiClient client = switch (config.mode()) {
                case KUBECONFIG -> createFromKubeconfig(config);
                case IN_CLUSTER -> createFromInCluster();
            };

            client.setConnectTimeout(config.requestTimeoutMs());
            client.setReadTimeout(config.requestTimeoutMs());
            return client;
        } catch (KubernetesEvidenceCollectionException e) {
            throw e;
        } catch (Exception e) {
            throw new KubernetesEvidenceCollectionException(
                "Failed to create Kubernetes API client: " + e.getMessage(), e);
        }
    }

    private ApiClient createFromKubeconfig(KubernetesClientConfig config) throws IOException {
        String kubeconfigPath = config.kubeconfigPath();
        if (kubeconfigPath == null || kubeconfigPath.isEmpty()) {
            kubeconfigPath = defaultKubeconfigPath();
        }

        Path path = Path.of(kubeconfigPath);
        if (!Files.exists(path)) {
            throw new KubernetesEvidenceCollectionException(
                "Kubeconfig file not found: " + kubeconfigPath +
                ". Use --kubeconfig <path> or ensure ~/.kube/config exists.");
        }

        KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(path.toFile()));

        if (config.context() != null && !config.context().isEmpty()) {
            kubeConfig.setContext(config.context());
        }

        return ClientBuilder.kubeconfig(kubeConfig).build();
    }

    private ApiClient createFromInCluster() throws IOException {
        // Pre-check: in-cluster mode requires KUBERNETES_SERVICE_HOST env var
        if (System.getenv("KUBERNETES_SERVICE_HOST") == null) {
            throw new KubernetesEvidenceCollectionException(
                "In-cluster Kubernetes config is not available. " +
                "Ensure the agent is running inside a Kubernetes pod with a ServiceAccount.");
        }
        try {
            return ClientBuilder.cluster().build();
        } catch (Exception e) {
            throw new KubernetesEvidenceCollectionException(
                "Failed to create in-cluster Kubernetes client: " + e.getMessage(), e);
        }
    }

    private String defaultKubeconfigPath() {
        String home = System.getProperty("user.home");
        return home + "/.kube/config";
    }
}
