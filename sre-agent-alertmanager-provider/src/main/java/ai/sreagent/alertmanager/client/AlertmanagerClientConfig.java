package ai.sreagent.alertmanager.client;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for HTTP Alertmanager client.
 */
public record AlertmanagerClientConfig(
    String baseUrl,
    Duration timeout,
    Map<String, String> headers
) {
    public AlertmanagerClientConfig {
        if (timeout == null) timeout = Duration.ofSeconds(10);
        if (headers == null) headers = Map.of();
    }

    public static AlertmanagerClientConfig defaults() {
        return new AlertmanagerClientConfig("http://localhost:9093", Duration.ofSeconds(10), Map.of());
    }

    public static AlertmanagerClientConfig of(String baseUrl) {
        return new AlertmanagerClientConfig(baseUrl, Duration.ofSeconds(10), Map.of());
    }
}
