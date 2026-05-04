package ai.sreagent.prometheus.mapper;

/**
 * Prometheus evidence type constants.
 * These map to generic Evidence.evidenceType strings.
 */
public final class PrometheusEvidenceTypes {

    private PrometheusEvidenceTypes() {}

    public static final String METRIC_ERROR_RATE_SPIKE = "metric_error_rate_spike";
    public static final String METRIC_LATENCY_P95_SPIKE = "metric_latency_p95_spike";
    public static final String METRIC_LATENCY_P99_SPIKE = "metric_latency_p99_spike";
    public static final String METRIC_DOWNSTREAM_LATENCY_SPIKE = "metric_downstream_latency_spike";
    public static final String METRIC_MEMORY_USAGE_HIGH = "metric_memory_usage_high";
    public static final String METRIC_CPU_USAGE_HIGH = "metric_cpu_usage_high";
    public static final String METRIC_RESTART_RATE_INCREASED = "metric_restart_rate_increased";
    public static final String METRIC_REQUEST_RATE_DROP = "metric_request_rate_drop";
    public static final String METRIC_NO_SIGNAL = "metric_no_signal";

    /** Source identifier for all Prometheus evidence */
    public static final String SOURCE = "prometheus";
}
