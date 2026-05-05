package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class HypothesisComparatorTest {

    private HypothesisComparator comparator;
    private IncidentTask incident;

    @BeforeEach
    void setUp() {
        comparator = new HypothesisComparator();
        incident = new IncidentTask(
                "inc_test", "HighErrorRate", "order-service", "prod",
                "critical", Instant.parse("2026-04-28T10:03:00Z"),
                Map.of(), Map.of()
        );
    }

    private List<ConfidenceResult> buildScenarioEConfidenceResults() {
        return List.of(
                new ConfidenceResult(
                        "hyp_deployment_regression", 0.64, "medium",
                        List.of("deploy_event", "error_spike", "timeout_logs", "config_change"),
                        List.of("historical_timeout", "downstream_latency"),
                        List.of(), List.of(),
                        "probable_root_cause", "calibration note"
                ),
                new ConfidenceResult(
                        "hyp_downstream_dependency_latency", 0.58, "low",
                        List.of("timeout_logs", "latency_spike", "dependency_match"),
                        List.of("downstream_5xx_absent", "deploy_event"),
                        List.of(), List.of(),
                        "uncertain", "calibration note"
                ),
                new ConfidenceResult(
                        "hyp_pod_oom_killed", 0.15, "very_low",
                        List.of(), List.of(),
                        List.of("OOM event required", "memory data required"),
                        List.of("No OOM evidence found"),
                        "insufficient_evidence", "calibration note"
                )
        );
    }

    private List<VerificationResult> buildScenarioEVerificationResults() {
        return List.of(
                new VerificationResult(
                        "hyp_deployment_regression",
                        List.of("ev_001", "ev_002", "ev_003", "ev_004"),
                        List.of("ev_005", "ev_007"),
                        List.of(), List.of("contradiction1", "contradiction2"),
                        "explanation"
                ),
                new VerificationResult(
                        "hyp_downstream_dependency_latency",
                        List.of("ev_003", "ev_005", "ev_008"),
                        List.of("ev_006", "ev_001"),
                        List.of(), List.of("contradiction1", "contradiction2"),
                        "explanation"
                ),
                new VerificationResult(
                        "hyp_pod_oom_killed",
                        List.of(), List.of(),
                        List.of("OOM required", "memory required"),
                        List.of("No OOM evidence"),
                        "explanation"
                )
        );
    }

    @Test
    void shouldIdentifyLeadingHypothesis() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        assertThat(comparison.leadingHypothesisId()).isEqualTo("hyp_deployment_regression");
    }

    @Test
    void shouldDetectCompetingHypotheses() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        assertThat(comparison.competingHypothesisIds())
                .contains("hyp_downstream_dependency_latency");
    }

    @Test
    void shouldComputeScoreGap() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        assertThat(comparison.scoreGap()).isCloseTo(0.06, withPercentage(1.0));
    }

    @Test
    void shouldDetectNearTie() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        assertThat(comparison.nearTie()).isTrue();
    }

    @Test
    void shouldProduceCompetingHypothesesDecision() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        InvestigationDecision decision = comparator.decide(incident, comparison, results);

        assertThat(decision.decisionType()).isEqualTo("competing_hypotheses");
        assertThat(decision.selectedHypothesisId()).isEqualTo("hyp_deployment_regression");
        assertThat(decision.competingHypotheses()).contains("hyp_downstream_dependency_latency");
    }

    @Test
    void shouldIncludeNextProbes() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        InvestigationDecision decision = comparator.decide(incident, comparison, results);

        assertThat(decision.nextProbes()).isNotEmpty();
    }

    @Test
    void shouldIncludeRationale() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        InvestigationDecision decision = comparator.decide(incident, comparison, results);

        assertThat(decision.rationale()).isNotBlank();
        assertThat(decision.rationale()).contains("分数接近");
    }

    @Test
    void comparisonSummary_shouldNotOverclaim() {
        List<ConfidenceResult> results = buildScenarioEConfidenceResults();
        List<VerificationResult> verResults = buildScenarioEVerificationResults();

        HypothesisComparison comparison = comparator.compare(
                incident, results, verResults, List.of()
        );

        assertThat(comparison.comparisonSummary()).contains("竞争假设");
    }
}
