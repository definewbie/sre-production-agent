package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Comparison between the leading hypothesis and competing hypotheses.
 * A near-tie (scoreGap < threshold) produces a competing_hypotheses decision.
 */
public record HypothesisComparison(
    @JsonProperty("incident_id") String incidentId,
    @JsonProperty("leading_hypothesis_id") String leadingHypothesisId,
    @JsonProperty("competing_hypothesis_ids") List<String> competingHypothesisIds,
    @JsonProperty("score_gap") double scoreGap,
    @JsonProperty("decisive_evidence_ids") List<String> decisiveEvidenceIds,
    @JsonProperty("comparison_summary") String comparisonSummary,
    boolean nearTie
) {}