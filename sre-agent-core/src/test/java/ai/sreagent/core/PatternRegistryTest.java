package ai.sreagent.core;

import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatternRegistryTest {

    @Test
    @DisplayName("Default registry contains all three built-in patterns")
    void defaultRegistryHasAllPatterns() {
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();

        assertThat(registry.size()).isEqualTo(4);
        assertThat(registry.patternIds()).containsExactlyInAnyOrder(
                "deployment_regression",
                "downstream_dependency_latency",
                "pod_oom_killed",
                "pod_crash_loop"
        );
    }

    @Test
    @DisplayName("deployment_regression pattern has correct structure")
    void deploymentRegressionPattern() {
        DiagnosticPattern pattern = BuiltinPatterns.deploymentRegression();

        assertThat(pattern.id()).isEqualTo("deployment_regression");
        assertThat(pattern.baseScore()).isEqualTo(0.30);
        assertThat(pattern.supportingEvidenceTypes()).containsExactlyInAnyOrder(
                "deploy_event_near_alert_window",
                "error_rate_spike_after_deploy",
                "dependency_timeout_logs",
                "retry_timeout_config_change"
        );
        assertThat(pattern.counterEvidenceTypes()).containsExactlyInAnyOrder(
                "historical_timeout_logs_present",
                "downstream_latency_spike"
        );
        assertThat(pattern.confidenceWeights()).containsEntry("deploy_event_near_alert_window", 0.12);
    }

    @Test
    @DisplayName("downstream_dependency_latency pattern has correct structure")
    void downstreamDependencyLatencyPattern() {
        DiagnosticPattern pattern = BuiltinPatterns.downstreamDependencyLatency();

        assertThat(pattern.id()).isEqualTo("downstream_dependency_latency");
        assertThat(pattern.baseScore()).isEqualTo(0.25);
        assertThat(pattern.supportingEvidenceTypes()).containsExactlyInAnyOrder(
                "dependency_timeout_logs",
                "downstream_latency_spike",
                "service_dependency_match"
        );
        assertThat(pattern.counterEvidenceTypes()).containsExactlyInAnyOrder(
                "downstream_5xx_absent",
                "deploy_event_near_alert_window"
        );
    }

    @Test
    @DisplayName("pod_oom_killed pattern has correct structure")
    void podOomKilledPattern() {
        DiagnosticPattern pattern = BuiltinPatterns.podOomKilled();

        assertThat(pattern.id()).isEqualTo("pod_oom_killed");
        assertThat(pattern.baseScore()).isEqualTo(0.35);
        assertThat(pattern.supportingEvidenceTypes()).containsExactlyInAnyOrder(
                "kubernetes_event_oomkilled",
                "pod_restart_count_increased",
                "memory_usage_near_limit"
        );
        assertThat(pattern.counterEvidenceTypes()).containsExactlyInAnyOrder(
                "no_restart_observed",
                "memory_usage_normal"
        );
    }

    @Test
    @DisplayName("Registry get() returns empty for unknown pattern")
    void getUnknownPatternReturnsEmpty() {
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Optional<DiagnosticPattern> result = registry.get("nonexistent_pattern");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Confidence weights sum is reasonable for each pattern")
    void confidenceWeightsAreReasonable() {
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();

        for (DiagnosticPattern pattern : registry.all()) {
            // Only supporting evidence types contribute positively;
            // counter evidence types are subtracted during scoring.
            double supportingSum = pattern.supportingEvidenceTypes().stream()
                    .mapToDouble(t -> pattern.confidenceWeights().getOrDefault(t, 0.0))
                    .sum();
            double maxPossibleScore = pattern.baseScore() + supportingSum;
            assertThat(maxPossibleScore)
                    .as("Max possible score for %s should be between 0 and 1, got %f", pattern.id(), maxPossibleScore)
                    .isBetween(0.0, 1.0);
        }
    }
}
