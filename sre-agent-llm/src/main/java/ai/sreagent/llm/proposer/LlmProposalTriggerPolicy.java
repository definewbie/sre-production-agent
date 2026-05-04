package ai.sreagent.llm.proposer;

import ai.sreagent.core.workflow.InvestigationResult;

/**
 * Policy for deciding whether LLM hypothesis proposals should be generated.
 * The proposer should be triggered when deterministic RCA is inconclusive.
 */
public class LlmProposalTriggerPolicy {

    /**
     * Returns true if the LLM proposer should generate proposals.
     */
    public boolean shouldPropose(InvestigationResult result) {
        if (result == null || result.decision() == null) return false;

        String decisionType = result.decision().decisionType();
        double confidence = result.decision().confidenceScore();
        double scoreGap = result.comparison() != null ? result.comparison().scoreGap() : 1.0;

        // Trigger conditions
        if ("competing_hypotheses".equals(decisionType)) return true;
        if ("uncertain_requires_more_evidence".equals(decisionType)) return true;
        if ("insufficient_evidence".equals(decisionType)) return true;
        if (confidence < 0.60) return true;
        if (scoreGap < 0.10) return true;

        // Default: do not trigger for clear decisions
        return false;
    }
}
