package ai.sreagent.core.topology;

import ai.sreagent.core.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyBuilderTest {

    @Test
    void build_shouldMergeConfiguredAndObservedDependencyEdges() {
        ServiceTopology configured = new ServiceTopology(Map.of(
                "frontend-service", List.of("order-service"),
                "order-service", List.of()
        ));
        List<Evidence> evidence = List.of(new Evidence(
                "ev-downstream", "inc-1", "prometheus",
                "metric_downstream_latency_p95_spike", "order-service",
                Instant.parse("2026-05-11T04:00:00Z"),
                "order-service downstream latency to payment-service",
                Map.of("downstream", "payment-service"), 0.8
        ));

        ServiceTopology topology = new TopologyBuilder().build(configured, evidence);

        assertThat(topology.getDownstream("frontend-service")).containsExactly("order-service");
        assertThat(topology.getDownstream("order-service")).containsExactly("payment-service");
        assertThat(topology.getEdgeSource("frontend-service", "order-service"))
                .isEqualTo(TopologyEdgeSource.CONFIGURED_TOPOLOGY);
        assertThat(topology.getEdgeSource("order-service", "payment-service"))
                .isEqualTo(TopologyEdgeSource.OBSERVED_DEPENDENCY);
    }

    @Test
    void build_shouldPreferTraceSourceWhenSameEdgeAppearsMultipleTimes() {
        ServiceTopology configured = new ServiceTopology(Map.of());
        List<Evidence> evidence = List.of(
                new Evidence("ev-log", "inc-1", "loki",
                        "log_downstream_timeout", "order-service",
                        Instant.parse("2026-05-11T04:00:00Z"),
                        "Timeout calling payment-service",
                        Map.of("downstream", "payment-service"), 0.7),
                new Evidence("ev-trace", "inc-1", "jaeger",
                        "trace_dependency_path", "payment-service",
                        Instant.parse("2026-05-11T04:00:01Z"),
                        "order-service -> payment-service",
                        Map.of(), 0.9)
        );

        ServiceTopology topology = new TopologyBuilder().build(configured, evidence);

        assertThat(topology.getDownstream("order-service")).containsExactly("payment-service");
        assertThat(topology.getEdgeSource("order-service", "payment-service"))
                .isEqualTo(TopologyEdgeSource.TRACE);
        PropagationPath path = topology.findImpactPath("payment-service", "order-service");
        assertThat(path.pathSource()).isEqualTo(TopologyEdgeSource.TRACE);
        assertThat(path.pathConfidence()).isEqualTo(TopologyEdgeConfidence.HIGH);
    }
}
