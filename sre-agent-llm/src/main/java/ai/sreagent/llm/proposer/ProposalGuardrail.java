package ai.sreagent.llm.proposer;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates LLM hypothesis proposals against safety guardrails.
 * Ensures proposals cannot bypass deterministic RCA.
 *
 * Rules:
 * 1. canAffectDecision must be false
 * 2. priorConfidence must be between 0.0 and 0.5
 * 3. Proposal must include verification plan or probe intents
 * 4. Status must be a valid ProposalStatus
 * 5. Proposal must not claim final RCA
 */
public class ProposalGuardrail {

    /**
     * Validates and potentially corrects a proposal.
     * If the proposal violates critical guardrails, status is set to REJECTED_BY_GUARDRAIL.
     * If violations are correctable, the proposal is normalized.
     */
    public UnverifiedHypothesisProposal validate(UnverifiedHypothesisProposal proposal) {
        if (proposal == null) return null;

        boolean canAffectDecision = proposal.canAffectDecision();
        double priorConfidence = proposal.priorConfidence();
        ProposalStatus status = proposal.status();

        // Rule 1: canAffectDecision must be false
        if (canAffectDecision) {
            canAffectDecision = false;
        }

        // Rule 2: priorConfidence must be 0.0-0.5
        if (priorConfidence > 0.5 || priorConfidence < 0.0) {
            priorConfidence = Math.min(0.5, Math.max(0.0, priorConfidence));
        }

        // Rule 3: Must have verification plan or probe intents
        boolean hasPlan = proposal.verificationPlan() != null
            && (hasContent(proposal.verificationPlan().requiredEvidence())
                || hasContent(proposal.verificationPlan().probeIntents()));

        // Rule 4: Must not claim final RCA in title or reasoning
        String titleLower = proposal.title() != null ? proposal.title().toLowerCase() : "";
        String reasoningLower = proposal.reasoning() != null ? proposal.reasoning().toLowerCase() : "";
        boolean claimsFinalRca = titleLower.contains("root cause confirmed")
            || reasoningLower.contains("root cause confirmed");

        // Critical violations → reject
        if (proposal.canAffectDecision() || !hasPlan || claimsFinalRca) {
            status = ProposalStatus.REJECTED_BY_GUARDRAIL;
        }

        return new UnverifiedHypothesisProposal(
            proposal.proposalId(),
            proposal.title(),
            proposal.rootCauseType(),
            proposal.affectedService(),
            proposal.candidateCause(),
            proposal.reasoning(),
            proposal.supportingSignals(),
            proposal.verificationPlan(),
            priorConfidence,
            status,
            canAffectDecision
        );
    }

    /**
     * Returns true if the proposal passes all guardrails.
     */
    public boolean isValid(UnverifiedHypothesisProposal proposal) {
        if (proposal == null) return false;
        UnverifiedHypothesisProposal validated = validate(proposal);
        return validated.status() == ProposalStatus.UNVERIFIED_PROPOSAL;
    }

    private static <T> boolean hasContent(List<T> list) {
        return list != null && !list.isEmpty();
    }
}
