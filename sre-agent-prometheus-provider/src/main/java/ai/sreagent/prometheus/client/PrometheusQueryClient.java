package ai.sreagent.prometheus.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Abstraction for querying Prometheus.
 * Implementations may use HTTP, fixture files, or mock data.
 */
public interface PrometheusQueryClient {

    /**
     * Execute an instant query against Prometheus.
     *
     * @param promql PromQL query string
     * @param time   evaluation timestamp
     * @return raw JSON response from Prometheus
     */
    String query(String promql, Instant time);

    /**
     * Execute a range query against Prometheus.
     *
     * @param promql PromQL query string
     * @param start  range start
     * @param end    range end
     * @param step   query resolution step
     * @return raw JSON response from Prometheus
     */
    String queryRange(String promql, Instant start, Instant end, Duration step);

    /**
     * Human-readable name of this client implementation.
     */
    String clientName();

    /**
     * Check if this client is available and functional.
     */
    boolean isAvailable();
}
