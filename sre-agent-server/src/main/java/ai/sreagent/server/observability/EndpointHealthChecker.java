package ai.sreagent.server.observability;

/**
 * Checks health of a single observability endpoint.
 */
public interface EndpointHealthChecker {
    ObservabilityEndpointStatus check(ObservabilityEndpointConfig config);
}
