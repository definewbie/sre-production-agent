package ai.sreagent.server.observability;

import java.time.Instant;
import java.util.List;

/**
 * Overall observability status response.
 */
public record ObservabilityStatusResponse(
        String overallStatus,   // healthy, partial, down, unknown
        Instant checkedAt,
        List<ObservabilityEndpointStatus> endpoints
) {}
