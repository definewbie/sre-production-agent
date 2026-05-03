package ai.sreagent.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Parses raw Kubernetes API JSON responses into structured data.
 * Uses Jackson JsonNode for flexible parsing of K8s API output.
 */
public class KubernetesJsonParser {

    private final ObjectMapper mapper;

    public KubernetesJsonParser() {
        this.mapper = new ObjectMapper();
    }

    public KubernetesJsonParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parse a Pod JSON and extract key status fields.
     */
    public ParsedPod parsePod(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            // Handle both single pod and pod list
            JsonNode pod = root.has("items") ? root.get("items").get(0) : root;
            if (pod == null) return null;

            String name = pod.at("/metadata/name").asText("");
            String namespace = pod.at("/metadata/namespace").asText("");
            String phase = pod.at("/status/phase").asText("");

            JsonNode containerStatuses = pod.at("/status/containerStatuses");
            String containerName = "";
            int restartCount = 0;
            String waitingReason = "";
            String terminatedReason = "";
            int terminatedExitCode = 0;

            if (containerStatuses.isArray() && !containerStatuses.isEmpty()) {
                JsonNode cs = containerStatuses.get(0);
                containerName = cs.at("/name").asText("");
                restartCount = cs.at("/restartCount").asInt(0);
                waitingReason = cs.at("/state/waiting/reason").asText("");
                terminatedReason = cs.at("/lastState/terminated/reason").asText("");
                terminatedExitCode = cs.at("/lastState/terminated/exitCode").asInt(0);
            }

            return new ParsedPod(name, namespace, phase, containerName, restartCount,
                    waitingReason, terminatedReason, terminatedExitCode);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a Deployment JSON and extract key status fields.
     */
    public ParsedDeployment parseDeployment(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode dep = root.has("items") ? root.get("items").get(0) : root;
            if (dep == null) return null;

            String name = dep.at("/metadata/name").asText("");
            String namespace = dep.at("/metadata/namespace").asText("");
            int replicas = dep.at("/spec/replicas").asInt(0);
            int readyReplicas = dep.at("/status/readyReplicas").asInt(0);
            int availableReplicas = dep.at("/status/availableReplicas").asInt(0);
            int updatedReplicas = dep.at("/status/updatedReplicas").asInt(0);

            return new ParsedDeployment(name, namespace, replicas, readyReplicas,
                    availableReplicas, updatedReplicas);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse an EventList JSON and extract warning events.
     */
    public List<ParsedEvent> parseEvents(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.at("/items");
            List<ParsedEvent> events = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String name = item.at("/metadata/name").asText("");
                    String reason = item.at("/reason").asText("");
                    String message = item.at("/message").asText("");
                    String type = item.at("/type").asText("");
                    String involvedObjectName = item.at("/involvedObject/name").asText("");
                    String timestamp = item.at("/lastTimestamp").asText("");
                    int count = item.at("/count").asInt(1);

                    events.add(new ParsedEvent(name, reason, message, type,
                            involvedObjectName, timestamp, count));
                }
            }
            return events;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Detect fault modes from a parsed pod.
     */
    public List<KubernetesFaultMode> detectFaultModes(ParsedPod pod) {
        List<KubernetesFaultMode> faults = new ArrayList<>();
        if (pod == null) return faults;

        if ("OOMKilled".equals(pod.terminatedReason()) || pod.terminatedExitCode() == 137) {
            faults.add(KubernetesFaultMode.POD_OOM_KILLED);
        }
        if ("CrashLoopBackOff".equals(pod.waitingReason())) {
            faults.add(KubernetesFaultMode.CRASH_LOOP_BACK_OFF);
        }
        if (pod.restartCount() > 3) {
            faults.add(KubernetesFaultMode.RESTART_COUNT_INCREASED);
        }
        if ("Pending".equals(pod.phase())) {
            faults.add(KubernetesFaultMode.PENDING_SCHEDULING);
        }
        if ("ImagePullBackOff".equals(pod.waitingReason()) || "ErrImagePull".equals(pod.waitingReason())) {
            faults.add(KubernetesFaultMode.IMAGE_PULL_BACK_OFF);
        }

        return faults;
    }

    // --- Parsed data records ---

    public record ParsedPod(
        String name, String namespace, String phase,
        String containerName, int restartCount,
        String waitingReason, String terminatedReason, int terminatedExitCode
    ) {}

    public record ParsedDeployment(
        String name, String namespace,
        int replicas, int readyReplicas, int availableReplicas, int updatedReplicas
    ) {}

    public record ParsedEvent(
        String name, String reason, String message, String type,
        String involvedObjectName, String lastTimestamp, int count
    ) {}
}
