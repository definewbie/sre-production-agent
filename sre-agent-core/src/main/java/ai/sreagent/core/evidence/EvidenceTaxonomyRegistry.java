package ai.sreagent.core.evidence;

import java.util.*;

/**
 * Registry mapping provider-specific evidence types to normalized taxonomy.
 * All known evidence types from K8s, Prometheus, Loki, Alertmanager, and Trace providers
 * are registered here with their category, signal, source kind, and causal role.
 *
 * Unknown evidence types map to UNKNOWN for all taxonomy fields.
 */
public final class EvidenceTaxonomyRegistry {

    private EvidenceTaxonomyRegistry() {}

    private static final Map<String, TaxonomyEntry> REGISTRY = new LinkedHashMap<>();

    record TaxonomyEntry(
        EvidenceCategory category,
        EvidenceSignal signal,
        EvidenceSourceKind sourceKind,
        EvidenceCausalRole causalRole
    ) {}

    static {
        // ===== Kubernetes evidence types =====
        register("k8s_pod_status",                EvidenceCategory.KUBERNETES, EvidenceSignal.K8S_POD_STATUS,         EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.SYMPTOM);
        register("k8s_deployment_status",          EvidenceCategory.KUBERNETES, EvidenceSignal.K8S_DEPLOYMENT_STATUS,  EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.CONTEXT);
        register("k8s_event",                     EvidenceCategory.KUBERNETES, EvidenceSignal.K8S_EVENT,              EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.SYMPTOM);
        register("container_crash_loop_backoff",   EvidenceCategory.KUBERNETES, EvidenceSignal.CRASH_LOOP,             EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.CAUSE_CANDIDATE);
        register("pod_restart_count_increased",    EvidenceCategory.KUBERNETES, EvidenceSignal.RESTART,                EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.SYMPTOM);
        register("pod_not_ready",                  EvidenceCategory.KUBERNETES, EvidenceSignal.POD_NOT_READY,          EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.SYMPTOM);
        register("deployment_metadata",            EvidenceCategory.KUBERNETES, EvidenceSignal.DEPLOYMENT_METADATA,    EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.CONTEXT);

        // ===== Prometheus evidence types =====
        register("metric_error_rate_spike",        EvidenceCategory.METRIC,     EvidenceSignal.ERROR_RATE_SPIKE,       EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_latency_p95_spike",       EvidenceCategory.METRIC,     EvidenceSignal.LATENCY_SPIKE,          EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_latency_p99_spike",       EvidenceCategory.METRIC,     EvidenceSignal.LATENCY_P99_SPIKE,      EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_downstream_latency_spike",EvidenceCategory.METRIC,     EvidenceSignal.DOWNSTREAM_LATENCY,     EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.CAUSE_CANDIDATE);
        register("metric_memory_usage_high",       EvidenceCategory.METRIC,     EvidenceSignal.MEMORY_PRESSURE,        EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_cpu_usage_high",          EvidenceCategory.METRIC,     EvidenceSignal.CPU_PRESSURE,           EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_restart_rate_increased",  EvidenceCategory.METRIC,     EvidenceSignal.RESTART,                EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_request_rate_drop",       EvidenceCategory.METRIC,     EvidenceSignal.REQUEST_RATE_DROP,      EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.SYMPTOM);
        register("metric_no_signal",               EvidenceCategory.METRIC,     EvidenceSignal.NO_SIGNAL,              EvidenceSourceKind.PROMETHEUS, EvidenceCausalRole.NO_SIGNAL);

        // ===== Loki evidence types =====
        register("log_timeout_error",              EvidenceCategory.LOG,        EvidenceSignal.TIMEOUT,                EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_downstream_timeout",         EvidenceCategory.LOG,        EvidenceSignal.DOWNSTREAM_LATENCY,     EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_exception_spike",            EvidenceCategory.LOG,        EvidenceSignal.EXCEPTION,              EvidenceSourceKind.LOKI,       EvidenceCausalRole.SYMPTOM);
        register("log_crash_loop",                 EvidenceCategory.LOG,        EvidenceSignal.CRASH_LOOP,             EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_oom_message",                EvidenceCategory.LOG,        EvidenceSignal.OOM,                    EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_db_connection_timeout",      EvidenceCategory.LOG,        EvidenceSignal.DB_CONNECTION_TIMEOUT,  EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_retry_exhausted",            EvidenceCategory.LOG,        EvidenceSignal.RETRY_EXHAUSTED,        EvidenceSourceKind.LOKI,       EvidenceCausalRole.CAUSE_CANDIDATE);
        register("log_http_5xx",                   EvidenceCategory.LOG,        EvidenceSignal.HTTP_5XX,               EvidenceSourceKind.LOKI,       EvidenceCausalRole.SYMPTOM);
        register("log_no_signal",                  EvidenceCategory.LOG,        EvidenceSignal.NO_SIGNAL,              EvidenceSourceKind.LOKI,       EvidenceCausalRole.NO_SIGNAL);

        // ===== Alertmanager evidence types =====
        register("alert_firing",                   EvidenceCategory.ALERT,      EvidenceSignal.ALERT_FIRING,           EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.SYMPTOM);
        register("alert_resolved",                 EvidenceCategory.ALERT,      EvidenceSignal.ALERT_RESOLVED,         EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.CONTEXT);
        register("alert_still_firing",             EvidenceCategory.ALERT,      EvidenceSignal.ALERT_STILL_FIRING,     EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.SYMPTOM);
        register("alert_severity_high",            EvidenceCategory.ALERT,      EvidenceSignal.ALERT_SEVERITY_HIGH,    EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.SYMPTOM);
        register("alert_grouped",                  EvidenceCategory.ALERT,      EvidenceSignal.ALERT_GROUPED,          EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.CONTEXT);
        register("alert_silenced",                 EvidenceCategory.ALERT,      EvidenceSignal.ALERT_SILENCED,         EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.CONTEXT);
        register("alert_inhibited",                EvidenceCategory.ALERT,      EvidenceSignal.ALERT_INHIBITED,        EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.CONTEXT);
        register("alert_near_window",              EvidenceCategory.ALERT,      EvidenceSignal.ALERT_NEAR_WINDOW,      EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.CONTEXT);
        register("alert_no_signal",                EvidenceCategory.ALERT,      EvidenceSignal.NO_SIGNAL,              EvidenceSourceKind.ALERTMANAGER, EvidenceCausalRole.NO_SIGNAL);

        // ===== Trace evidence types =====
        register("trace_downstream_span_slow",     EvidenceCategory.TRACE,      EvidenceSignal.DOWNSTREAM_LATENCY,     EvidenceSourceKind.TRACE,      EvidenceCausalRole.CAUSE_CANDIDATE);
        register("trace_error_span",               EvidenceCategory.TRACE,      EvidenceSignal.ERROR_SPAN,             EvidenceSourceKind.TRACE,      EvidenceCausalRole.CAUSE_CANDIDATE);
        register("trace_root_span_slow",           EvidenceCategory.TRACE,      EvidenceSignal.SLOW_SPAN,              EvidenceSourceKind.TRACE,      EvidenceCausalRole.SYMPTOM);
        register("trace_dependency_path",          EvidenceCategory.TRACE,      EvidenceSignal.DEPENDENCY_PATH,        EvidenceSourceKind.TRACE,      EvidenceCausalRole.TOPOLOGY_CONTEXT);
        register("trace_timeout_span",             EvidenceCategory.TRACE,      EvidenceSignal.TIMEOUT_SPAN,           EvidenceSourceKind.TRACE,      EvidenceCausalRole.CAUSE_CANDIDATE);
        register("trace_child_span_dominates_latency", EvidenceCategory.TRACE,  EvidenceSignal.CHILD_DOMINATES_LATENCY,EvidenceSourceKind.TRACE,      EvidenceCausalRole.CAUSE_CANDIDATE);
        register("trace_no_signal",                EvidenceCategory.TRACE,      EvidenceSignal.NO_SIGNAL,              EvidenceSourceKind.TRACE,      EvidenceCausalRole.NO_SIGNAL);

        // ===== Static / Scenario evidence types =====
        register("deploy_event_near_alert_window", EvidenceCategory.DEPLOYMENT, EvidenceSignal.DEPLOYMENT_METADATA,    EvidenceSourceKind.STATIC,     EvidenceCausalRole.CONTEXT);
        register("error_rate_spike_after_deploy",  EvidenceCategory.METRIC,     EvidenceSignal.ERROR_RATE_SPIKE,       EvidenceSourceKind.STATIC,     EvidenceCausalRole.SYMPTOM);
        register("dependency_timeout_logs",        EvidenceCategory.LOG,        EvidenceSignal.TIMEOUT,                EvidenceSourceKind.STATIC,     EvidenceCausalRole.CAUSE_CANDIDATE);
        register("retry_timeout_config_change",    EvidenceCategory.DEPLOYMENT, EvidenceSignal.DEPLOYMENT_METADATA,    EvidenceSourceKind.STATIC,     EvidenceCausalRole.CONTEXT);
        register("historical_timeout_logs_present",EvidenceCategory.LOG,        EvidenceSignal.TIMEOUT,                EvidenceSourceKind.STATIC,     EvidenceCausalRole.CONTEXT);
        register("downstream_latency_spike",       EvidenceCategory.METRIC,     EvidenceSignal.DOWNSTREAM_LATENCY,     EvidenceSourceKind.STATIC,     EvidenceCausalRole.CAUSE_CANDIDATE);
        register("downstream_5xx_absent",          EvidenceCategory.METRIC,     EvidenceSignal.HTTP_5XX,               EvidenceSourceKind.STATIC,     EvidenceCausalRole.COUNTER_SIGNAL);
        register("service_dependency_match",       EvidenceCategory.TOPOLOGY,   EvidenceSignal.SERVICE_DEPENDENCY,     EvidenceSourceKind.STATIC,     EvidenceCausalRole.TOPOLOGY_CONTEXT);
        register("kubernetes_event_oomkilled",     EvidenceCategory.KUBERNETES, EvidenceSignal.OOM,                    EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.CAUSE_CANDIDATE);
        register("liveness_probe_failure",          EvidenceCategory.KUBERNETES, EvidenceSignal.LIVENESS_PROBE_FAILURE,   EvidenceSourceKind.KUBERNETES, EvidenceCausalRole.CAUSE_CANDIDATE);
        register("memory_usage_near_limit",        EvidenceCategory.METRIC,     EvidenceSignal.MEMORY_PRESSURE,        EvidenceSourceKind.STATIC,     EvidenceCausalRole.SYMPTOM);
        register("no_restart_observed",            EvidenceCategory.KUBERNETES, EvidenceSignal.RESTART,                EvidenceSourceKind.STATIC,     EvidenceCausalRole.COUNTER_SIGNAL);
        register("memory_usage_normal",            EvidenceCategory.METRIC,     EvidenceSignal.MEMORY_PRESSURE,        EvidenceSourceKind.STATIC,     EvidenceCausalRole.COUNTER_SIGNAL);
        register("pod_ready",                      EvidenceCategory.KUBERNETES, EvidenceSignal.POD_NOT_READY,          EvidenceSourceKind.STATIC,     EvidenceCausalRole.COUNTER_SIGNAL);
        register("container_running_normal",       EvidenceCategory.KUBERNETES, EvidenceSignal.CRASH_LOOP,             EvidenceSourceKind.STATIC,     EvidenceCausalRole.COUNTER_SIGNAL);
    }

    private static void register(
            String evidenceType,
            EvidenceCategory category,
            EvidenceSignal signal,
            EvidenceSourceKind sourceKind,
            EvidenceCausalRole causalRole) {
        REGISTRY.put(evidenceType, new TaxonomyEntry(category, signal, sourceKind, causalRole));
    }

    /**
     * Look up taxonomy entry for a given evidence type string.
     * Returns null for unknown types.
     */
    public static TaxonomyEntry lookup(String evidenceType) {
        if (evidenceType == null) return null;
        return REGISTRY.get(evidenceType);
    }

    /**
     * Check if an evidence type is registered.
     */
    public static boolean isRegistered(String evidenceType) {
        return evidenceType != null && REGISTRY.containsKey(evidenceType);
    }

    /**
     * Get category for an evidence type, or UNKNOWN if not registered.
     */
    public static EvidenceCategory getCategory(String evidenceType) {
        TaxonomyEntry entry = lookup(evidenceType);
        return entry != null ? entry.category() : EvidenceCategory.UNKNOWN;
    }

    /**
     * Get signal for an evidence type, or UNKNOWN if not registered.
     */
    public static EvidenceSignal getSignal(String evidenceType) {
        TaxonomyEntry entry = lookup(evidenceType);
        return entry != null ? entry.signal() : EvidenceSignal.UNKNOWN;
    }

    /**
     * Get source kind for an evidence type.
     * Falls back to inferring from Evidence.source field if not registered.
     */
    public static EvidenceSourceKind getSourceKind(String evidenceType, String source) {
        TaxonomyEntry entry = lookup(evidenceType);
        if (entry != null) return entry.sourceKind();
        return inferSourceKind(source);
    }

    /**
     * Get causal role for an evidence type, or UNKNOWN if not registered.
     */
    public static EvidenceCausalRole getCausalRole(String evidenceType) {
        TaxonomyEntry entry = lookup(evidenceType);
        return entry != null ? entry.causalRole() : EvidenceCausalRole.UNKNOWN;
    }

    /**
     * Get all registered evidence type strings.
     */
    public static Set<String> registeredTypes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * Get count of registered evidence types.
     */
    public static int size() {
        return REGISTRY.size();
    }

    /**
     * Infer EvidenceSourceKind from the source field of an Evidence object.
     */
    static EvidenceSourceKind inferSourceKind(String source) {
        if (source == null) return EvidenceSourceKind.UNKNOWN;
        return switch (source.toLowerCase()) {
            case "prometheus" -> EvidenceSourceKind.PROMETHEUS;
            case "loki" -> EvidenceSourceKind.LOKI;
            case "alertmanager" -> EvidenceSourceKind.ALERTMANAGER;
            case "tracing" -> EvidenceSourceKind.TRACE;
            case "kubernetes", "k8s" -> EvidenceSourceKind.KUBERNETES;
            case "static", "json" -> EvidenceSourceKind.STATIC;
            default -> EvidenceSourceKind.UNKNOWN;
        };
    }
}
