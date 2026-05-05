package ai.sreagent.server.live;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.domain.ConfidenceResult;
import ai.sreagent.core.domain.VerificationResult;
import ai.sreagent.core.verification.ConfidenceScorer;
import ai.sreagent.core.verification.VerificationEngine;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Kubernetes runtime evidence correctly influences Scenario G RCA:
 * - Healthy pods exclude pod_oom_killed and pod_crash_loop
 * - Kubernetes evidence provides counter-evidence against false hypotheses
 * - downstream_dependency_latency remains the leading hypothesis
 */
class ScenarioGKubernetesRcaTest {

    private static final String INC_ID = "inc-scenario-g-k8s";

    private Evidence makeEvidence(String id, String source, String type, String service,
                                   String content, double strength) {
        return new Evidence(id, INC_ID, source, type, service, Instant.now(), content, Map.of(), strength);
    }

    /**
     * Build Scenario G evidence WITH Kubernetes runtime context:
     * - Pods are Running, restartCount=0 (healthy)
     * - Deployment has all replicas ready
     * This should help exclude pod_oom_killed and pod_crash_loop.
     */
    private List<Evidence> buildScenarioGWithK8sRuntime() {
        return List.of(
            // Prometheus: latency / error signals
            makeEvidence("ev-k8s-1", "prometheus", "metric_latency_p95_spike", "payment-service",
                    "p95 latency=2500ms", 0.85),
            makeEvidence("ev-k8s-2", "prometheus", "metric_downstream_latency_spike", "payment-service",
                    "downstream latency spike", 0.90),
            makeEvidence("ev-k8s-3", "prometheus", "metric_error_rate_spike", "order-service",
                    "error rate=8.2%", 0.70),

            // Loki: timeout / downstream signals
            makeEvidence("ev-k8s-4", "loki", "log_timeout_error", "order-service",
                    "timeout errors to payment-service", 0.80),
            makeEvidence("ev-k8s-5", "loki", "log_downstream_timeout", "order-service",
                    "downstream timeout to payment-service", 0.85),

            // Jaeger: slow span / dependency signals
            makeEvidence("ev-k8s-6", "jaeger", "trace_downstream_span_slow", "payment-service",
                    "payment span took 2500ms", 0.85),
            makeEvidence("ev-k8s-7", "jaeger", "trace_dependency_path", "order-service",
                    "order→payment dependency path", 0.80),

            // Kubernetes: runtime context (healthy pods, no restarts)
            makeEvidence("ev-k8s-8", "kubernetes", "deployment_metadata", "order-service",
                    "Deployment order-service: 3/3 replicas ready", 0.40),
            makeEvidence("ev-k8s-9", "kubernetes", "deployment_metadata", "payment-service",
                    "Deployment payment-service: 2/2 replicas ready", 0.40),
            makeEvidence("ev-k8s-10", "kubernetes", "k8s_pod_status", "order-service",
                    "Pod order-service-abc Running, restartCount=0", 0.30),
            makeEvidence("ev-k8s-11", "kubernetes", "k8s_pod_status", "payment-service",
                    "Pod payment-service-xyz Running, restartCount=0", 0.30)
        );
    }

    /**
     * Build Scenario G evidence WITHOUT Kubernetes runtime context.
     * Used to demonstrate that k8s evidence helps exclude false hypotheses.
     */
    private List<Evidence> buildScenarioGWithoutK8s() {
        return List.of(
            makeEvidence("ev-nk-1", "prometheus", "metric_latency_p95_spike", "payment-service",
                    "p95 latency=2500ms", 0.85),
            makeEvidence("ev-nk-2", "prometheus", "metric_downstream_latency_spike", "payment-service",
                    "downstream latency spike", 0.90),
            makeEvidence("ev-nk-3", "prometheus", "metric_error_rate_spike", "order-service",
                    "error rate=8.2%", 0.70),
            makeEvidence("ev-nk-4", "loki", "log_timeout_error", "order-service",
                    "timeout errors", 0.80),
            makeEvidence("ev-nk-5", "loki", "log_downstream_timeout", "order-service",
                    "downstream timeout", 0.85),
            makeEvidence("ev-nk-6", "jaeger", "trace_downstream_span_slow", "payment-service",
                    "slow span 2500ms", 0.85),
            makeEvidence("ev-nk-7", "jaeger", "trace_dependency_path", "order-service",
                    "dependency path", 0.80)
        );
    }

