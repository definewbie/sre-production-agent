package ai.sreagent.server.incident;

import ai.sreagent.core.workflow.InvestigationResult;

import java.util.Map;

/**
 * View of an incident + RCA result.
 */
public record IncidentRcaResultView(
    String incidentId,
    String status,
    String triggerSource,
    String alertName,
    String service,
    String namespace,
    String severity,
    String startedAt,
    String decisionType,
    String selectedHypothesisId,
    double confidenceScore,
    double scoreGap,
    Map<String, Double> scores,
    String reportUrl,
    long durationMs,
    String errorMessage
) {
    public enum IncidentStatus {
        RUNNING, COMPLETED, FAILED
    }

    public static IncidentRcaResultView running(String incidentId, String alertName,
                                                  String service, String severity) {
        return new IncidentRcaResultView(
                incidentId, IncidentStatus.RUNNING.name(), "alertmanager",
                alertName, service, null, severity, null,
                null, null, 0, 0, null, null, 0, null
        );
    }

    public static IncidentRcaResultView completed(InvestigationResult r, long durationMs) {
        var incident = r.incident();
        Map<String, Double> scores = null;
        if (r.confidenceResults() != null) {
            scores = new java.util.LinkedHashMap<>();
            for (var cr : r.confidenceResults()) {
                scores.put(cr.hypothesisId(), cr.score());
            }
        }

        return new IncidentRcaResultView(
                r.incidentId(), IncidentStatus.COMPLETED.name(), "alertmanager",
                incident != null ? incident.alertName() : null,
                incident != null ? incident.service() : null,
                incident != null ? incident.namespace() : null,
                incident != null ? incident.severity() : null,
                incident != null && incident.startedAt() != null ? incident.startedAt().toString() : null,
                r.decision() != null ? r.decision().decisionType() : null,
                r.decision() != null ? r.decision().selectedHypothesisId() : null,
                r.decision() != null ? r.decision().confidenceScore() : 0,
                r.comparison() != null ? r.comparison().scoreGap() : 0,
                scores,
                "/api/incidents/" + r.incidentId() + "/report",
                durationMs, null
        );
    }

    public static IncidentRcaResultView failed(String incidentId, String alertName,
                                                 String service, String errorMessage) {
        return new IncidentRcaResultView(
                incidentId, IncidentStatus.FAILED.name(), "alertmanager",
                alertName, service, null, null, null,
                null, null, 0, 0, null, null, 0, errorMessage
        );
    }
}
