package ai.sreagent.llm.proposer;

import java.util.List;

/**
 * An unverified hypothesis proposal from the LLM.
 * This is NOT a root cause conclusion — it is a testable hypothesis
 * that must be verified through evidence before being promoted.
 *
 * Key constraints:
 * - canAffectDecision must be false in Step R
 * - status must be UNVERIFIED_PROPOSAL unless guardrail rejects
 * - priorConfidence is NOT RCA confidence (advisory only)
 */
public record UnverifiedHypothesisProposal(
    String proposalId,
    String title,
    String rootCauseType,
    String affectedService,
    String candidateCause,
    String reasoning,
    List<String> supportingSignals,
    VerificationPlan verificationPlan,
    double priorConfidence,
    ProposalStatus status,
    boolean canAffectDecision
) {}
