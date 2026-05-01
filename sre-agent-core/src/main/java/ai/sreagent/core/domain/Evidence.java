package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * A single piece of evidence collected during an investigation.
 * Evidence is the foundation of all RCA conclusions.
 */
public record Evidence(
    String id,
    @JsonProperty("incident_id") String incidentId,
    String source,
    @JsonProperty("evidence_type") String evidenceType,
    String service,
    Instant timestamp,
    String content,
    Map<String, Object> attributes,
    double strength
) {}
