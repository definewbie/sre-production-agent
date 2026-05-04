package ai.sreagent.llm.proposer;

import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;

import java.util.List;

/**
 * Interface for LLM-based hypothesis proposal generation.
 * LLM proposes. Verification disposes.
 */
public interface LlmHypothesisProposer {
    /**
     * Generate hypothesis proposals based on investigation result and normalized evidence.
     * Returns unverified proposals — never final RCA decisions.
     */
    LlmHypothesisProposalResult propose(
        InvestigationResult result,
        List<NormalizedEvidence> normalizedEvidence
    );

    /**
     * Name of this proposer implementation.
     */
    String proposerName();
}
