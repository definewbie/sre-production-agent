package ai.sreagent.trace;

import ai.sreagent.trace.query.TraceQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request to collect trace evidence for an incident.
 */
public record TraceEvidenceRequest(
    String incidentId,
    String service,
    String namespace,
    String operation,
    Instant startTime,
    Instant endTime,
    Duration lookback,
    List<TraceQueryType> queryTypes,
    Map<String, String> attributes
) {

    public TraceEvidenceRequest {
        if (namespace == null) namespace = "default";
        if (lookback == null) lookback = Duration.ofMinutes(30);
        if (attributes == null) attributes = Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String incidentId;
        private String service;
        private String namespace = "default";
        private String operation;
        private Instant startTime;
        private Instant endTime;
        private Duration lookback = Duration.ofMinutes(30);
        private List<TraceQueryType> queryTypes = List.of();
        private Map<String, String> attributes = Map.of();

        public Builder incidentId(String incidentId) { this.incidentId = incidentId; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder lookback(Duration lookback) { this.lookback = lookback; return this; }
        public Builder queryTypes(List<TraceQueryType> queryTypes) { this.queryTypes = queryTypes; return this; }
        public Builder attributes(Map<String, String> attributes) { this.attributes = attributes; return this; }

        public TraceEvidenceRequest build() {
            return new TraceEvidenceRequest(incidentId, service, namespace, operation,
                    startTime, endTime, lookback, queryTypes, attributes);
        }
    }
}
