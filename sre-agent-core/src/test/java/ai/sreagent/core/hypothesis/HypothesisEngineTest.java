package ai.sreagent.core.hypothesis;

import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.domain.Hypothesis;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HypothesisEngineTest {

    private HypothesisEngine engine;
    private PatternRegistry registry;
    private IncidentTask incident;

    @BeforeEach
    void setUp() {
        engine = new HypothesisEngine();
        registry = BuiltinPatterns.defaultRegistry();
        incident = new IncidentTask(
                "inc_test",
                "HighErrorRate",
                "order-service",
                "prod",
                "critical",
                Instant.parse("2026-04-28T10:03:00Z"),
                Map.of("team", "order-platform"),
                Map.of("description", "order-service error rate exceeded 5%")
        );
    }

    @Test
    void shouldGenerateFourHypotheses() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        assertThat(hypotheses).hasSize(4);
    }

    @Test
    void shouldContainAllPatternIds() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        List<String> patternIds = hypotheses.stream()
                .map(Hypothesis::patternId)
                .toList();

        assertThat(patternIds).containsExactlyInAnyOrder(
                "deployment_regression",
                "downstream_dependency_latency",
                "pod_oom_killed",
                "pod_crash_loop"
        );
    }

    @Test
    void shouldGenerateStableHypothesisIds() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        List<String> hypothesisIds = hypotheses.stream()
                .map(Hypothesis::id)
                .toList();

        assertThat(hypothesisIds).containsExactlyInAnyOrder(
                "hyp_deployment_regression",
                "hyp_downstream_dependency_latency",
                "hyp_pod_oom_killed",
                "hyp_pod_crash_loop"
        );
    }

    @Test
    void shouldBindIncidentIdToAllHypotheses() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        assertThat(hypotheses).allMatch(h -> "inc_test".equals(h.incidentId()));
    }

    @Test
    void shouldSetAffectedServiceToIncidentService() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        assertThat(hypotheses).allMatch(h -> "order-service".equals(h.affectedService()));
    }

    @Test
    void shouldSetCorrectRootCauseTypes() {
        List<Hypothesis> hypotheses = engine.generate(incident, registry.all());

        Map<String, String> byPattern = hypotheses.stream()
                .collect(java.util.stream.Collectors.toMap(Hypothesis::patternId, Hypothesis::rootCauseType));

        assertThat(byPattern)
                .containsEntry("deployment_regression", "change_regression")
                .containsEntry("downstream_dependency_latency", "dependency_latency")
                .containsEntry("pod_oom_killed", "resource_pressure")
                .containsEntry("pod_crash_loop", "kubernetes_crash_loop");
    }
}
