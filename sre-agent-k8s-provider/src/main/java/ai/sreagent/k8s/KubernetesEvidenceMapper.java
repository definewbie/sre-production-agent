package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;

import java.time.Instant;
import java.util.*;

/**
 * Maps parsed Kubernetes resource data into the core Evidence domain model.
 * Bridges the gap between K8s-specific data and the generic RCA evidence model.
 *
 * <h3>Semantic Typing</h3>
 * <p>Each mapped evidence has a specific evidence type that the VerificationEngine
 * matches against pattern supporting/counter types. Evidence types must be explicit
 * and meaningful — {@code NONE} or overly generic types (e.g. {@code k8s_event})
 * are not allowed because they cause incorrect pattern matching.</p>
 *
 * <h3>Type Categories</h3>
 * <ul>
 *   <li><b>Fault types</b> — indicate a specific problem (e.g. container_crash_loop_backoff)</li>
 *   <li><b>Health / counter types</b> — indicate normal state (e.g. pod_ready, k8s_runtime_healthy)</li>
 *   <li><b>Metadata types</b> — contextual info, weak evidence only (e.g. deployment_metadata)</li>
 *   <li><b>No-signal type</b> — no anomaly detected (e.g. k8s_no_signal)</li>
 * </ul>
 */
public class KubernetesEvidenceMapper {

    private static final String SOURCE_KUBERNETES = "kubernetes";

