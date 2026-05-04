package ai.sreagent.prometheus.parser;

import java.time.Instant;
import java.util.Map;

/**
 * A single sample from a Prometheus query result.
 */
public record PrometheusSample(
    Map<String, String> labels,
    Instant timestamp,
    double value
) {}
