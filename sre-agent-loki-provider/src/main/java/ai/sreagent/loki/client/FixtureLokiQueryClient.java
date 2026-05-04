package ai.sreagent.loki.client;

import ai.sreagent.loki.query.LokiQueryType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Fixture-based Loki client for deterministic tests.
 * Returns pre-defined JSON fixtures mapped by query type.
 * No network. No live Loki.
 */
public class FixtureLokiQueryClient implements LokiQueryClient {

    private static final String FIXTURE_BASE = "fixtures/loki/";
    private final Map<LokiQueryType, String> fixtureMap;
    private String lastQueryType;

    public FixtureLokiQueryClient() {
        this.fixtureMap = Map.of(
                LokiQueryType.TIMEOUT_ERROR, "timeout_logs.json",
                LokiQueryType.DOWNSTREAM_TIMEOUT, "downstream_timeout_logs.json",
                LokiQueryType.EXCEPTION_LOGS, "exception_logs.json",
                LokiQueryType.CRASH_LOGS, "crash_logs.json",
                LokiQueryType.OOM_LOGS, "oom_logs.json",
                LokiQueryType.DB_CONNECTION_TIMEOUT, "db_connection_timeout_logs.json",
                LokiQueryType.RETRY_EXHAUSTED, "retry_exhausted_logs.json",
                LokiQueryType.HTTP_5XX_LOGS, "http_5xx_logs.json"
        );
    }

    /**
     * Set the query type hint for fixture resolution.
     * Called by provider before executing queries.
     */
    public void setQueryTypeHint(LokiQueryType queryType) {
        this.lastQueryType = queryType != null ? queryType.name() : null;
    }

    @Override
    public String query(String logql, Instant time) {
        return loadFixture();
    }

    @Override
    public String queryRange(String logql, Instant start, Instant end, Duration step) {
        return loadFixture();
    }

    private String loadFixture() {
        // Try query type hint first
        if (lastQueryType != null) {
            try {
                LokiQueryType qt = LokiQueryType.valueOf(lastQueryType);
                String fixtureFile = fixtureMap.get(qt);
                if (fixtureFile != null) {
                    String content = loadResource(FIXTURE_BASE + fixtureFile);
                    if (content != null) return content;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Fallback: try to match by logql keywords
        if (lastQueryType != null) {
            String content = loadResource(FIXTURE_BASE + lastQueryType.toLowerCase() + "_logs.json");
            if (content != null) return content;
        }

        // Final fallback: empty result
        return loadResource(FIXTURE_BASE + "empty_result.json");
    }

    private String loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String clientName() {
        return "fixture";
    }
}
