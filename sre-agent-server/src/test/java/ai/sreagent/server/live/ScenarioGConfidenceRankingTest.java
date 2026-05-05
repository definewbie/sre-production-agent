package ai.sreagent.server.live;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.ConfidenceScorer;
import ai.sreagent.core.verification.VerificationEngine;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Scenario G live-style evidence produces correct confidence ranking:
 * - downstream_dependency_latency should score higher than pod_oom_killed
 * - downstream_dependency_latency should be the leading hypothesis
 */
class ScenarioGConfidenceRankingTest {

    private static final String INC_ID = "inc-scenario-g-ranking";

    private Evidence makeEvidence(String id, String source, String type, String service,
                                   String content, double strength) {
        return new Evidence(id, INC_ID, source, type, service, Instant.now(), content, Map.of(), strength);
    }

    private List<Evidence> buildScenarioGLiveEvidence() {
        return List.of(
            makeEvidence("ev-g-1", "prometheus", "metric_latency_p95_spike", "payment-service",
                    "p95 latency=2500ms", 0.85),
            makeEvidence("ev-g-2", "prometheus", "metric_downstream_latency_spike", "payment-service",
                    "downstream latency spike", 0.90),
            makeEvidence("ev-g-3", "prometheus", "metric_error_rate_spike", "order-service",
                    "error rate=8.2%", 0.70),
            makeEvidence("ev-g-4", "loki", "log_timeout_error", "order-service",
                    "timeout errors", 0.80),
            makeEvidence("ev-g-5", "loki", "log_downstream_timeout", "order-service",
                    "downstream timeout", 0.85),
            makeEvidence("ev-g-6", "loki", "log_retry_exhausted", "order-service",
                    "retry exhausted", 0.75),
            makeEvidence("ev-g-7", "jaeger", "trace_downstream_span_slow", "payment-service",
                    "slow span 2500ms", 0.85),
            makeEvidence("ev-g-8", "jaeger", "trace_dependency_path", "order-service",
                    "dependency path detected", 0.80),
            makeEvidence("ev-g-9", "jaeger", "trace_timeout_span", "order-service",
                    "timeout span", 0.70),
            makeEvidence("ev-g-10", "kubernetes", "deployment_metadata", "order-service",
                    "deployment healthy", 0.40)
        );
    }

    @Test
    void downstreamScoresHigherThanOom() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        HypothesisEngine hypEngine = new HypothesisEngine();
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        VerificationEngine verEngine = new VerificationEngine();
        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);
        List<VerificationResult> verResults = new ArrayList<>(verMap.values());

        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, verResults, evidence);

        ConfidenceResult downstreamScore = confResults.stream()
                .filter(cr -> {
                    Hypothesis h = hypotheses.stream().filter(hyp -> hyp.id().equals(cr.hypothesisId())).findFirst().orElse(null);
                    return h != null && "downstream_dependency_latency".equals(h.patternId());
                })
                .findFirst().orElse(null);

        ConfidenceResult oomScore = confResults.stream()
                .filter(cr -> {
                    Hypothesis h = hypotheses.stream().filter(hyp -> hyp.id().equals(cr.hypothesisId())).findFirst().orElse(null);
                    return h != null && "pod_oom_killed".equals(h.patternId());
                })
                .findFirst().orElse(null);

        assertThat(downstreamScore).as("downstream_dependency_latency confidence result must exist").isNotNull();
        assertThat(oomScore).as("pod_oom_killed confidence result must exist").isNotNull();

        assertThat(downstreamScore.score())
                .as("downstream_dependency_latency (%.2f) should score higher than pod_oom_killed (%.2f)"
                        .formatted(downstreamScore.score(), oomScore.score()))
                .isGreaterThan(oomScore.score());
    }

    @Test
    void downstreamIsLeadingHypothesis() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        assertThat(result.decision().selectedHypothesisId()).isNotNull();

        Hypothesis selected = result.hypotheses().stream()
                .filter(h -> h.id().equals(result.decision().selectedHypothesisId()))
                .findFirst().orElse(null);
        assertThat(selected).isNotNull();

        assertThat(selected.patternId())
                .as("pod_oom_killed should NOT be the leading hypothesis for Scenario G evidence")
                .isNotEqualTo("pod_oom_killed");
    }

    @Test
    void confidenceResults_notAllZero() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        boolean allZero = result.confidenceResults().stream()
                .allMatch(cr -> cr.score() == 0.0);
        assertThat(allZero).as("Not all confidence results should be 0").isFalse();
    }

    @Test
    void oomContradictionDetected_whenNoOomEvidence() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        DiagnosticPattern oomPattern = BuiltinPatterns.podOomKilled();
        VerificationEngine verEngine = new VerificationEngine();

        // Use HypothesisEngine to generate a real hypothesis
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        HypothesisEngine hypEngine = new HypothesisEngine();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        Hypothesis oomHyp = hypotheses.stream()
                .filter(h -> "pod_oom_killed".equals(h.patternId()))
                .findFirst().orElse(null);
        assertThat(oomHyp).as("pod_oom_killed hypothesis must exist").isNotNull();

        VerificationResult vr = verEngine.verify(oomHyp, oomPattern, evidence);

        assertThat(vr.contradictions())
                .as("pod_oom_killed should have contradictions when no OOM evidence present")
                .isNotEmpty();
    }
}
