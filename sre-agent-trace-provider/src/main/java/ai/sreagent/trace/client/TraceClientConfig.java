package ai.sreagent.trace.client;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for HTTP trace backend client.
 */
public record TraceClientConfig(
    String baseUrl,
    String backendType,
    Duration timeout,
    Map<String, String> headers
) {
    public TraceClientConfig {
        if (timeout == null) timeout = Duration.ofSeconds(10);
        if (headers == null) headers = Map.of();
        if (backendType == null) backendType = "jaeger";
    }

    public static TraceClientConfig defaults() {
        return new TraceClientConfig("http://localhost:16686", "jaeger",
                Duration.ofSeconds(10), Map.of());
    }

    public static TraceClientConfig of(String baseUrl, String backendType) {
        return new TraceClientConfig(baseUrl, backendType, Duration.ofSeconds(10), Map.of());
    }
}
