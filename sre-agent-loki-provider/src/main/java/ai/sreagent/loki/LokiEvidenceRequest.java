package ai.sreagent.loki;

import ai.sreagent.loki.query.LokiQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request to collect Loki log evidence for an incident.
 */
public record LokiEvidenceRequest(
    String incidentId,
    String service,
    String namespace,
    Instant startTime,
    Instant endTime,
    Duration lookback,
    List<LokiQueryType> queryTypes,
    Map<String, String> labels
) {

    public LokiEvidenceRequest {
        if (namespace == null) namespace = "default";
        if (lookback == null) lookback = Duration.ofMinutes(30);
        if (labels == null) labels = Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String incidentId;
        private String service;
        private String namespace = "default";
        private Instant startTime;
        private Instant endTime;
        private Duration lookback = Duration.ofMinutes(30);
        private List<LokiQueryType> queryTypes = List.of();
        private Map<String, String> labels = Map.of();

        public Builder incidentId(String incidentId) { this.incidentId = incidentId; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder lookback(Duration lookback) { this.lookback = lookback; return this; }
        public Builder queryTypes(List<LokiQueryType> queryTypes) { this.queryTypes = queryTypes; return this; }
        public Builder labels(Map<String, String> labels) { this.labels = labels; return this; }

        public LokiEvidenceRequest build() {
            return new LokiEvidenceRequest(incidentId, service, namespace,
                    startTime, endTime, lookback, queryTypes, labels);
        }
    }
}
