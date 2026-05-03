package ai.sreagent.k8s;

import java.io.IOException;
import java.util.Map;

/**
 * Kubernetes resource reader using the official Kubernetes Java Client.
 * This is the PRODUCTION implementation — uses ServiceAccount + RBAC.
 *
 * Current status: skeleton. Full implementation requires:
 * - io.kubernetes:client-java dependency
 * - ServiceAccount with appropriate RBAC permissions
 * - In-cluster or kubeconfig authentication
 *
 * For now, throws UnsupportedOperationException to fail fast if used.
 */
public class JavaClientKubernetesResourceReader implements KubernetesResourceReader {

    public JavaClientKubernetesResourceReader() {
        // Future: initialize KubernetesClient with in-cluster or kubeconfig
    }

    @Override
    public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
        throw new UnsupportedOperationException(
            "JavaClientKubernetesResourceReader is not yet implemented. " +
            "Use KubectlKubernetesResourceReader for local dev or FixtureKubernetesResourceReader for testing.");
    }

    @Override
    public boolean isAvailable() {
        // Future: check if KubernetesClient can connect to cluster
        return false;
    }

    @Override
    public String readerName() {
        return "java-client";
    }
}
