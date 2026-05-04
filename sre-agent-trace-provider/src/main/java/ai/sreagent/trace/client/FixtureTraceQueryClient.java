package ai.sreagent.trace.client;

import ai.sreagent.trace.query.TraceQueryType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Fixture-based trace client for deterministic tests.
 * Returns pre-defined JSON fixtures mapped by query type.
 * No network. No live tracing backend.
 */
public class FixtureTraceQueryClient implements TraceQueryClient {

    private static final String FIXTURE_BASE = "fixtures/trace/";

    private final Map<TraceQueryType, String> fixtureMap;
    private String fixtureOverride;
    private boolean explicitFixture = false;

    public FixtureTraceQueryClient() {
        this.fixtureMap = Map.of(
                TraceQueryType.DOWNSTREAM_SLOW_SPAN, "downstream_slow_span.json",
                TraceQueryType.ERROR_SPAN, "error_span.json",
                TraceQueryType.ROOT_SPAN_SLOW, "root_span_slow.json",
                TraceQueryType.DEPENDENCY_PATH, "dependency_path_order_payment.json",
                TraceQueryType.TIMEOUT_SPAN, "timeout_span.json"
        );
    }

    /**
     * Set the query type hint for fixture resolution.
     * Called by provider before executing queries.
     */
    public void setQueryTypeHint(TraceQueryType queryType) {
        if (!explicitFixture) {
            String fixtureFile = fixtureMap.get(queryType);
            if (fixtureFile != null) {
                this.fixtureOverride = fixtureFile;
            }
        }
    }

    /**
     * Explicitly set fixture file — used by tests.
     * Once set, query type hints will not override it.
     */
    public void setFixtureName(String fixtureName) {
        this.fixtureOverride = fixtureName;
        this.explicitFixture = true;
    }

    /**
     * @return true if an explicit fixture has been set by tests
     */
    public boolean hasExplicitFixture() {
        return explicitFixture;
    }

    @Override
    public String findTraces(String service, Instant start, Instant end, int limit) {
        return loadFixture();
    }

    @Override
    public String getTrace(String traceId) {
        return loadFixture();
    }

    private String loadFixture() {
        // Try explicit or hint-based fixture first
        if (fixtureOverride != null) {
            String content = loadResource(FIXTURE_BASE + fixtureOverride);
            if (content != null) return content;
        }

        // Final fallback: empty result
        return loadResource(FIXTURE_BASE + "empty_trace.json");
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
