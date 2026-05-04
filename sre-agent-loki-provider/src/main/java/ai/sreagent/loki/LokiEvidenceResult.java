package ai.sreagent.loki;

import ai.sreagent.core.domain.Evidence;

import java.util.List;
import java.util.Map;

/**
 * Result of Loki log evidence collection.
 */
public record LokiEvidenceResult(
    String incidentId,
    List<Evidence> evidence,
    Map<String, Object> rawSummary
) {

    public LokiEvidenceResult {
        if (evidence == null) evidence = List.of();
        if (rawSummary == null) rawSummary = Map.of();
    }

    public int evidenceCount() {
        return evidence.size();
    }
}
