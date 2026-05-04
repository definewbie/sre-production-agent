package ai.sreagent.server.observability;

/**
 * Health status of a single observability endpoint.
 */
public record ObservabilityEndpointStatus(
        String name,
        String type,
        String url,
        String status,      // connected, disconnected, unknown, not_configured
        long latencyMs,
        String message
) {}
