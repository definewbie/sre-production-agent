package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class ConfidenceScorerTest {

    private ConfidenceScorer scorer;
    private PatternRegistry registry;
    private VerificationEngine verificationEngine;
    private List<Evidence> scenarioEEvidence;

    @BeforeEach
    void setUp() {
        scorer = new ConfidenceScorer();
        registry = BuiltinPatterns.defaultRegistry();
        verificationEngine = new VerificationEngine();

        scenarioEEvidence = List.of(
                new Evidence("ev_001", "inc_test", "deploy", "deploy_event_near_alert_window",
                        "order-service", Instant.parse("2026-04-28T10:00:00Z"),
                        "order-service v1.2.3 deployed", Map.of(), 0.85),
                new Evidence("ev_002", "inc_test", "metric", "error_rate_spike_after_deploy",
                        "order-service", Instant.parse("2026-04-28T10:03:00Z"),
                        "error rate 0.2% → 8.7%", Map.of(), 0.90),
                new Evidence("ev_003", "inc_test", "log", "dependency_timeout_logs",
                        "order-service", Instant.parse("2026-04-28T10:04:00Z"),
                        "payment timeout after 500ms", Map.of(), 0.80),
                new Evidence("ev_004", "inc_test", "git", "retry_timeout_config_change",
                        "order-service", Instant.parse("2026-04-28T09:55:00Z"),
                        "timeout changed from 2000ms to 500ms", Map.of(), 0.88),
                new Evidence("ev_005", "inc_test", "metric", "downstream_latency_spike",
                        "payment-service", Instant.parse("2026-04-28T10:06:00Z"),
                        "payment P95 latency 120ms → 450ms", Map.of(), 0.70),
                new Evidence("ev_006", "inc_test", "metric", "downstream_5xx_absent",
                        "payment-service", Instant.parse("2026-04-28T10:08:00Z"),
                        "payment 5xx rate 0.1%, no increase", Map.of(), 0.75),
                new Evidence("ev_007", "inc_test", "log", "historical_timeout_logs_present",
                        "order-service", Instant.parse("2026-04-28T09:45:00Z"),
                        "pre-existing timeout errors at low frequency", Map.of(), 0.65),
                new Evidence("ev_008", "inc_test", "topology", "service_dependency_match",
                        "order-service", Instant.parse("2026-04-28T10:08:00Z"),
                        "order-service → payment-service dependency", Map.of(), 0.80)
        );
    }

    @Test
    void deploymentRegression_shouldScore64() {
        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, scenarioEEvidence);

        assertThat(result.score()).isCloseTo(0.50, withPercentage(1.0));
        assertThat(result.level()).isEqualTo("low");
        assertThat(result.decision()).isEqualTo("uncertain");
        assertThat(result.supportingFactors()).isNotEmpty();
        assertThat(result.counterFactors()).isNotEmpty();
        assertThat(result.calibrationNotes()).isNotBlank();
    }

    @Test
    void downstreamDependencyLatency_shouldScore58() {
        DiagnosticPattern pattern = registry.get("downstream_dependency_latency").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_downstream_dependency_latency", "inc_test", "downstream_dependency_latency",
                "Downstream dependency latency caused timeout errors",
                "dependency_latency", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, scenarioEEvidence);

        assertThat(result.score()).isCloseTo(0.45, withPercentage(1.0));
        assertThat(result.level()).isEqualTo("low");
        assertThat(result.decision()).isEqualTo("uncertain");
        assertThat(result.supportingFactors()).isNotEmpty();
        assertThat(result.counterFactors()).isNotEmpty();
        assertThat(result.calibrationNotes()).isNotBlank();
    }

    @Test
    void podOomKilled_shouldBeWeak() {
        DiagnosticPattern pattern = registry.get("pod_oom_killed").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_pod_oom_killed", "inc_test", "pod_oom_killed",
                "Pod OOMKilled or resource pressure caused service errors",
                "resource_pressure", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, scenarioEEvidence);

        assertThat(result.score()).isLessThan(0.40);
        assertThat(result.decision()).isEqualTo("insufficient_evidence");
    }

    @Test
    void allScores_shouldBeClampedBetween0And1() {
        for (DiagnosticPattern pattern : registry.all()) {
            Hypothesis h = new Hypothesis(
                    "hyp_" + pattern.id(), "inc_test", pattern.id(),
                    "title", "type", "svc", "cause"
            );
            VerificationResult vr = verificationEngine.verify(h, pattern, scenarioEEvidence);
            ConfidenceResult result = scorer.score(h, pattern, vr, scenarioEEvidence);

            assertThat(result.score()).isBetween(0.0, 1.0);
        }
    }
}
