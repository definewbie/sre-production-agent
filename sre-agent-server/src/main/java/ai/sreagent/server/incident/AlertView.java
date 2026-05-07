package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;

/**
 * Compact view of a firing alert for the UI.
 */
public record AlertView(
    String fingerprint,
    String alertName,
    String service,
    String namespace,
    String severity,
    String state,
    String startsAt,
    String summary
) {
    public static AlertView from(AlertmanagerAlert alert) {
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
                summary
        );
    }
}
