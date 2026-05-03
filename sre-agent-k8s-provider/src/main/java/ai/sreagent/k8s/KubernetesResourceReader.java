package ai.sreagent.k8s;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction for reading Kubernetes cluster resources.
 * Implementations may use kubectl CLI, Java client, or fixture files.
 */
public interface KubernetesResourceReader {

    /**
     * Read a Kubernetes resource as raw JSON string.
     *
     * @param resourceType e.g. "pods", "deployments", "services", "events"
     * @param name         resource name (empty for list operations)
     * @param namespace    Kubernetes namespace
     * @param labels       optional label selector map
     * @return raw JSON string from the cluster
     */
    String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException;

    /**
     * Read a list of Kubernetes resources as raw JSON string.
     */
    default String listResources(String resourceType, String namespace, Map<String, String> labels) throws IOException {
        return readResource(resourceType, "", namespace, labels);
    }

    /**
     * Check if this reader is available and functional.
     */
    boolean isAvailable();

    /**
     * Human-readable name of this reader implementation.
     */
    String readerName();
}
