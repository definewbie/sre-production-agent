package ai.sreagent.prometheus.query;

/**
 * Types of Prometheus queries supported by the provider.
 * Each type maps to a query template and evidence mapping strategy.
 */
public enum PrometheusQueryType {

    ERROR_RATE("error_rate"),
    LATENCY_P95("latency_p95"),
    LATENCY_P99("latency_p99"),
    DOWNSTREAM_LATENCY_P95("downstream_latency_p95"),
    MEMORY_USAGE("memory_usage"),
    CPU_USAGE("cpu_usage"),
    RESTART_RATE("restart_rate"),
    REQUEST_RATE("request_rate");

    private final String key;

    PrometheusQueryType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /**
     * Parse from string, case-insensitive.
     * Returns null for unknown types.
     */
    public static PrometheusQueryType fromString(String value) {
        if (value == null) return null;
        for (PrometheusQueryType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.key.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
