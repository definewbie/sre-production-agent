package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * A single entry in the event trace — the audit log of an investigation.
 * Every step of the RCA workflow must be recorded here.
 */
public record EventTraceEntry(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("incident_id") String incidentId,
    @JsonProperty("event_type") String eventType,
    Instant timestamp,
    Map<String, Object> payload
) {}