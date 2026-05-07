package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.relevance.AlertRelevance;

import java.util.Map;

/**
 * Extended alert view with relevance classification and RCA eligibility.
 */
public record AlertView(
    String fingerprint,
    String alertName,
    String service,
    String namespace,
    String severity,
    String state,
    String startsAt,
    String summary,
    AlertRelevance relevance,
    boolean rcaEligible,
    String ineligibleReason,
    Map<String, String> labels,
    Map<String, String> annotations
) {
    /**
     * Create AlertView from classified alert data.
     */
    public static AlertView from(AlertmanagerAlert alert, AlertRelevance relevance,
                                  boolean rcaEligible, String ineligibleReason) {
        String summary = alert.annotations() != null
                ? alert.annotations().getOrDefault("summary", "")
                : "";
        return new AlertView(
                alert.fingerprint() != null ? alert.fingerprint() : "",
                alert.alertName(),
                alert.service(),
                alert.namespace(),
                alert.severity(),
                alert.state() != null ? alert.state() : "active",
                alert.startsAt() != null ? alert.startsAt().toString() : null,
                summary,
                relevance,
                rcaEligible,
                ineligibleReason,
                alert.labels(),
                alert.annotations()
        );
    }

    /**
     * Backward-compatible factory (treats all alerts as SERVICE_ALERT).
     * Used when classification is not yet applied.
     */
    public static AlertView from(AlertmanagerAlert alert) {
        return from(alert, AlertRelevance.SERVICE_ALERT, true, null);
    }
}
