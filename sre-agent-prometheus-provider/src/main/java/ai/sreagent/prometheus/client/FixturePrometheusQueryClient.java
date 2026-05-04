package ai.sreagent.prometheus.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Reads Prometheus query results from bundled fixture JSON files.
 * Use for testing and CI — no live Prometheus required.
 *
 * Matching strategy:
 * 1. If promql exactly matches a known fixture key (e.g., "error_rate"), use that.
 * 2. If promql contains metric keywords, map to the appropriate fixture.
 * 3. Otherwise, return empty result.
 */
public class FixturePrometheusQueryClient implements PrometheusQueryClient {

    private static final String FIXTURE_BASE = "/fixtures/prometheus/";

    // Keyword → fixture file mapping
    // Order matters: more specific patterns first
    private static final Map.Entry<String, String>[] KEYWORD_PATTERNS = new Map.Entry[] {
        Map.entry("http_requests_total{service", "error_rate_spike.json"),           // error rate
        Map.entry("http_request_duration_seconds_bucket", "latency_p95_spike.json"), // latency
        Map.entry("http_client_request_duration_seconds_bucket", "downstream_latency_spike.json"), // downstream
        Map.entry("container_memory_working_set_bytes", "memory_usage_high.json"),    // memory
        Map.entry("container_cpu_usage_seconds_total", "memory_usage_high.json"),     // cpu → reuse memory fixture
        Map.entry("kube_pod_container_status_restarts_total", "restart_rate_increased.json"), // restart
        Map.entry("sum(rate(http_requests_total", "error_rate_spike.json")            // request rate
    };

    @Override
    public String query(String promql, Instant time) {
        String fixtureFile = resolveFixture(promql);
        return loadFixture(fixtureFile);
    }

    @Override
    public String queryRange(String promql, Instant start, Instant end, Duration step) {
        String fixtureFile = resolveFixture(promql);
        return loadFixture(fixtureFile);
    }

    @Override
    public String clientName() {
        return "fixture";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private String resolveFixture(String promql) {
        if (promql == null || promql.isBlank()) {
            return "empty_result.json";
        }

        // Try keyword pattern matching
        for (Map.Entry<String, String> entry : KEYWORD_PATTERNS) {
            if (promql.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "empty_result.json";
    }

    private String loadFixture(String filename) {
        try (InputStream is = getClass().getResourceAsStream(FIXTURE_BASE + filename)) {
            if (is == null) {
                // Return empty result fixture as fallback
                try (InputStream fallback = getClass().getResourceAsStream(FIXTURE_BASE + "empty_result.json")) {
                    if (fallback != null) {
                        return new String(fallback.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                throw new RuntimeException("Fixture not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fixture: " + filename, e);
        }
    }
}
