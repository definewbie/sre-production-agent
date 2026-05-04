package ai.sreagent.llm.proposer;

import java.util.List;

/**
 * Result of LLM hypothesis proposal generation.
 * Base fields come from deterministic RCA result — LLM cannot change them.
 */
public record LlmHypothesisProposalResult(
    String incidentId,
    String baseDecisionType,
    String baseSelectedHypothesisId,
    double baseConfidenceScore,
    double baseScoreGap,
    List<UnverifiedHypothesisProposal> proposals,
    boolean advisoryOnly,
    String modelProvider
) {}
