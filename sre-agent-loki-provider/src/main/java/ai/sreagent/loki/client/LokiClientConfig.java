package ai.sreagent.loki.client;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for HTTP Loki client.
 */
public record LokiClientConfig(
    String baseUrl,
    Duration timeout,
    Map<String, String> headers
) {
    public LokiClientConfig {
        if (timeout == null) timeout = Duration.ofSeconds(10);
        if (headers == null) headers = Map.of();
    }

    public static LokiClientConfig defaults() {
        return new LokiClientConfig("http://localhost:3100", Duration.ofSeconds(10), Map.of());
    }

    public static LokiClientConfig of(String baseUrl) {
        return new LokiClientConfig(baseUrl, Duration.ofSeconds(10), Map.of());
    }
}
