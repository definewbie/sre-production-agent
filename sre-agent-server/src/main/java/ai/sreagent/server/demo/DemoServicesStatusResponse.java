package ai.sreagent.server.demo;

import java.util.List;

/**
 * Aggregate status response for all demo services.
 */
public record DemoServicesStatusResponse(
        List<DemoServiceStatus> services,
        String topology
) {}
