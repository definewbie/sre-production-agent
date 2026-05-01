package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Confidence score for a single hypothesis after verification.
 * The score is computed deterministically from supporting/counter/missing evidence.
 */
public record ConfidenceResult(
    @JsonProperty("hypothesis_id") String hypothesisId,
    double score,
    String level,
    @JsonProperty("supporting_factors") List<String> supportingFactors,
    @JsonProperty("counter_factors") List<String> counterFactors,
    @JsonProperty("missing_factors") List<String> missingFactors,
    List<String> contradictions,
    String decision,
    @JsonProperty("calibration_notes") String calibrationNotes
) {}