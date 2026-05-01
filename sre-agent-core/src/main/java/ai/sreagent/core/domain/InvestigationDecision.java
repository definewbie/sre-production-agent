package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The final decision from an investigation.
 * Decision types: identified_root_cause, competing_hypotheses, escalation, insufficient_evidence.
 */
public record InvestigationDecision(
    @JsonProperty("incident_id") String incidentId,
    @JsonProperty("selected_hypothesis_id") String selectedHypothesisId,
    @JsonProperty("decision_type") String decisionType,
    @JsonProperty("confidence_score") double confidenceScore,
    String rationale,
    @JsonProperty("next_probes") List<String> nextProbes,
    @JsonProperty("competing_hypotheses") List<String> competingHypotheses
) {}