package ai.sreagent.probe;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.evidence.NormalizedEvidence;

import java.util.List;

/**
 * Bundle of raw and normalized evidence collected by probe execution.
 */
public record ProbeEvidenceBundle(
    List<Evidence> evidence,
    List<NormalizedEvidence> normalizedEvidence
) {
    public static ProbeEvidenceBundle empty() {
        return new ProbeEvidenceBundle(List.of(), List.of());
    }
}
