package ai.sreagent.prometheus;

import ai.sreagent.prometheus.query.PrometheusQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request to collect Prometheus evidence for an incident.
 */
public record PrometheusEvidenceRequest(
    String incidentId,
    String service,
    String namespace,
    Instant startTime,
    Instant endTime,
    Duration lookback,
    List<PrometheusQueryType> queryTypes,
    Map<String, String> labels
) {

    public PrometheusEvidenceRequest {
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
        private List<PrometheusQueryType> queryTypes = List.of();
        private Map<String, String> labels = Map.of();

        public Builder incidentId(String incidentId) { this.incidentId = incidentId; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder lookback(Duration lookback) { this.lookback = lookback; return this; }
        public Builder queryTypes(List<PrometheusQueryType> queryTypes) { this.queryTypes = queryTypes; return this; }
        public Builder labels(Map<String, String> labels) { this.labels = labels; return this; }

        public PrometheusEvidenceRequest build() {
            return new PrometheusEvidenceRequest(incidentId, service, namespace,
                    startTime, endTime, lookback, queryTypes, labels);
        }
    }
}
