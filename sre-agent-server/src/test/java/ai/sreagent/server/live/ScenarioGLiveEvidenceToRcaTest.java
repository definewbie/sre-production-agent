package ai.sreagent.server.live;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.ConfidenceScorer;
import ai.sreagent.core.verification.HypothesisComparator;
import ai.sreagent.core.verification.VerificationEngine;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Scenario G live-style evidence is correctly consumed by the RCA pipeline.
 * Evidence objects are constructed to match what live Prometheus/Loki/Jaeger would produce.
 * No fixture clients are used.
 */
class ScenarioGLiveEvidenceToRcaTest {

    private static final String INC_ID = "inc-scenario-g-test";

    private Evidence makeEvidence(String id, String source, String type, String service,
                                   String content, double strength) {
        return new Evidence(id, INC_ID, source, type, service, Instant.now(), content, Map.of(), strength);
    }

    private List<Evidence> buildScenarioGLiveEvidence() {
        return List.of(
            makeEvidence("ev-g-1", "prometheus", "metric_latency_p95_spike", "payment-service",
                    "Prometheus: p95 latency for payment-service=2500ms (threshold=1000ms)", 0.85),
            makeEvidence("ev-g-2", "prometheus", "metric_downstream_latency_spike", "payment-service",
                    "Prometheus: downstream latency spike detected for payment-service (p95=2500ms)", 0.90),
            makeEvidence("ev-g-3", "prometheus", "metric_error_rate_spike", "order-service",
                    "Prometheus: error rate for order-service=8.2% (threshold=5%)", 0.70),
            makeEvidence("ev-g-4", "prometheus", "metric_request_rate", "order-service",
                    "Prometheus: request rate for order-service=120 req/s", 0.50),

            makeEvidence("ev-g-5", "loki", "log_timeout_error", "order-service",
                    "Loki: timeout errors calling payment-service in order-service logs", 0.80),
            makeEvidence("ev-g-6", "loki", "log_downstream_timeout", "order-service",
                    "Loki: downstream timeout to payment-service detected", 0.85),
            makeEvidence("ev-g-7", "loki", "log_retry_exhausted", "order-service",
                    "Loki: retry exhausted for payment-service calls", 0.75),
            makeEvidence("ev-g-8", "loki", "log_exception_spike", "order-service",
                    "Loki: exception spike in order-service", 0.65),

            makeEvidence("ev-g-9", "jaeger", "trace_downstream_span_slow", "payment-service",
                    "Jaeger: payment-service span took 2500ms in order-service checkout trace", 0.85),
            makeEvidence("ev-g-10", "jaeger", "trace_dependency_path", "order-service",
                    "Jaeger: dependency path order-service→payment-service detected", 0.80),
            makeEvidence("ev-g-11", "jaeger", "trace_timeout_span", "order-service",
                    "Jaeger: timeout span detected in order-service→payment-service", 0.70),

            makeEvidence("ev-g-12", "kubernetes", "deployment_metadata", "order-service",
                    "Kubernetes: deployment order-service replicas=3/3 available", 0.40),
            makeEvidence("ev-g-13", "kubernetes", "pod_healthy", "payment-service",
                    "Kubernetes: all payment-service pods healthy, no restarts", 0.30)
        );
    }

    @Test
    void liveEvidence_entersRcaVerification() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        assertThat(result).isNotNull();
        assertThat(result.decision()).isNotNull();
        assertThat(result.verificationResults()).isNotEmpty();
        assertThat(result.confidenceResults()).isNotEmpty();
    }

    @Test
    void downstreamDependencyLatency_hasSupportingEvidence() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();

        VerificationEngine verEngine = new VerificationEngine();
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        HypothesisEngine hypEngine = new HypothesisEngine();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        Hypothesis downstreamHyp = hypotheses.stream()
                .filter(h -> "downstream_dependency_latency".equals(h.patternId()))
                .findFirst().orElse(null);
        assertThat(downstreamHyp).as("downstream_dependency_latency hypothesis must exist").isNotNull();

        DiagnosticPattern downstreamPattern = patternMap.get("downstream_dependency_latency");
        VerificationResult verResult = verEngine.verify(downstreamHyp, downstreamPattern, evidence);

        assertThat(verResult.supportingEvidenceIds())
                .as("downstream_dependency_latency should have supporting evidence from live-style sources")
                .isNotEmpty();
    }

    @Test
    void prometheusLokiJaeger_allContributeToVerification() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();

        VerificationEngine verEngine = new VerificationEngine();
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        HypothesisEngine hypEngine = new HypothesisEngine();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);

        Set<String> allSupportingIds = new HashSet<>();
        for (VerificationResult vr : verMap.values()) {
            allSupportingIds.addAll(vr.supportingEvidenceIds());
        }

        boolean hasPrometheus = allSupportingIds.stream().anyMatch(id ->
                evidence.stream().filter(e -> e.id().equals(id)).anyMatch(e -> "prometheus".equals(e.source())));
        boolean hasLoki = allSupportingIds.stream().anyMatch(id ->
                evidence.stream().filter(e -> e.id().equals(id)).anyMatch(e -> "loki".equals(e.source())));
        boolean hasJaeger = allSupportingIds.stream().anyMatch(id ->
                evidence.stream().filter(e -> e.id().equals(id)).anyMatch(e -> "jaeger".equals(e.source())));

        assertThat(hasPrometheus).as("At least 1 Prometheus evidence must be in supporting").isTrue();
        assertThat(hasLoki).as("At least 1 Loki evidence must be in supporting").isTrue();
        assertThat(hasJaeger).as("At least 1 Jaeger evidence must be in supporting").isTrue();
    }

    @Test
    void fullRcaPipeline_producesNonZeroScores() {
        List<Evidence> evidence = buildScenarioGLiveEvidence();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        boolean anyNonZero = result.confidenceResults().stream()
                .anyMatch(cr -> cr.score() > 0.0);
        assertThat(anyNonZero).as("At least one hypothesis should have non-zero confidence").isTrue();
    }

    @Test
    void noSignalEvidence_notTreatedAsValidAnomaly() {
        Evidence noSignal = new Evidence("ev-ns-1", INC_ID, "prometheus", "metric_memory_usage_no_signal",
                "payment-service", Instant.now(), "No signal", Map.of(), 0.1);
        Evidence real = new Evidence("ev-rl-1", INC_ID, "prometheus", "metric_latency_p95_spike",
                "payment-service", Instant.now(), "Latency spike", Map.of(), 0.85);

        List<Evidence> evidence = List.of(noSignal, real);

        VerificationEngine verEngine = new VerificationEngine();
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        HypothesisEngine hypEngine = new HypothesisEngine();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);

        for (VerificationResult vr : verMap.values()) {
            assertThat(vr.supportingEvidenceIds()).doesNotContain("ev-ns-1");
        }
    }
}
