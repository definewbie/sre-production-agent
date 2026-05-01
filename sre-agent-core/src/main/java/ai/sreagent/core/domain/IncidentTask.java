package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Represents an incident alert that triggers an RCA investigation.
 */
public record IncidentTask(
    String id,
    @JsonProperty("alert_name") String alertName,
    String service,
    String namespace,
    String severity,
    @JsonProperty("started_at") Instant startedAt,
    Map<String, String> labels,
    Map<String, String> annotations
) {}
