package ai.sreagent.trace;

import ai.sreagent.core.domain.Evidence;

import java.util.List;
import java.util.Map;

/**
 * Result of trace evidence collection.
 */
public record TraceEvidenceResult(
    String incidentId,
    List<Evidence> evidence,
    Map<String, Object> rawSummary
) {

    public TraceEvidenceResult {
        if (evidence == null) evidence = List.of();
        if (rawSummary == null) rawSummary = Map.of();
    }

    public int evidenceCount() {
        return evidence.size();
    }
}
