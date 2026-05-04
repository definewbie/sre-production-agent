package ai.sreagent.probe;

import ai.sreagent.llm.proposer.ProbeIntent;

import java.util.List;

/**
 * A plan to execute a set of probe intents.
 * canAffectDecision is always false in Step S.
 */
public record ProbeExecutionPlan(
    String incidentId,
    String proposalId,
    List<ProbeIntent> probeIntents,
    ProbeExecutionMode mode,
    boolean canAffectDecision
) {
    public ProbeExecutionPlan {
        if (canAffectDecision) {
            throw new IllegalArgumentException(
                "ProbeExecutionPlan.canAffectDecision must be false in Step S");
        }
    }
}
