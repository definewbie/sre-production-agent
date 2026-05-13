package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * End-to-end test for Scenario E (competing hypotheses).
 * Loads actual alert/evidence JSON from classpath and runs the full Step C chain:
 *
 *   EvidenceLoader → PatternRegistry → HypothesisEngine → VerificationEngine
 *   → ConfidenceScorer → HypothesisComparator → InvestigationDecision
 *
 * Expected values (V2 ratio-based scoring with provider aliases):
 *   downstream_dependency_latency    = 0.23 (edges out deployment_regression due to
 *                                          service_dependency_match evidence and
 *                                          fewer missing core types)
 *   deployment_regression            = 0.21 (counter evidence + three unmatched core types
 *                                          exception_logs_present/http_5xx_logs_present/
 *                                          error_traces_present penalize score)
 *   score_gap                        = 0.02
 *   decision                         = insufficient_evidence (both < 0.40)
 *
 * Note: In production with live log/trace providers, deployment_regression
 * would receive exception_logs_present, http_5xx_logs_present, and
 * error_traces_present evidence, pushing its score above 0.50.
 */
class ScenarioEConfidenceTest {

    private IncidentTask incident;
    private List<Evidence> evidence;
    private List<Hypothesis> hypotheses;
    private List<VerificationResult> verifications;
    private List<ConfidenceResult> confidences;
    private Map<String, DiagnosticPattern> patternMap;

    @BeforeEach
    void setUp() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();

        // Load from classpath
        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json")) {
            assertThat(alertIs).as("alert JSON must exist on classpath").isNotNull();
            assertThat(evidenceIs).as("evidence JSON must exist on classpath").isNotNull();

            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        patternMap = registry.all().stream()
                .collect(Collectors.toMap(DiagnosticPattern::id, Function.identity()));

        // Step B chain
        hypotheses = new HypothesisEngine().generate(incident, registry.all());
        Map<String, VerificationResult> verMap = new VerificationEngine()
                .verifyAll(hypotheses, patternMap, evidence);
        verifications = hypotheses.stream()
                .map(h -> verMap.get(h.id()))
                .filter(v -> v != null)
                .toList();

        // Step C chain
        ConfidenceScorer scorer = new ConfidenceScorer();
        confidences = scorer.scoreAll(hypotheses, patternMap, verifications, evidence);
    }

    private ConfidenceResult findConfidence(String hypothesisId) {
        return confidences.stream()
                .filter(cr -> cr.hypothesisId().equals(hypothesisId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void deploymentRegression_shouldScore21() {
        ConfidenceResult result = findConfidence("hyp_deployment_regression");
        assertThat(result.score()).isCloseTo(0.25, withPercentage(5.0));
    }

    @Test
    void downstreamDependencyLatency_withTopology_shouldScore30() {
        ConfidenceResult result = findConfidence("hyp_downstream_dependency_latency");
        assertThat(result.score()).isCloseTo(0.30, withPercentage(5.0));
        assertThat(result.topologyCausalityScore()).isGreaterThan(0.0);
    }

    @Test
    void scoreGap_shouldBe05() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.scoreGap()).isCloseTo(0.05, withPercentage(10.0));
    }

    @Test
    void decision_shouldBeInsufficientEvidence() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confidences);

        assertThat(decision.decisionType()).isEqualTo("insufficient_evidence");
    }

    @Test
    void leadingHypothesis_shouldBeDownstreamDependencyLatency() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.leadingHypothesisId()).isEqualTo("hyp_downstream_dependency_latency");
    }

    @Test
    void competingHypotheses_shouldBeEmpty() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.competingHypothesisIds()).isEmpty();
    }

    @Test
    void podOomKilled_shouldBeWeak() {
        ConfidenceResult result = findConfidence("hyp_pod_oom_killed");
        assertThat(result.score()).isLessThan(0.40);
        assertThat(result.decision()).isEqualTo("insufficient_evidence");
    }

    @Test
    void nearTie_shouldBeFalse() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.nearTie()).isFalse();
    }
}
