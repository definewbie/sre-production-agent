package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationEngineTest {

    private VerificationEngine verificationEngine;
    private PatternRegistry registry;

    // Synthetic evidence matching Scenario E
    private List<Evidence> scenarioEEvidence;

    @BeforeEach
    void setUp() {
        verificationEngine = new VerificationEngine();
        registry = BuiltinPatterns.defaultRegistry();

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
    void deploymentRegression_shouldHaveSupportingAndCounterEvidence() {
        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service",
                "Recent deployment or config change caused elevated errors"
        );

        VerificationResult result = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        assertThat(result.supportingEvidenceIds()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.counterEvidenceIds()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.contradictions()).isNotEmpty();
        assertThat(result.explanation()).contains("supporting").contains("counter");
    }

    @Test
    void downstreamDependencyLatency_shouldHaveSupportingAndCounterEvidence() {
        DiagnosticPattern pattern = registry.get("downstream_dependency_latency").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_downstream_dependency_latency", "inc_test", "downstream_dependency_latency",
                "Downstream dependency latency caused timeout errors",
                "dependency_latency", "order-service",
                "payment-service latency may have caused order-service timeouts"
        );

        VerificationResult result = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        assertThat(result.supportingEvidenceIds()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.counterEvidenceIds()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.contradictions()).isNotEmpty();
    }

    @Test
    void podOomKilled_shouldBeUnsupported() {
        DiagnosticPattern pattern = registry.get("pod_oom_killed").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_pod_oom_killed", "inc_test", "pod_oom_killed",
                "Pod OOMKilled or resource pressure caused service errors",
                "resource_pressure", "order-service",
                "Pod memory pressure or OOMKilled events caused instability"
        );

        VerificationResult result = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        assertThat(result.supportingEvidenceIds()).isEmpty();
        assertThat(result.contradictions()).isNotEmpty();
        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("OOMKilled") || c.contains("memory pressure"));
    }

    @Test
    void deploymentRegression_shouldDetectHistoricalTimeoutContradiction() {
        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "title", "change_regression", "order-service", "cause"
        );

        VerificationResult result = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("existed before the deployment"));
    }

    @Test
    void downstreamDependencyLatency_shouldDetectDeployEventContradiction() {
        DiagnosticPattern pattern = registry.get("downstream_dependency_latency").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_downstream_dependency_latency", "inc_test", "downstream_dependency_latency",
                "title", "dependency_latency", "order-service", "cause"
        );

        VerificationResult result = verificationEngine.verify(hypothesis, pattern, scenarioEEvidence);

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("recent deployment"));
    }
}
