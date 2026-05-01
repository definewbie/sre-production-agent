package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A candidate root cause hypothesis generated from a diagnostic pattern.
 * Hypotheses are created before verification — they are not conclusions.
 */
public record Hypothesis(
    String id,
    @JsonProperty("incident_id") String incidentId,
    @JsonProperty("pattern_id") String patternId,
    String title,
    @JsonProperty("root_cause_type") String rootCauseType,
    @JsonProperty("affected_service") String affectedService,
    @JsonProperty("candidate_cause") String candidateCause
) {}