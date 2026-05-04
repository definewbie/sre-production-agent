package ai.sreagent.loki.client;

import java.time.Duration;
import java.time.Instant;

/**
 * Abstraction for Loki log query execution.
 * Implementations: fixture (tests), http (live).
 */
public interface LokiQueryClient {

    /**
     * Execute an instant LogQL query.
     *
     * @param logql LogQL query string
     * @param time  query evaluation time
     * @return raw JSON response string
     */
    String query(String logql, Instant time);

    /**
     * Execute a range LogQL query.
     *
     * @param logql LogQL query string
     * @param start range start
     * @param end   range end
     * @param step  query step interval
     * @return raw JSON response string
     */
    String queryRange(String logql, Instant start, Instant end, Duration step);

    /**
     * @return true if this client can reach its backend
     */
    boolean isAvailable();

    /**
     * @return display name for logging (e.g. "fixture", "http")
     */
    String clientName();
}
