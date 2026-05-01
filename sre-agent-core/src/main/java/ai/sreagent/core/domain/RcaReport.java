package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The final RCA report produced by an investigation.
 * This is the human-readable output.
 */
public record RcaReport(
    @JsonProperty("incident_id") String incidentId,
    String title,
    String summary,
    InvestigationDecision decision,
    List<Hypothesis> hypotheses,
    List<VerificationResult> verificationResults,
    List<ConfidenceResult> confidenceResults,
    HypothesisComparison hypothesisComparison,
    List<Evidence> evidenceSummary,
    @JsonProperty("recommended_actions") List<String> recommendedActions,
    @JsonProperty("next_probes") List<String> nextProbes
) {}