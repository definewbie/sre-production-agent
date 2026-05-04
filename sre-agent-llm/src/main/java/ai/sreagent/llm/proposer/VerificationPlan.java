package ai.sreagent.llm.proposer;

import java.util.List;

/**
 * Plan for verifying an LLM-proposed hypothesis.
 * Lists required, missing, and counter evidence plus probe intents.
 */
public record VerificationPlan(
    List<String> requiredEvidence,
    List<String> missingEvidence,
    List<String> counterEvidenceToCheck,
    List<ProbeIntent> probeIntents
) {
    public static VerificationPlan empty() {
        return new VerificationPlan(List.of(), List.of(), List.of(), List.of());
    }
}
