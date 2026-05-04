package ai.sreagent.alertmanager;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;

import java.util.List;
import java.util.Map;

/**
 * Result of Alertmanager alert/evidence collection.
 */
public record AlertmanagerResult(
    List<IncidentTask> incidents,
    List<Evidence> evidence,
    Map<String, Object> rawSummary
) {
    public AlertmanagerResult {
        if (incidents == null) incidents = List.of();
        if (evidence == null) evidence = List.of();
        if (rawSummary == null) rawSummary = Map.of();
    }

    public int incidentCount() {
        return incidents.size();
    }

    public int evidenceCount() {
        return evidence.size();
    }
}
