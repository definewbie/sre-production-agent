package ai.sreagent.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LLM-enhanced RCA report.
 *
 * Key invariant: base* fields always come from the deterministic InvestigationResult.
 * They are never parsed from or influenced by LLM output.
 * LLM narrative fields are advisory only.
 */
public record LlmEnhancedReport(
        @JsonProperty("incident_id") String incidentId,
        @JsonProperty("base_decision_type") String baseDecisionType,
        @JsonProperty("base_selected_hypothesis_id") String baseSelectedHypothesisId,
        @JsonProperty("base_confidence_score") double baseConfidenceScore,
        @JsonProperty("base_score_gap") double baseScoreGap,
        @JsonProperty("executive_summary") String executiveSummary,
        @JsonProperty("reasoning_narrative") String reasoningNarrative,
        @JsonProperty("uncertainty_explanation") String uncertaintyExplanation,
        @JsonProperty("next_steps_explanation") String nextStepsExplanation,
        String limitations,
        @JsonProperty("unverified_proposals") List<String> unverifiedProposals,
        @JsonProperty("evidence_scope_note") String evidenceScopeNote,
        @JsonProperty("model_provider") String modelProvider,
        @JsonProperty("advisory_only") boolean advisoryOnly
) {}
