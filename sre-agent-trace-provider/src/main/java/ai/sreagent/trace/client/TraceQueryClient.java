package ai.sreagent.trace.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Abstraction for trace backend queries.
 * Implementations: fixture (tests), http (live).
 * Backend-neutral — not tied to Jaeger, Tempo, or any specific vendor.
 */
public interface TraceQueryClient {

    /**
     * Find traces matching the given service and time range.
     *
     * @param service service name filter
     * @param start   range start
     * @param end     range end
     * @param limit   max traces to return
     * @return raw JSON response string
     */
    String findTraces(String service, Instant start, Instant end, int limit);

    /**
     * Get a single trace by ID.
     *
     * @param traceId trace identifier
     * @return raw JSON response string
     */
    String getTrace(String traceId);

    /**
     * @return true if this client can reach its backend
     */
    boolean isAvailable();

    /**
     * @return display name for logging (e.g. "fixture", "http")
     */
    String clientName();
}
