package ai.sreagent.prometheus;

import ai.sreagent.core.domain.Evidence;

import java.util.List;
import java.util.Map;

/**
 * Result of Prometheus evidence collection.
 */
public record PrometheusEvidenceResult(
    String incidentId,
    List<Evidence> evidence,
    Map<String, Object> rawSummary
) {

    public PrometheusEvidenceResult {
        if (evidence == null) evidence = List.of();
        if (rawSummary == null) rawSummary = Map.of();
    }

    public int evidenceCount() {
        return evidence.size();
    }
}
