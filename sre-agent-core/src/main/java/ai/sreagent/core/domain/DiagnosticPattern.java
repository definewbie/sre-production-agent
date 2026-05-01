package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A diagnostic pattern that describes a known failure mode
 * and how to match evidence against it.
 */
public record DiagnosticPattern(
    String id,
    String description,
    List<String> evidenceRequirements,
    @JsonProperty("supporting_evidence_types") List<String> supportingEvidenceTypes,
    @JsonProperty("counter_evidence_types") List<String> counterEvidenceTypes,
    @JsonProperty("confidence_weights") Map<String, Double> confidenceWeights,
    @JsonProperty("base_score") double baseScore
) {}