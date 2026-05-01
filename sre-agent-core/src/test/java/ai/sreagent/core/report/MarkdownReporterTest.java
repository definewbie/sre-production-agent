package ai.sreagent.core.report;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.ConfidenceScorer;
import ai.sreagent.core.verification.HypothesisComparator;
import ai.sreagent.core.verification.VerificationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReporterTest {

    private String report;

    @BeforeEach
    void setUp() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        IncidentTask incident;
        List<Evidence> evidence;
        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json")) {
            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        HypothesisEngine hypEngine = new HypothesisEngine();
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        VerificationEngine verEngine = new VerificationEngine();
        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);
        List<VerificationResult> verResults = new ArrayList<>(verMap.values());

        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, verResults, evidence);

        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confResults, verResults, evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confResults);

        MarkdownReporter reporter = new MarkdownReporter();
        report = reporter.generate(incident, hypotheses, verResults, confResults, comparison, decision, evidence);
    }

    @Test
    void reportContainsTitle() {
        assertThat(report).contains("Competing Hypotheses Report");
    }

    @Test
    void reportContainsDecision() {
        assertThat(report).contains("Decision: competing_hypotheses");
    }

    @Test
    void reportContainsLeadingHypothesis() {
        assertThat(report).contains("deployment_regression");
    }

    @Test
    void reportContainsCompetingHypothesis() {
        assertThat(report).contains("downstream_dependency_latency");
    }

    @Test
    void reportContainsScores() {
        assertThat(report).contains("0.64");
        assertThat(report).contains("0.58");
    }

    @Test
    void reportContainsScoreGap() {
        assertThat(report).contains("0.06");
    }

    @Test
    void reportContainsCalibrationNotes() {
        assertThat(report).contains("Calibration Notes");
    }

    @Test
    void reportContainsSuggestedNextProbes() {
        assertThat(report).contains("Suggested Next Probes");
    }

    @Test
    void reportContainsContradictions() {
        assertThat(report).contains("Contradictions");
    }

    @Test
    void reportContainsCounterEvidence() {
        assertThat(report).contains("Counter Evidence");
    }
}
