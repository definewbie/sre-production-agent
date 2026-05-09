package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * Proves K8s provider evidence can drive the RCA workflow.
 * Uses the same classpath scenario data as ScenarioFCrashLoopWorkflowTest
 * but focuses on the provider-agnostic workflow integration.
 */
class K8sEvidenceToWorkflowTest {

    @Test
    void k8sEvidenceDrivesPodCrashLoopDecision() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        IncidentTask incident;
        List<Evidence> evidence;

        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_evidence.json")) {
            assertThat(alertIs).isNotNull();
            assertThat(evidenceIs).isNotNull();
            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        // All evidence comes from kubernetes source
        assertThat(evidence).allMatch(e -> "kubernetes".equals(e.source()));

        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = registry.all().stream()
                .collect(Collectors.toMap(DiagnosticPattern::id, Function.identity()));

        List<Hypothesis> hypotheses = new HypothesisEngine().generate(incident, registry.all());
        Map<String, VerificationResult> verMap = new VerificationEngine()
                .verifyAll(hypotheses, patternMap, evidence);

        // pod_crash_loop hypothesis must have supporting evidence
        VerificationResult podCrashLoopVer = verMap.get("hyp_pod_crash_loop");
        assertThat(podCrashLoopVer).isNotNull();
        assertThat(podCrashLoopVer.supportingEvidenceIds()).isNotEmpty();

        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confidences = scorer.scoreAll(hypotheses, patternMap,
                List.copyOf(verMap.values()), evidence);

        ConfidenceResult podCrashLoopConf = confidences.stream()
                .filter(cr -> cr.hypothesisId().equals("hyp_pod_crash_loop"))
                .findFirst().orElseThrow();

        // pod_crash_loop must be the top hypothesis
        assertThat(podCrashLoopConf.score()).isCloseTo(0.70, withPercentage(5.0));
        assertThat(podCrashLoopConf.decision()).isEqualTo("probable_root_cause");

        // Decision at incident level
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confidences,
                List.copyOf(verMap.values()), evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confidences);

        assertThat(decision.decisionType()).isEqualTo("probable_root_cause");
        assertThat(decision.selectedHypothesisId()).isEqualTo("hyp_pod_crash_loop");
    }

    @Test
    void k8sEvidenceTypesCoverAllSupportingTypes() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        List<Evidence> evidence;
        try (InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_evidence.json")) {
            assertThat(evidenceIs).isNotNull();
            evidence = loader.loadEvidence(evidenceIs);
        }

        List<String> evidenceTypes = evidence.stream()
                .map(Evidence::evidenceType)
                .toList();

        assertThat(evidenceTypes).containsExactlyInAnyOrder(
                "container_crash_loop_backoff",
                "pod_restart_count_increased",
                "pod_not_ready",
                "deployment_metadata"
        );
    }
}
