package ai.sreagent.core.patterns;

import ai.sreagent.core.domain.DiagnosticPattern;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Built-in diagnostic patterns for the core demo scenarios.
 * These encode common Kubernetes microservice failure modes.
 *
 * Confidence weights are manually assigned based on SRE diagnostic experience.
 * They are NOT learned from historical incident data.
 * This is an MVP calibration — production systems should learn from real data.
 *
 * Each pattern includes both "core" evidence types (from static JSON fixtures)
 * and "provider alias" types (from Prometheus / Loki / Trace / K8s providers).
 * This ensures the VerificationEngine matches evidence regardless of source.
 */
public final class BuiltinPatterns {

    private BuiltinPatterns() {}

    public static DiagnosticPattern deploymentRegression() {
        return new DiagnosticPattern(
            "deployment_regression",
            "Recent deployment introduced a regression causing service degradation",
            List.of(
                "A deployment event must exist near the alert window",
                "Error rate or latency must change after deployment"
            ),
            List.of(
                // Core types (Scenario E static evidence)
                "deploy_event_near_alert_window",
                "error_rate_spike_after_deploy",
                "dependency_timeout_logs",
                "retry_timeout_config_change",
                // Provider aliases (Prometheus / Loki / Trace)
                "metric_error_rate_spike",
                "metric_latency_p95_spike",
                "metric_latency_p99_spike",
                "log_timeout_error",
                "log_downstream_timeout",
                "log_exception_spike",
                "log_http_5xx",
                "trace_error_span",
                "trace_root_span_slow"
            ),
            List.of(
                "historical_timeout_logs_present",
                "downstream_latency_spike",
                "metric_downstream_latency_spike",
                "trace_downstream_span_slow",
                "trace_child_span_dominates_latency"
            ),
            Map.ofEntries(
                // Core weights (supporting)
                entry("deploy_event_near_alert_window", 0.18),
                entry("error_rate_spike_after_deploy", 0.14),
                entry("dependency_timeout_logs", 0.08),
                entry("retry_timeout_config_change", 0.12),
                // Counter weights — higher = stronger refutation signal
                entry("historical_timeout_logs_present", 0.10),
                entry("downstream_latency_spike", 0.12),
                entry("metric_downstream_latency_spike", 0.15),
                entry("trace_downstream_span_slow", 0.15),
                entry("trace_child_span_dominates_latency", 0.18),
                // Provider alias weights (supporting)
                entry("metric_error_rate_spike", 0.10),
                entry("metric_latency_p95_spike", 0.08),
                entry("metric_latency_p99_spike", 0.08),
                entry("log_timeout_error", 0.08),
                entry("log_downstream_timeout", 0.08),
                entry("log_exception_spike", 0.06),
                entry("log_http_5xx", 0.06),
                entry("trace_error_span", 0.06),
                entry("trace_root_span_slow", 0.06)
            ),
            0.30
        );
    }

    public static DiagnosticPattern downstreamDependencyLatency() {
        return new DiagnosticPattern(
            "downstream_dependency_latency",
            "Downstream service latency causing timeout errors in the caller",
            List.of(
                "Downstream service must show latency degradation",
                "Caller logs must show timeout or connection errors to the downstream"
            ),
            List.of(
                // Core types (Scenario E static evidence)
                "dependency_timeout_logs",
                "downstream_latency_spike",
                "service_dependency_match",
                // Provider aliases (Prometheus / Loki / Trace)
                "metric_downstream_latency_spike",
                "metric_latency_p95_spike",
                "metric_error_rate_spike",
                "log_timeout_error",
                "log_downstream_timeout",
                "log_http_5xx",
                "log_exception_spike",
                "log_retry_exhausted",
                "trace_downstream_span_slow",
                "trace_dependency_path",
                "trace_timeout_span",
                "trace_child_span_dominates_latency"
            ),
            List.of(
                "downstream_5xx_absent",
                "deploy_event_near_alert_window"
            ),
            Map.ofEntries(
                // Core weights
                entry("dependency_timeout_logs", 0.12),
                entry("downstream_latency_spike", 0.14),
                entry("service_dependency_match", 0.14),
                entry("downstream_5xx_absent", 0.10),
                entry("deploy_event_near_alert_window", 0.10),
                // Provider alias weights — downstream evidence is strongest signal
                entry("metric_downstream_latency_spike", 0.16),
                entry("metric_latency_p95_spike", 0.10),
                entry("metric_error_rate_spike", 0.06),
                entry("log_timeout_error", 0.12),
                entry("log_downstream_timeout", 0.14),
                entry("log_http_5xx", 0.08),
                entry("log_exception_spike", 0.06),
                entry("log_retry_exhausted", 0.08),
                entry("trace_downstream_span_slow", 0.16),
                entry("trace_dependency_path", 0.14),
                entry("trace_timeout_span", 0.12),
                entry("trace_child_span_dominates_latency", 0.14)
            ),
            0.25
        );
    }

