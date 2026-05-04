package ai.sreagent.server.observability;

/**
 * Configuration for an observability endpoint.
 * @param name  display name (e.g. "Prometheus")
 * @param type  endpoint type (e.g. "prometheus")
 * @param url   URL to check (e.g. "http://localhost:9090")
 * @param healthPath  health check path (e.g. "/-/ready")
 */
public record ObservabilityEndpointConfig(
        String name,
        String type,
        String url,
        String healthPath
) {
    public String fullUrl() {
        if (healthPath == null || healthPath.isBlank()) {
            return url;
        }
        return url + healthPath;
    }
}
