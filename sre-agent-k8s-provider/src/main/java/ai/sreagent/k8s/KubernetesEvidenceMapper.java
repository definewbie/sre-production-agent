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

    /**
     * Map a parsed Pod into semantic Evidence objects matching RCA pattern evidence types.
     * Unlike mapPodToEvidence (generic k8s_pod_status), this method detects fault conditions
     * and generates specific evidence types that the pod_crash_loop pattern expects:
     *   - container_crash_loop_backoff
     *   - pod_restart_count_increased
     *   - pod_not_ready
     */
    public List<Evidence> mapPodToSemanticEvidence(KubernetesJsonParser.ParsedPod pod, String incidentId) {
        List<Evidence> evidenceList = new ArrayList<>();
        String podName = pod.name();

        // 1. CrashLoopBackOff detection
        //    a) Explicit waiting state with CrashLoopBackOff reason
        //    b) Terminated with Error + high restart count → infer CrashLoopBackOff cycle
        boolean isCrashLoop = "CrashLoopBackOff".equals(pod.waitingReason())
            || ("Error".equals(pod.terminatedReason()) && pod.restartCount() >= 2)
            || ("Error".equals(pod.terminatedReason()) && pod.restartCount() >= 1 && !"Running".equals(pod.phase()));

        if (isCrashLoop) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("pod_name", podName);
            attrs.put("container", pod.containerName());
            attrs.put("exit_code", pod.terminatedExitCode());
            attrs.put("reason", "CrashLoopBackOff");
            attrs.put("restart_count", pod.restartCount());

            String content = "Pod " + podName + " is in CrashLoopBackOff state. Container exiting with code "
                    + pod.terminatedExitCode() + ".";
            evidenceList.add(new Evidence(
                "ev-k8s-crashloop-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "container_crash_loop_backoff",
                podName,
                Instant.now(),
                content,
                attrs,
                0.95
            ));
        }

        // 2. Restart count increased
        if (pod.restartCount() >= 2) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("restart_count", pod.restartCount());
            attrs.put("pod_name", podName);

            String content = "Pod has restarted " + pod.restartCount()
                    + " times. Restart count is abnormally high.";
            evidenceList.add(new Evidence(
                "ev-k8s-restart-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "pod_restart_count_increased",
                podName,
                Instant.now(),
                content,
                attrs,
                0.90
            ));
        }

        // 3. Pod not ready
        if (!"Running".equals(pod.phase()) || !pod.waitingReason().isEmpty() || pod.restartCount() > 0) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("ready", false);
            attrs.put("pod_name", podName);

            String content = "Pod readiness probe failing. Pod " + podName + " is not ready.";
            evidenceList.add(new Evidence(
                "ev-k8s-notready-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "pod_not_ready",
                podName,
                Instant.now(),
                content,
                attrs,
                0.85
            ));
        }

        return evidenceList;
    }

    /**
     * Map a parsed Deployment into a deployment_metadata Evidence object.
     * Matches the evidence type expected by the pod_crash_loop pattern.
     */
    public Evidence mapDeploymentToMetadataEvidence(KubernetesJsonParser.ParsedDeployment deployment, String incidentId) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("deployment_name", deployment.name());
        attrs.put("replicas", deployment.replicas());
        attrs.put("updated_replicas", deployment.updatedReplicas());
        attrs.put("available_replicas", deployment.availableReplicas());
        attrs.put("ready_replicas", deployment.readyReplicas());

        String content = "Deployment " + deployment.name() + " has "
                + deployment.readyReplicas() + "/" + deployment.replicas() + " desired replicas ready.";
        if (deployment.readyReplicas() < deployment.replicas()) {
            content = content + " [DEGRADED]";
        }

        return new Evidence(
            "ev-k8s-deploy-meta-" + UUID.randomUUID().toString().substring(0, 8),
            incidentId,
            SOURCE_KUBERNETES,
            "deployment_metadata",
            deployment.name(),
            Instant.now(),
            content,
            attrs,
            0.70
        );
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