    public static DiagnosticPattern podOomKilled() {
        return new DiagnosticPattern(
            "pod_oom_killed",
            "Pod killed by OOM due to memory limit too low or memory leak",
            List.of(
                "Kubernetes must report OOMKilled event",
                "Memory usage must approach or exceed limit"
            ),
            List.of(
                // Core types (Scenario F K8s evidence)
                "kubernetes_event_oomkilled",
                "pod_restart_count_increased",
                "memory_usage_near_limit",
                // Provider aliases (Prometheus / K8s)
                "metric_memory_usage_high",
                "metric_restart_rate_increased",
                "metric_cpu_usage_high",
                "log_oom_message",
                "log_crash_loop"
            ),
            List.of(
                "no_restart_observed",
                "memory_usage_normal"
            ),
            Map.ofEntries(
                // Core weights
                entry("kubernetes_event_oomkilled", 0.15),
                entry("pod_restart_count_increased", 0.10),
                entry("memory_usage_near_limit", 0.10),
                entry("no_restart_observed", 0.10),
                entry("memory_usage_normal", 0.10),
                // Provider alias weights
                entry("metric_memory_usage_high", 0.10),
                entry("metric_restart_rate_increased", 0.08),
                entry("metric_cpu_usage_high", 0.06),
                entry("log_oom_message", 0.12),
                entry("log_crash_loop", 0.08)
            ),
            0.10
        );
    }

    public static DiagnosticPattern podCrashLoop() {
        return new DiagnosticPattern(
            "pod_crash_loop",
            "Service instability caused by container crash loop or repeated pod restarts",
            List.of(
                "Kubernetes must report CrashLoopBackOff status on pod",
                "Pod restart count must be elevated"
            ),
            List.of(
                // Core types (Scenario F K8s evidence)
                "container_crash_loop_backoff",
                "pod_restart_count_increased",
                "pod_not_ready",
                "deployment_metadata",
                // Provider aliases (Prometheus / K8s)
                "metric_restart_rate_increased",
                "metric_memory_usage_high",
                "metric_cpu_usage_high",
                "log_crash_loop",
                "log_oom_message",
                "log_exception_spike"
            ),
            List.of(
                "no_restart_observed",
                "pod_ready",
                "container_running_normal"
            ),
            Map.ofEntries(
                // Core weights
                entry("container_crash_loop_backoff", 0.30),
                entry("pod_restart_count_increased", 0.20),
                entry("pod_not_ready", 0.15),
                entry("deployment_metadata", 0.05),
                entry("no_restart_observed", 0.30),
                entry("pod_ready", 0.20),
                entry("container_running_normal", 0.20),
                // Provider alias weights
                entry("metric_restart_rate_increased", 0.15),
                entry("metric_memory_usage_high", 0.08),
                entry("metric_cpu_usage_high", 0.06),
                entry("log_crash_loop", 0.20),
                entry("log_oom_message", 0.10),
                entry("log_exception_spike", 0.08)
            ),
            0.10
        );
    }

    /**
     * Register all built-in patterns into a registry.
     */
    public static PatternRegistry defaultRegistry() {
        PatternRegistry registry = new PatternRegistry();
        registry.register(deploymentRegression());
        registry.register(downstreamDependencyLatency());
        registry.register(podOomKilled());
        registry.register(podCrashLoop());
        return registry;
    }
}
