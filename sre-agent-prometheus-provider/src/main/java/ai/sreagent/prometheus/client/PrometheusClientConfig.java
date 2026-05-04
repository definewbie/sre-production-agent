package ai.sreagent.prometheus.client;

import java.time.Duration;
import java.time.Instant;

/**
 * Configuration for HTTP Prometheus client.
 */
public record PrometheusClientConfig(
    String baseUrl,
    Duration timeout,
    java.util.Map<String, String> headers
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    public PrometheusClientConfig {
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        }
        if (headers == null) {
            headers = java.util.Map.of();
        }
    }

    public static PrometheusClientConfig defaults() {
        return new PrometheusClientConfig("http://localhost:9090", DEFAULT_TIMEOUT, java.util.Map.of());
    }

    public static PrometheusClientConfig of(String baseUrl) {
        return new PrometheusClientConfig(baseUrl, DEFAULT_TIMEOUT, java.util.Map.of());
    }

    public static PrometheusClientConfig of(String baseUrl, Duration timeout) {
        return new PrometheusClientConfig(baseUrl, timeout, java.util.Map.of());
    }
}