    /**
     * Map a parsed Pod into an Evidence object (generic, non-semantic).
     * Prefer {@link #mapPodToSemanticEvidence} for live RCA scenarios.
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
     * Map a parsed Deployment into an Evidence object (generic, non-semantic).
     * Prefer {@link #mapDeploymentToMetadataEvidence} for live RCA scenarios.
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
     * Map parsed Events into Evidence objects (generic, non-semantic).
     * Prefer {@link #mapEventsToSemanticEvidence} for live RCA scenarios.
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

    // ── Semantic mapping methods ──────────────────────────────────────────

    /**
     * Map a parsed Pod into semantic Evidence objects matching RCA pattern evidence types.
     *
     * <p>Produces fault-specific types when anomalies are detected, or health/counter
     * types when the pod is healthy. Never produces {@code NONE} or ambiguous types.</p>
     *
     * <p>Output types:</p>
     * <ul>
     *   <li>{@code container_crash_loop_backoff} — pod in CrashLoopBackOff cycle</li>
     *   <li>{@code pod_restart_count_increased} — restartCount >= 2 (not crash-loop-specific)</li>
     *   <li>{@code restart_count_observed} — restartCount == 1 (weak signal, not "increased")</li>
     *   <li>{@code pod_not_ready} — pod phase not Running or has waiting reason (not just restartCount > 0)</li>
     *   <li>{@code container_oom_killed} — container last terminated with OOMKilled</li>
     *   <li>{@code pod_ready} — healthy pod counter evidence</li>
     *   <li>{@code k8s_runtime_healthy} — no anomalies detected</li>
     *   <li>{@code k8s_no_signal} — no anomaly to report</li>
     * </ul>
     */
    public List<Evidence> mapPodToSemanticEvidence(KubernetesJsonParser.ParsedPod pod, String incidentId) {
        List<Evidence> evidenceList = new ArrayList<>();
        String podName = pod.name();
        boolean hasAnomaly = false;

        // 1. OOMKilled detection — check lastState.terminated.reason
        if ("OOMKilled".equals(pod.terminatedReason())) {
            hasAnomaly = true;
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("pod_name", podName);
            attrs.put("container", pod.containerName());
            attrs.put("terminated_reason", "OOMKilled");
            attrs.put("exit_code", pod.terminatedExitCode());

            String content = "Container in pod " + podName + " was OOMKilled (exit code "
                    + pod.terminatedExitCode() + ").";
            evidenceList.add(new Evidence(
                "ev-k8s-oom-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "container_oom_killed",
                podName,
                Instant.now(),
                content,
                attrs,
                0.95
            ));
        }

        // 2. CrashLoopBackOff detection
        //    a) Explicit waiting state with CrashLoopBackOff reason
        //    b) Terminated with Error + high restart count → infer CrashLoopBackOff cycle
        boolean isCrashLoop = "CrashLoopBackOff".equals(pod.waitingReason())
            || ("Error".equals(pod.terminatedReason()) && pod.restartCount() >= 2)
            || ("Error".equals(pod.terminatedReason()) && pod.restartCount() >= 1 && !"Running".equals(pod.phase()));

        if (isCrashLoop) {
            hasAnomaly = true;
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

        // 3. Restart count evidence
        //    restartCount >= 2 → pod_restart_count_increased (strong signal)
        //    restartCount == 1 → restart_count_observed (weak signal, no baseline comparison)
        if (pod.restartCount() >= 2) {
            hasAnomaly = true;
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
        } else if (pod.restartCount() == 1) {
            hasAnomaly = true;
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("restart_count", 1);
            attrs.put("pod_name", podName);

            String content = "Pod has restarted 1 time. Single restart observed, no baseline for comparison.";
            evidenceList.add(new Evidence(
                "ev-k8s-restart-obs-" + UUID.randomUUID().toString().substring(0, 8),
                incidentId,
                SOURCE_KUBERNETES,
                "restart_count_observed",
                podName,
                Instant.now(),
                content,
                attrs,
                0.60
            ));
        }

        // 4. Pod not ready — only when phase is not Running or there's a waiting reason
        //    restartCount alone does NOT make a pod "not ready" — latency-induced restarts
        //    can occur on otherwise healthy pods
        boolean podNotReady = !"Running".equals(pod.phase()) || !pod.waitingReason().isEmpty();
        if (podNotReady) {
            hasAnomaly = true;
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("ready", false);
            attrs.put("pod_name", podName);
            attrs.put("phase", pod.phase());
            if (!pod.waitingReason().isEmpty()) {
                attrs.put("waiting_reason", pod.waitingReason());
            }

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

        // 5. Healthy pod — counter evidence for crash_loop / oom patterns
        //    Only emitted when no anomalies were detected
        if (!hasAnomaly) {
            boolean isHealthy = "Running".equals(pod.phase())
                    && pod.waitingReason().isEmpty()
                    && pod.restartCount() == 0;

            if (isHealthy) {
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("pod_name", podName);
                attrs.put("phase", "Running");
                attrs.put("restart_count", 0);
                attrs.put("ready", true);

                String content = "Pod " + podName + " is healthy: phase=Running, restartCount=0, no waiting state.";
                evidenceList.add(new Evidence(
                    "ev-k8s-healthy-" + UUID.randomUUID().toString().substring(0, 8),
                    incidentId,
                    SOURCE_KUBERNETES,
                    "k8s_runtime_healthy",
                    podName,
                    Instant.now(),
                    content,
                    attrs,
                    0.80
                ));
            } else {
                // Pod has some state but no classified anomaly
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("pod_name", podName);
                attrs.put("phase", pod.phase());
                attrs.put("restart_count", pod.restartCount());

                String content = "Pod " + podName + " has no classified Kubernetes anomaly.";
                evidenceList.add(new Evidence(
                    "ev-k8s-nosig-" + UUID.randomUUID().toString().substring(0, 8),
                    incidentId,
                    SOURCE_KUBERNETES,
                    "k8s_no_signal",
                    podName,
                    Instant.now(),
                    content,
                    attrs,
                    0.30
                ));
            }
        }

        return evidenceList;
    }

    /**
     * Map a parsed Deployment into a deployment_metadata Evidence object.
     * Matches the evidence type expected by diagnostic patterns.
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

    /**
     * Map parsed Events into semantic Evidence objects with event-reason-specific types.
     *
     * <p>Instead of the generic {@code k8s_event} type (which can incorrectly match patterns),
     * this method assigns semantic types based on event reason:</p>
     * <ul>
     *   <li>Unhealthy / FailedScheduling → {@code pod_not_ready}</li>
     *   <li>BackOff (image pull) → {@code image_pull_backoff}</li>
     *   <li>Killing → {@code pod_terminating}</li>
     *   <li>OOMKilling → {@code container_oom_killed}</li>
     *   <li>Pulled / Started / Created → {@code k8s_no_signal} (normal lifecycle events)</li>
     *   <li>Other → {@code k8s_no_signal} (unclassified, treated as no-anomaly-signal)</li>
     * </ul>
     */
    public List<Evidence> mapEventsToSemanticEvidence(List<KubernetesJsonParser.ParsedEvent> events, String incidentId) {
        List<Evidence> evidenceList = new ArrayList<>();
        for (KubernetesJsonParser.ParsedEvent event : events) {
            String semanticType = classifyEventReason(event.reason());
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
                semanticType,
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
     * Classify a Kubernetes event reason into a semantic evidence type.
     * Returns {@code k8s_no_signal} for unclassified/normal events to prevent
     * them from incorrectly supporting crash_loop or other fault patterns.
     */
    private String classifyEventReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            return "k8s_no_signal";
        }
        return switch (reason) {
            // ── Fault signals ──
            case "Unhealthy", "FailedScheduling", "FailedMount" -> "pod_not_ready";
            case "BackOff" -> "image_pull_backoff";
            case "Killing" -> "pod_terminating";
            case "OOMKilling" -> "container_oom_killed";

            // ── Normal lifecycle events — NOT fault signals ──
            case "Pulled", "Started", "Created", "SuccessfulCreate",
                 "Scheduled", "StartedScarceResource", "TaintManagerEviction" -> "k8s_no_signal";

            // ── Unclassified — treat as no-signal to prevent false pattern matching ──
            default -> "k8s_no_signal";
        };
    }

    // ── Content builders ──────────────────────────────────────────────────

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
