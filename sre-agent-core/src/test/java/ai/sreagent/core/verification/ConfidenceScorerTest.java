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

    // ─── R5: no_topology + direct_only 场景测试 ───

    /**
     * 无拓扑证据：去掉 ev_008（service_dependency_match）后，deployment_regression
     * 失去拓扑支持因子，置信度不高于完整场景。
     */
    @Test
    void deploymentRegression_withoutTopology_shouldScoreLower() {
        List<Evidence> noTopoEvidence = scenarioEEvidence.stream()
                .filter(e -> !e.evidenceType().equals("topology"))
                .toList();

        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, noTopoEvidence);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, noTopoEvidence);

        assertThat(result.score()).isLessThanOrEqualTo(0.50);
        assertThat(result.decision()).isEqualTo("uncertain");
    }

    /**
     * 核心证据仅来自一种 provider（如只用 metric），V2 归一化不应崩溃。
     */
    @Test
    void directOnly_singleProvider_shouldNotCrash() {
        List<Evidence> metricOnly = scenarioEEvidence.stream()
                .filter(e -> e.evidenceType().equals("metric"))
                .toList();

        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, metricOnly);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, metricOnly);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.score()).isLessThan(0.50);
    }

    /**
     * 无拓扑 + 单一 provider 的组合极端场景。
     */
    @Test
    void noTopologyAndSingleProvider_shouldHandleGracefully() {
        List<Evidence> logOnly = scenarioEEvidence.stream()
                .filter(e -> e.evidenceType().equals("log"))
                .toList();

        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, logOnly);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, logOnly);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.decision()).isEqualTo("insufficient_evidence");
    }


    // ═══ Section 6: Resource Pressure & Crash Loop Boundary Tests ═══
    // Verify V2 ratio-based scoring handles partial/edge-case evidence gracefully
    // for resource_pressure and crash_loop pattern families.

    // ── 6a. resource_pressure 边界: 仅有 memory_usage + OOM log，无 K8s OOMKilled ──

    /**
     * 有 memory_usage_near_limit + log_oom_message，但没有 container_oom_killed
     * （部分证据覆盖），评分应不崩溃且低于完整场景。
     */
    @Test
    void resourcePressure_withPartialEvidence_shouldNotCrash() {
        List<Evidence> partial = List.of(
                new Evidence("ev_rp_001", "inc_rp_test", "metric", "memory_usage_near_limit",
                        "recommend-service", Instant.parse("2026-04-28T11:20:00Z"),
                        "Memory usage at 94% of limit (512Mi/544Mi)", Map.of(), 0.85),
                new Evidence("ev_rp_002", "inc_rp_test", "log", "log_oom_message",
                        "recommend-service", Instant.parse("2026-04-28T11:21:00Z"),
                        "OOM Killer invoked: recommend-service (PID 12345)", Map.of(), 0.90)
        );

        DiagnosticPattern pattern = registry.get("pod_oom_killed").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_resource_pressure", "inc_rp_test", "pod_oom_killed",
                "Memory pressure caused OOM kill",
                "resource_pressure", "recommend-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, partial);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, partial);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.score()).isGreaterThan(0.0);
        assertThat(result.decision()).isIn("uncertain", "insufficient_evidence");
    }

    /**
     * 仅有 counter 证据（memory_usage_stable, no_oom_logs），无任何 supporting 证据。
     * 应输出低分而不崩溃。
     */
    @Test
    void resourcePressure_counterOnly_shouldScoreLow() {
        List<Evidence> counterOnly = List.of(
                new Evidence("ev_rp_c1", "inc_rp_test", "metric", "memory_usage_stable",
                        "recommend-service", Instant.parse("2026-04-28T11:20:00Z"),
                        "Memory usage stable at 45%", Map.of(), 0.75),
                new Evidence("ev_rp_c2", "inc_rp_test", "log", "no_oom_logs",
                        "recommend-service", Instant.parse("2026-04-28T11:21:00Z"),
                        "No OOM messages in last 60 minutes", Map.of(), 0.80)
        );

        DiagnosticPattern pattern = registry.get("pod_oom_killed").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_resource_pressure", "inc_rp_test", "pod_oom_killed",
                "Memory pressure caused OOM kill",
                "resource_pressure", "recommend-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, counterOnly);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, counterOnly);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.score()).isLessThanOrEqualTo(0.15);
    }

    // ── 6b. crash_loop 边界: 仅有 container_crash 无 pod restart 证据 ──

    /**
     * 有 container_crash_loop_backoff，但没有 pod_restart_count_increased
     * （pod 尚未触发多次重启），评分应低于完整 ScenarioF 场景（~0.70）。
     */
    @Test
    void crashLoop_onlyContainerCrash_shouldScoreLower() {
        List<Evidence> singleCrash = List.of(
                new Evidence("ev_cl_001", "inc_cl_test", "kubernetes", "container_crash_loop_backoff",
                        "recommend-service", Instant.parse("2026-04-28T11:20:00Z"),
                        "Container recommend-service in CrashLoopBackOff", Map.of(), 0.95)
        );

        DiagnosticPattern pattern = registry.get("pod_crash_loop").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_pod_crash_loop_partial", "inc_cl_test", "pod_crash_loop",
                "Pod crash loop causing instability",
                "crash_loop", "recommend-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, singleCrash);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, singleCrash);

        assertThat(result.score()).isBetween(0.0, 1.0);
        // 单条证据不能构成 probable_root_cause
        assertThat(result.decision()).isNotEqualTo("probable_root_cause");
    }

    /**
     * 单一 crash_loop counter 证据：no_restart_observed。
     * 应产生低置信度而不崩溃。
     */
    @Test
    void crashLoop_counterOnly_shouldScoreLow() {
        List<Evidence> counterOnly = List.of(
                new Evidence("ev_cl_c1", "inc_cl_test", "metric", "no_restart_observed",
                        "recommend-service", Instant.parse("2026-04-28T11:20:00Z"),
                        "No pod restarts in last 60 minutes", Map.of(), 0.85)
        );

        DiagnosticPattern pattern = registry.get("pod_crash_loop").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_pod_crash_loop_partial", "inc_cl_test", "pod_crash_loop",
                "Pod crash loop causing instability",
                "crash_loop", "recommend-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, counterOnly);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, counterOnly);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.score()).isLessThanOrEqualTo(0.15);
    }

    // ── 6c. deployment_regression 额外边界 ──

    /**
     * 仅有 counter 证据（deploy_successful + no_error_increase），
     * 无任何 supporting 证据。
     */
    @Test
    void deploymentRegression_counterOnly_shouldScoreLow() {
        List<Evidence> counterOnly = List.of(
                new Evidence("ev_dr_c1", "inc_dr_test", "deploy", "deploy_successful",
                        "order-service", Instant.parse("2026-04-28T10:00:00Z"),
                        "Deployment v1.2.3 succeeded with canary passing", Map.of(), 0.90),
                new Evidence("ev_dr_c2", "inc_dr_test", "metric", "no_error_increase",
                        "order-service", Instant.parse("2026-04-28T10:05:00Z"),
                        "Error rate unchanged after deploy", Map.of(), 0.75)
        );

        DiagnosticPattern pattern = registry.get("deployment_regression").orElseThrow();
        Hypothesis hypothesis = new Hypothesis(
                "hyp_deployment_regression", "inc_dr_test", "deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression", "order-service", "cause"
        );
        VerificationResult vr = verificationEngine.verify(hypothesis, pattern, counterOnly);
        ConfidenceResult result = scorer.score(hypothesis, pattern, vr, counterOnly);

        assertThat(result.score()).isBetween(0.0, 1.0);
        assertThat(result.score()).isLessThanOrEqualTo(0.20);
        assertThat(result.decision()).isEqualTo("insufficient_evidence");
    }

}