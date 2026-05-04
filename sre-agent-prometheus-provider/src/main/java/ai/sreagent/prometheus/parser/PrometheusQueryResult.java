package ai.sreagent.prometheus.parser;

import java.util.List;

/**
 * Parsed result from a Prometheus query response.
 */
public record PrometheusQueryResult(
    String resultType,
    List<PrometheusSample> samples
) {

    public boolean isEmpty() {
        return samples == null || samples.isEmpty();
    }
}
