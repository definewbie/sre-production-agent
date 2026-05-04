package ai.sreagent.server.demo;

/**
 * Status of a single demo microservice.
 */
public record DemoServiceStatus(
        String service,
        String url,
        String health,
        String faultConfig,
        boolean reachable
) {}
