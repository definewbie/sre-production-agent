package ai.sreagent.core.evidence;

/**
 * Semantic signal extracted from evidence.
 * Provider-agnostic — multiple providers can produce the same signal
 * (e.g. both Prometheus and Trace can produce DOWNSTREAM_LATENCY).
 */
public enum EvidenceSignal {
    ERROR_RATE_SPIKE,
    LATENCY_SPIKE,
    LATENCY_P99_SPIKE,
    DOWNSTREAM_LATENCY,
    TIMEOUT,
    EXCEPTION,
    CRASH_LOOP,
    OOM,
    RESTART,
    POD_NOT_READY,
    MEMORY_PRESSURE,
    CPU_PRESSURE,
    REQUEST_RATE_DROP,
    ALERT_FIRING,
    ALERT_RESOLVED,
    ALERT_STILL_FIRING,
    ALERT_SEVERITY_HIGH,
    ALERT_GROUPED,
    ALERT_SILENCED,
    ALERT_INHIBITED,
    ALERT_NEAR_WINDOW,
    DEPENDENCY_PATH,
    ERROR_SPAN,
    SLOW_SPAN,
    TIMEOUT_SPAN,
    CHILD_DOMINATES_LATENCY,
    DEPLOYMENT_METADATA,
    SERVICE_DEPENDENCY,
    K8S_POD_STATUS,
    K8S_DEPLOYMENT_STATUS,
    K8S_EVENT,
    DB_CONNECTION_TIMEOUT,
    RETRY_EXHAUSTED,
    HTTP_5XX,
    NO_SIGNAL,
    UNKNOWN
}
