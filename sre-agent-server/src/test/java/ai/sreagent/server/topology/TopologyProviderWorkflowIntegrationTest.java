package ai.sreagent.server.topology;

import ai.sreagent.core.domain.ConfidenceResult;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.domain.TopologyEdgeSource;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyProviderWorkflowIntegrationTest {

    @Test
    void classpathConfiguredTopology_shouldDriveWorkflowPropagationPath() {
        TopologyProvider topologyProvider = new TopologyProvider(new MockEnvironment());
        assertThat(topologyProvider.getTopology().getDownstream("order-service"))
                .contains("payment-service");

        String incidentId = "inc-configured-topology-workflow";
        IncidentTask incident = new IncidentTask(
                incidentId,
                "Order checkout latency",
                "order-service",
                "demo",
                "warning",
                Instant.parse("2026-05-11T04:00:00Z"),
                Map.of(),
                Map.of()
        );
        List<Evidence> evidence = List.of(
                new Evidence("ev-order-timeout", incidentId, "loki",
                        "log_downstream_timeout", "order-service",
                        Instant.parse("2026-05-11T04:00:30Z"),
                        "Order checkout timed out while waiting for a dependency",
                        Map.of(), 0.80),
                new Evidence("ev-payment-latency", incidentId, "prometheus",
                        "metric_latency_p95_spike", "payment-service",
                        Instant.parse("2026-05-11T04:00:10Z"),
                        "Payment p95 latency exceeded threshold",
                        Map.of(), 0.90)
        );

        InvestigationResult result = new InvestigationWorkflow()
                .runFromMemory(incident, evidence, topologyProvider.getTopology());

        ConfidenceResult downstream = result.confidenceResults().stream()
                .filter(c -> "hyp_downstream_dependency_latency".equals(c.hypothesisId()))
                .findFirst()
                .orElseThrow();

        assertThat(downstream.propagationPath().isPresent()).isTrue();
        assertThat(downstream.propagationPath().services())
                .containsExactly("payment-service", "order-service");
        assertThat(downstream.propagationPath().pathSource())
                .isEqualTo(TopologyEdgeSource.CONFIGURED_TOPOLOGY);
        assertThat(downstream.propagationScore()).isGreaterThan(0.0);
        assertThat(downstream.topologyCausalityScore()).isEqualTo(downstream.propagationScore());
        assertThat(result.markdownReport()).contains("Propagation Score");
    }
}
