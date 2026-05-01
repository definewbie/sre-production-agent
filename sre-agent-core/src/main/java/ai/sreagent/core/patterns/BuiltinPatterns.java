package ai.sreagent.core.patterns;

import ai.sreagent.core.domain.DiagnosticPattern;

import java.util.List;
import java.util.Map;

/**
 * Built-in diagnostic patterns for the three core demo scenarios.
 * These encode common Kubernetes microservice failure modes.
 *
 * Confidence weights are manually assigned based on SRE diagnostic experience.
 * They are NOT learned from historical incident data.
 * This is an MVP calibration — production systems should learn from real data.
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
                "deploy_event_near_alert_window",
                "error_rate_spike_after_deploy",
                "dependency_timeout_logs",
                "retry_timeout_config_change"
            ),
            List.of(
                "historical_timeout_logs_present",
                "downstream_latency_spike"
            ),
            Map.of(
                "deploy_event_near_alert_window", 0.12,
                "error_rate_spike_after_deploy", 0.10,
                "dependency_timeout_logs", 0.08,
                "retry_timeout_config_change", 0.12,
                "historical_timeout_logs_present", 0.04,
                "downstream_latency_spike", 0.04
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
                "dependency_timeout_logs",
                "downstream_latency_spike",
                "service_dependency_match"
            ),
            List.of(
                "downstream_5xx_absent",
                "deploy_event_near_alert_window"
            ),
            Map.of(
                "dependency_timeout_logs", 0.12,
                "downstream_latency_spike", 0.14,
                "service_dependency_match", 0.14,
                "downstream_5xx_absent", 0.05,
                "deploy_event_near_alert_window", 0.02
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
                "kubernetes_event_oomkilled",
                "pod_restart_count_increased",
                "memory_usage_near_limit"
            ),
            List.of(
                "no_restart_observed",
                "memory_usage_normal"
            ),
            Map.of(
                "kubernetes_event_oomkilled", 0.15,
                "pod_restart_count_increased", 0.10,
                "memory_usage_near_limit", 0.10,
                "no_restart_observed", 0.10,
                "memory_usage_normal", 0.10
            ),
            0.35
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
        return registry;
    }
}
