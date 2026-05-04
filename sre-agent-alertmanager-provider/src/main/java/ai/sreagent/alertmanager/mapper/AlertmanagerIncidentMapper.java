package ai.sreagent.alertmanager.mapper;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.core.domain.IncidentTask;

import java.time.Instant;
import java.util.Map;

/**
 * Maps Alertmanager alerts to IncidentTask objects.
 * Extracts alertname, service, namespace, severity, labels, annotations
 * and creates deterministic incident IDs.
 */
public class AlertmanagerIncidentMapper {

    /**
     * Map a single Alertmanager alert to an IncidentTask.
     *
     * @param alert parsed alert
     * @return IncidentTask
     */
    public IncidentTask map(AlertmanagerAlert alert) {
        if (alert == null) {
            return buildUnknownIncident();
        }

        String incidentId = buildIncidentId(alert);
        String alertName = alert.alertName();
        String service = alert.service();
        String namespace = alert.namespace();
        String severity = alert.severity();
        Instant startedAt = alert.startsAt() != null ? alert.startsAt() : Instant.now();
        Map<String, String> labels = alert.labels();
        Map<String, String> annotations = alert.annotations();

        return new IncidentTask(incidentId, alertName, service, namespace,
                severity, startedAt, labels, annotations);
    }

    /**
     * Build a deterministic incident ID from alert metadata.
     * Format: inc_alertmanager_{fingerprint} or inc_alertmanager_{alertname}_{service}_{epoch}
     */
    private String buildIncidentId(AlertmanagerAlert alert) {
        if (alert.fingerprint() != null && !alert.fingerprint().isBlank()) {
            return "inc_alertmanager_" + alert.fingerprint();
        }

        // Fallback deterministic ID
        String alertName = alert.alertName().replaceAll("[^a-zA-Z0-9_-]", "_");
        String service = alert.service().replaceAll("[^a-zA-Z0-9_-]", "_");
        long epoch = alert.startsAt() != null ? alert.startsAt().getEpochSecond() : 0;
        return "inc_alertmanager_" + alertName + "_" + service + "_" + epoch;
    }

    private IncidentTask buildUnknownIncident() {
        return new IncidentTask(
                "inc_alertmanager_unknown",
                "UnknownAlert",
                "unknown-service",
                "default",
                "warning",
                Instant.now(),
                Map.of(),
                Map.of()
        );
    }
}
