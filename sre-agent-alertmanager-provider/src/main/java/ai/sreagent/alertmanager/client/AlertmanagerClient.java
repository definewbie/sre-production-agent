package ai.sreagent.alertmanager.client;

import java.util.Map;

/**
 * Abstraction for Alertmanager alert queries.
 * Implementations: fixture (tests), http (live).
 */
public interface AlertmanagerClient {

    /**
     * Fetch alerts from Alertmanager.
     *
     * @param labelMatchers label matchers (e.g., service=order-service)
     * @param includeResolved whether to include resolved alerts
     * @return raw JSON response string (Alertmanager v2 alerts array)
     */
    String getAlerts(Map<String, String> labelMatchers, boolean includeResolved);

    /**
     * @return true if this client can reach its backend
     */
    boolean isAvailable();

    /**
     * @return display name for logging (e.g. "fixture", "http")
     */
    String clientName();
}
