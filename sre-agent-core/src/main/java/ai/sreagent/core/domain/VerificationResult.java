package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of verifying a hypothesis against collected evidence.
 * Classifies evidence into supporting, counter, missing, and contradiction.
 */
public record VerificationResult(
    @JsonProperty("hypothesis_id") String hypothesisId,
    @JsonProperty("supporting_evidence_ids") List<String> supportingEvidenceIds,
    @JsonProperty("counter_evidence_ids") List<String> counterEvidenceIds,
    @JsonProperty("missing_evidence") List<String> missingEvidence,
    List<String> contradictions,
    String explanation
) {}