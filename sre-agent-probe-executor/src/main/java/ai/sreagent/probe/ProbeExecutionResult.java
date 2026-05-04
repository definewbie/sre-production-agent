package ai.sreagent.probe;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.evidence.NormalizedEvidence;

import java.util.List;

/**
 * Result of executing a probe plan.
 * canAffectDecision is always false in Step S.
 */
public record ProbeExecutionResult(
    String incidentId,
    String proposalId,
    ProbeExecutionStatus status,
    List<Evidence> evidence,
    List<NormalizedEvidence> normalizedEvidence,
    List<String> executedProbeIds,
    List<String> skippedProbeIds,
    List<String> errors,
    boolean canAffectDecision
) {
    public ProbeExecutionResult {
        if (canAffectDecision) {
            throw new IllegalArgumentException(
                "ProbeExecutionResult.canAffectDecision must be false in Step S");
        }
    }
}
