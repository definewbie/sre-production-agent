package ai.sreagent.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTopologyTest {

    private final ServiceTopology topology = new ServiceTopology(Map.of(
            "frontend", List.of("order-service"),
            "order-service", List.of("payment-service", "inventory-service"),
            "payment-service", List.of("gateway"),
            "inventory-service", List.of(),
            "gateway", List.of()
    ));

    @Test
    void findDependencyPath_shouldReturnMultiHopCallPath() {
        PropagationPath path = topology.findDependencyPath(
                "frontend", "gateway", TopologyEdgeSource.CONFIGURED_TOPOLOGY);

        assertThat(path.isPresent()).isTrue();
        assertThat(path.services()).containsExactly(
                "frontend", "order-service", "payment-service", "gateway");
        assertThat(path.pathLength()).isEqualTo(3);
        assertThat(path.direction()).isEqualTo(PropagationDirection.UPSTREAM_TO_DOWNSTREAM);
        assertThat(path.pathConfidence()).isEqualTo(TopologyEdgeConfidence.MEDIUM);
    }

    @Test
    void findImpactPath_shouldReturnReversePropagationPath() {
        PropagationPath path = topology.findImpactPath(
                "gateway", "frontend", TopologyEdgeSource.CONFIGURED_TOPOLOGY);

        assertThat(path.isPresent()).isTrue();
        assertThat(path.services()).containsExactly(
                "gateway", "payment-service", "order-service", "frontend");
        assertThat(path.pathLength()).isEqualTo(3);
        assertThat(path.direction()).isEqualTo(PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT);
    }

    @Test
    void findDependencyPath_withoutPath_shouldReturnNone() {
        PropagationPath path = topology.findDependencyPath(
                "inventory-service", "gateway", TopologyEdgeSource.CONFIGURED_TOPOLOGY);

        assertThat(path.isPresent()).isFalse();
        assertThat(path).isSameAs(PropagationPath.NONE);
    }

    @Test
    void findAffectedNodes_shouldTraverseUpstream() {
        assertThat(topology.findAffectedNodes("gateway"))
                .containsExactlyInAnyOrder(
                        "gateway", "payment-service", "order-service", "frontend");
    }
}
