package ai.sreagent.alertmanager.parser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single parsed alert from Alertmanager v2 API.
 */
public record AlertmanagerAlert(
    Map<String, String> labels,
    Map<String, String> annotations,
    Instant startsAt,
    Instant endsAt,
    String state,
    String fingerprint,
    List<String> silencedBy,
    List<String> inhibitedBy
) {
    public AlertmanagerAlert {
        if (labels == null) labels = Map.of();
        if (annotations == null) annotations = Map.of();
        if (silencedBy == null) silencedBy = List.of();
        if (inhibitedBy == null) inhibitedBy = List.of();
    }

    /**
     * Extract alert name from labels.
     */
    public String alertName() {
        return labels.getOrDefault("alertname", "UnknownAlert");
    }

    /**
     * Extract service name from common label keys.
     */
    public String service() {
        return labels.getOrDefault("service",
                labels.getOrDefault("app",
                        labels.getOrDefault("job",
                                labels.getOrDefault("pod",
                                        labels.getOrDefault("deployment", "unknown-service")))));
    }

    /**
     * Extract namespace from labels.
     */
    public String namespace() {
        return labels.getOrDefault("namespace", "default");
    }

    /**
     * Extract severity from labels.
     */
    public String severity() {
        return labels.getOrDefault("severity", "warning");
    }

    /**
     * Whether this alert is currently firing/active.
     */
    public boolean isFiring() {
        return "active".equals(state) || "firing".equals(state);
    }

    /**
     * Whether this alert has been resolved.
     */
    public boolean isResolved() {
        return "resolved".equals(state);
    }

    /**
     * Whether this alert is silenced.
     */
    public boolean isSilenced() {
        return silencedBy != null && !silencedBy.isEmpty();
    }

    /**
     * Whether this alert is inhibited.
     */
    public boolean isInhibited() {
        return inhibitedBy != null && !inhibitedBy.isEmpty();
    }

    /**
     * Whether endsAt is set (not zero/empty).
     */
    public boolean hasEndTime() {
        return endsAt != null && !endsAt.equals(Instant.parse("0001-01-01T00:00:00Z"));
    }
}
