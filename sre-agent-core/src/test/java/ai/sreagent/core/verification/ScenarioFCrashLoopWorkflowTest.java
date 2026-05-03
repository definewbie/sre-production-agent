package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.domain.DiagnosticPattern;
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
 * End-to-end test for Scenario F (K8s CrashLoopBackOff).
 * Loads k8s_crashloop alert/evidence from classpath and runs the full chain.
 *
 * Target values:
 *   pod_crash_loop score >= 0.80
 *   decision = likely_root_cause
 *   selected hypothesis = hyp_pod_crash_loop
 */
class ScenarioFCrashLoopWorkflowTest {

    private IncidentTask incident;
    private List<Evidence> evidence;
    private List<Hypothesis> hypotheses;
    private List<VerificationResult> verifications;
    private List<ConfidenceResult> confidences;
    private Map<String, DiagnosticPattern> patternMap;

    @BeforeEach
    void setUp() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_evidence.json")) {
            assertThat(alertIs).as("k8s_crashloop alert JSON must exist on classpath").isNotNull();
            assertThat(evidenceIs).as("k8s_crashloop evidence JSON must exist on classpath").isNotNull();

            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        patternMap = registry.all().stream()
                .collect(Collectors.toMap(DiagnosticPattern::id, Function.identity()));

        hypotheses = new HypothesisEngine().generate(incident, registry.all());
        Map<String, VerificationResult> verMap = new VerificationEngine()
                .verifyAll(hypotheses, patternMap, evidence);
        verifications = hypotheses.stream()
                .map(h -> verMap.get(h.id()))
                .filter(v -> v != null)
                .toList();

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
    void podCrashLoop_shouldScoreAbove80() {
        ConfidenceResult result = findConfidence("hyp_pod_crash_loop");
        assertThat(result.score()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void podCrashLoop_shouldBeLikelyRootCause() {
        ConfidenceResult result = findConfidence("hyp_pod_crash_loop");
        assertThat(result.decision()).isEqualTo("likely_root_cause");
    }

    @Test
    void decision_shouldBeLikelyRootCause() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confidences);

        assertThat(decision.decisionType()).isEqualTo("likely_root_cause");
    }

    @Test
    void selectedHypothesis_shouldBePodCrashLoop() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confidences);

        assertThat(decision.selectedHypothesisId()).isEqualTo("hyp_pod_crash_loop");
    }

    @Test
    void scoreGap_shouldBeAbove15() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.scoreGap()).isGreaterThanOrEqualTo(0.15);
    }

    @Test
    void podCrashLoop_shouldBeHighConfidence() {
        ConfidenceResult result = findConfidence("hyp_pod_crash_loop");
        assertThat(result.level()).isEqualTo("high");
    }

    @Test
    void incidentService_shouldBeRecommendService() {
        assertThat(incident.service()).isEqualTo("recommend-service");
    }

    @Test
    void incidentAlert_shouldBePodCrashLooping() {
        assertThat(incident.alertName()).isEqualTo("PodCrashLooping");
    }

    @Test
    void fourK8sEvidenceLoaded() {
        assertThat(evidence).hasSize(4);
    }

    @Test
    void noCompetingHypotheses() {
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences, verifications, evidence);

        assertThat(comparison.nearTie()).isFalse();
        assertThat(comparison.competingHypothesisIds()).isEmpty();
    }
}
