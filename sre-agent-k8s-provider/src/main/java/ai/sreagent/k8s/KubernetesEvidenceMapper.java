package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;

import java.time.Instant;
import java.util.*;

/**
 * Maps parsed Kubernetes resource data into the core Evidence domain model.
 * Bridges the gap between K8s-specific data and the generic RCA evidence model.
 */
public class KubernetesEvidenceMapper {

    private static final String SOURCE_KUBERNETES = "kubernetes";

    /**
     * Map a parsed Pod into an Evidence object.
     */
    public Evidence mapPodToEvidence(KubernetesJsonParser.ParsedPod pod, String incidentId) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("namespace", pod.namespace());
        attrs.put("phase", pod.phase());
        attrs.put("container", pod.containerName());
        attrs.put("restartCount", pod.restartCount());
        attrs.put("waitingReason", pod.waitingReason());
        attrs.put("terminatedReason", pod.terminatedReason());
        attrs.put("terminatedExitCode", pod.terminatedExitCode());

        String content = buildPodContent(pod);

        return new Evidence(
            "ev-k8s-pod-" + UUID.randomUUID().toString().substring(0, 8),
            incidentId,
            SOURCE_KUBERNETES,
            "k8s_pod_status",
            pod.name(),
            Instant.now(),
            content,
            attrs,
            0.8
        );
    }

    /**
     * Map a parsed Deployment into an Evidence object.
     */
    public Evidence mapDeploymentToEvidence(KubernetesJsonParser.ParsedDeployment deployment, String incidentId) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("namespace", deployment.namespace());
        attrs.put("replicas", deployment.replicas());
        attrs.put("readyReplicas", deployment.readyReplicas());
        attrs.put("availableReplicas", deployment.availableReplicas());
        attrs.put("updatedReplicas", deployment.updatedReplicas());
        attrs.put("degraded", deployment.readyReplicas() < deployment.replicas());

        String content = buildDeploymentContent(deployment);

        return new Evidence(
            "ev-k8s-deploy-" + UUID.randomUUID().toString().substring(0, 8),
            incidentId,
            SOURCE_KUBERNETES,
            "k8s_deployment_status",
            deployment.name(),
            Instant.now(),
            content,
            attrs,
            0.7
        );
    }

    /**
     * Map parsed Events into Evidence objects.
     */
    public List<Evidence> mapEventsToEvidence(List<KubernetesJsonParser.ParsedEvent> events, String incidentId) {
        List<Evidence> evidenceList = new ArrayList<>();
        for (KubernetesJsonParser.ParsedEvent event : events) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("reason", event.reason());
            attrs.put("type", event.type());
            attrs.put("involvedObject", event.involvedObjectName());
            attrs.put("count", event.count());

            double strength = "Warning".equals(event.type()) ? 0.75 : 0.5;

            evidenceList.add(new Evidence(
                "ev-k8s-evt-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "k8s_event",
                event.involvedObjectName(),
                Instant.now(),
                event.reason() + ": " + event.message(),
                attrs,
                strength
            ));
        }
        return evidenceList;
    }

    private String buildPodContent(KubernetesJsonParser.ParsedPod pod) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pod ").append(pod.name()).append(" in namespace ").append(pod.namespace());
        sb.append(" is in phase ").append(pod.phase());
        if (!pod.waitingReason().isEmpty()) {
            sb.append(" (waiting: ").append(pod.waitingReason()).append(")");
        }
        if (!pod.terminatedReason().isEmpty()) {
            sb.append(" (last terminated: ").append(pod.terminatedReason());
            sb.append(" exitCode=").append(pod.terminatedExitCode()).append(")");
        }
        sb.append(" restartCount=").append(pod.restartCount());
        return sb.toString();
    }

    private String buildDeploymentContent(KubernetesJsonParser.ParsedDeployment dep) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deployment ").append(dep.name()).append(" in namespace ").append(dep.namespace());
        sb.append(": ").append(dep.readyReplicas()).append("/").append(dep.replicas()).append(" replicas ready");
        if (dep.readyReplicas() < dep.replicas()) {
            sb.append(" [DEGRADED]");
        }
        return sb.toString();
    }
}
