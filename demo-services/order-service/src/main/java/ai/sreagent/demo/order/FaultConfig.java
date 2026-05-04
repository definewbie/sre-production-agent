package ai.sreagent.demo.order;

public record FaultConfig(String mode, int latencyMs, double errorRate, double timeoutRate) {
    public static FaultConfig DEFAULT = new FaultConfig("normal", 0, 0.0, 0.0);
}
