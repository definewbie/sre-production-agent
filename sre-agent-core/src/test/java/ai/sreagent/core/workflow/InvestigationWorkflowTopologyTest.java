package ai.sreagent.core.workflow;

import ai.sreagent.core.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationWorkflowTopologyTest {

    @Test
    void runFromMemory_withConfiguredTopology_shouldResolvePropagationPath() {
        IncidentTask incident = new IncidentTask(
                "inc-topology-workflow",
                "Order checkout latency",
                "order-service",
                "demo",
                "warning",
                Instant.parse("2026-05-11T04:00:00Z"),
                Map.of(),
                Map.of()
        );
        List<Evidence> evidence = List.of(
                new Evidence("ev-timeout", "inc-topology-workflow", "loki",
                        "log_downstream_timeout", "order-service",
                        Instant.parse("2026-05-11T04:00:30Z"),
                        "Timeout calling payment-service", Map.of(), 0.80),
                new Evidence("ev-latency", "inc-topology-workflow", "prometheus",
                        "metric_latency_p95_spike", "payment-service",
                        Instant.parse("2026-05-11T04:00:10Z"),
                        "payment-service p95 latency high", Map.of(), 0.90)
        );
        ServiceTopology topology = new ServiceTopology(Map.of(
                "order-service", List.of("payment-service"),
                "payment-service", List.of()
        ));

        InvestigationResult result = new InvestigationWorkflow()
                .runFromMemory(incident, evidence, topology);

        ConfidenceResult downstream = result.confidenceResults().stream()
                .filter(c -> "hyp_downstream_dependency_latency".equals(c.hypothesisId()))
                .findFirst()
                .orElseThrow();

        assertThat(downstream.propagationPath().isPresent()).isTrue();
        assertThat(downstream.propagationPath().services())
                .containsExactly("payment-service", "order-service");
        assertThat(downstream.topologyCausalityScore()).isGreaterThan(0.0);
        assertThat(result.eventTrace()).anyMatch(e ->
                "PROPAGATION_PATH_RESOLVED".equals(e.eventType()));
        assertThat(result.markdownReport()).contains("传播路径");
    }

    @Test
    void runFromMemory_withObservedDependencyEvidence_shouldResolvePropagationPath() {
        IncidentTask incident = new IncidentTask(
                "inc-observed-topology",
                "Order checkout latency",
                "order-service",
                "demo",
                "warning",
                Instant.parse("2026-05-11T04:00:00Z"),
                Map.of(),
                Map.of()
        );
        List<Evidence> evidence = List.of(
                new Evidence("ev-timeout", "inc-observed-topology", "loki",
                        "log_downstream_timeout", "order-service",
                        Instant.parse("2026-05-11T04:00:30Z"),
                        "Timeout calling payment-service",
                        Map.of("downstream", "payment-service"), 0.80),
                new Evidence("ev-latency", "inc-observed-topology", "prometheus",
                        "metric_latency_p95_spike", "payment-service",
                        Instant.parse("2026-05-11T04:00:10Z"),
                        "payment-service p95 latency high", Map.of(), 0.90)
        );

        InvestigationResult result = new InvestigationWorkflow()
                .runFromMemory(incident, evidence, new ServiceTopology(Map.of()));

        ConfidenceResult downstream = result.confidenceResults().stream()
                .filter(c -> "hyp_downstream_dependency_latency".equals(c.hypothesisId()))
                .findFirst()
                .orElseThrow();

        assertThat(downstream.propagationPath().isPresent()).isTrue();
        assertThat(downstream.propagationPath().services())
                .containsExactly("payment-service", "order-service");
        assertThat(downstream.propagationPath().pathSource())
                .isEqualTo(TopologyEdgeSource.OBSERVED_DEPENDENCY);
    }
}
