package ai.sreagent.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Kubernetes resource reader using the official Kubernetes Java Client.
 * This is the PRODUCTION implementation — uses ServiceAccount + RBAC in-cluster,
 * or kubeconfig for local development / kind validation.
 *
 * Returns JSON strings compatible with KubernetesJsonParser.
 */
public class JavaClientKubernetesResourceReader implements KubernetesResourceReader {

    private final ApiClient apiClient;
    private final CoreV1Api coreApi;
    private final AppsV1Api appsApi;
    private final ObjectMapper objectMapper;

    public JavaClientKubernetesResourceReader(KubernetesClientConfig config) {
        this(new KubernetesApiClientFactory().create(config));
    }

    public JavaClientKubernetesResourceReader(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.coreApi = new CoreV1Api(apiClient);
        this.appsApi = new AppsV1Api(apiClient);
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
        try {
            return switch (resourceType.toLowerCase()) {
                case "pods" -> readPods(namespace, labels);
                case "deployments" -> readDeployments(namespace, labels);
                case "services" -> readServices(namespace, labels);
                case "events" -> readEvents(namespace);
                default -> throw new KubernetesEvidenceCollectionException(
                        STR."Unsupported resource type: \{resourceType}");
            };
        } catch (ApiException e) {
            throw new KubernetesEvidenceCollectionException(
                    STR."Kubernetes API error reading \{resourceType} (HTTP \{e.getCode()}): \{e.getMessage()}", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            coreApi.listNamespace().limit(1).execute();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String readerName() {
        return "java-client";
    }

    // ─── Private implementation ───

    private String readPods(String namespace, Map<String, String> labels) throws ApiException, IOException {
        String labelSelector = toLabelSelector(labels);
        V1PodList podList = coreApi.listNamespacedPod(namespace)
            .labelSelector(labelSelector)
            .execute();
        return objectMapper.writeValueAsString(podList);
    }

    private String readDeployments(String namespace, Map<String, String> labels) throws ApiException, IOException {
        String labelSelector = toLabelSelector(labels);
        V1DeploymentList deployList = appsApi.listNamespacedDeployment(namespace)
            .labelSelector(labelSelector)
            .execute();
        return objectMapper.writeValueAsString(deployList);
    }

    private String readServices(String namespace, Map<String, String> labels) throws ApiException, IOException {
        String labelSelector = toLabelSelector(labels);
        V1ServiceList serviceList = coreApi.listNamespacedService(namespace)
            .labelSelector(labelSelector)
            .execute();
        return objectMapper.writeValueAsString(serviceList);
    }

    private String readEvents(String namespace) throws ApiException, IOException {
        CoreV1EventList eventList = coreApi.listNamespacedEvent(namespace)
            .execute();
        return objectMapper.writeValueAsString(eventList);
    }

    // ─── Label selector utility ───

    /**
     * Convert a label map to a Kubernetes label selector string.
     * Empty or null map returns null (no selector).
     * Keys are sorted for deterministic output.
     */
    static String toLabelSelector(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        // TreeMap for deterministic ordering
        Map<String, String> sorted = new TreeMap<>(labels);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
