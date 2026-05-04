package ai.sreagent.llm.proposer;

/**
 * Status of an LLM-generated hypothesis proposal.
 * Step R only supports UNVERIFIED_PROPOSAL and REJECTED_BY_GUARDRAIL.
 */
public enum ProposalStatus {
    UNVERIFIED_PROPOSAL,
    REJECTED_BY_GUARDRAIL
}
