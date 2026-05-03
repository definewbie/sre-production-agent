package ai.sreagent.k8s;

/**
 * Known Kubernetes fault modes detected from resource analysis.
 * Each fault mode maps to specific K8s resource signals.
 */
public enum KubernetesFaultMode {
    POD_OOM_KILLED("pod_oom_killed", "Container terminated due to OOM", 0.9),
    CRASH_LOOP_BACK_OFF("crash_loop_back_off", "Container repeatedly crashing and restarting", 0.85),
    POD_NOT_READY("pod_not_ready", "Pod not passing readiness probes", 0.7),
    RESTART_COUNT_INCREASED("restart_count_increased", "Container restart count increasing", 0.6),
    IMAGE_PULL_BACK_OFF("image_pull_back_off", "Container image cannot be pulled", 0.8),
    PENDING_SCHEDULING("pending_scheduling", "Pod cannot be scheduled to a node", 0.65);

    private final String id;
    private final String description;
    private final double defaultStrength;

    KubernetesFaultMode(String id, String description, double defaultStrength) {
        this.id = id;
        this.description = description;
        this.defaultStrength = defaultStrength;
    }

    public String id() { return id; }
    public String description() { return description; }
    public double defaultStrength() { return defaultStrength; }
}