    @Test
    void withK8sRuntime_downstreamStillLeading() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        Hypothesis selected = result.hypotheses().stream()
                .filter(h -> h.id().equals(result.decision().selectedHypothesisId()))
                .findFirst().orElse(null);

        assertThat(selected).isNotNull();
        assertThat(selected.patternId())
                .as("downstream_dependency_latency should be leading, not pod_oom_killed")
                .isEqualTo("downstream_dependency_latency");
    }

    @Test
    void withK8sRuntime_oomNotLeading() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        Hypothesis selected = result.hypotheses().stream()
                .filter(h -> h.id().equals(result.decision().selectedHypothesisId()))
                .findFirst().orElse(null);

        assertThat(selected).isNotNull();
        assertThat(selected.patternId())
                .as("pod_oom_killed must NOT be the leading hypothesis")
                .isNotEqualTo("pod_oom_killed");
    }

    @Test
    void withK8sRuntime_crashLoopNotLeading() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.runFromMemory(incident, evidence);

        Hypothesis selected = result.hypotheses().stream()
                .filter(h -> h.id().equals(result.decision().selectedHypothesisId()))
                .findFirst().orElse(null);

        assertThat(selected).isNotNull();
        assertThat(selected.patternId())
                .as("pod_crash_loop must NOT be the leading hypothesis")
                .isNotEqualTo("pod_crash_loop");
    }

    @Test
    void withK8sRuntime_downstreamScoreHigherThanOom() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
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

        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, new ArrayList<>(verMap.values()), evidence);

        ConfidenceResult downstream = findConfidence(confResults, hypotheses, "downstream_dependency_latency");
        ConfidenceResult oom = findConfidence(confResults, hypotheses, "pod_oom_killed");

        assertThat(downstream).isNotNull();
        assertThat(oom).isNotNull();
        assertThat(downstream.score())
                .as("downstream (%.2f) > oom (%.2f)".formatted(downstream.score(), oom.score()))
                .isGreaterThan(oom.score());
    }

    @Test
    void withK8sRuntime_downstreamScoreHigherThanCrashLoop() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
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

        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, new ArrayList<>(verMap.values()), evidence);

        ConfidenceResult downstream = findConfidence(confResults, hypotheses, "downstream_dependency_latency");
        ConfidenceResult crashLoop = findConfidence(confResults, hypotheses, "pod_crash_loop");

        assertThat(downstream).isNotNull();
        assertThat(crashLoop).isNotNull();
        assertThat(downstream.score())
                .as("downstream (%.2f) > crash_loop (%.2f)".formatted(downstream.score(), crashLoop.score()))
                .isGreaterThan(crashLoop.score());
    }

    @Test
    void kubernetesEvidence_entersAggregatedList() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
        long k8sCount = evidence.stream().filter(e -> "kubernetes".equals(e.source())).count();
        assertThat(k8sCount).as("Should have Kubernetes evidence in the list").isGreaterThanOrEqualTo(2);
    }

    @Test
    void kubernetesEvidence_notTreatedAsNoSignal() {
        List<Evidence> evidence = buildScenarioGWithK8sRuntime();
        List<Evidence> k8sEvidence = evidence.stream()
                .filter(e -> "kubernetes".equals(e.source()))
                .toList();

        for (Evidence e : k8sEvidence) {
            assertThat(e.evidenceType())
                    .as("Kubernetes evidence should not be _no_signal: " + e.evidenceType())
                    .doesNotEndWith("_no_signal");
        }
    }

    private ConfidenceResult findConfidence(List<ConfidenceResult> results,
                                             List<Hypothesis> hypotheses,
                                             String patternId) {
        return results.stream()
                .filter(cr -> {
                    Hypothesis h = hypotheses.stream()
                            .filter(hyp -> hyp.id().equals(cr.hypothesisId()))
                            .findFirst().orElse(null);
                    return h != null && patternId.equals(h.patternId());
                })
                .findFirst().orElse(null);
    }
}
