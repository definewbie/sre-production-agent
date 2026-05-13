package ai.sreagent.server.incident;

import ai.sreagent.core.domain.ServiceTopology;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentFingerprintTest {

    @Test
    void fromGroupsServicesInSameTopologyComponent() {
        ServiceTopology topology = new ServiceTopology(Map.of(
                "order-service", List.of("payment-service", "inventory-service"),
                "payment-service", List.of(),
                "inventory-service", List.of()
        ));

        IncidentFingerprint order = IncidentFingerprint.from("order-service", topology);
        IncidentFingerprint payment = IncidentFingerprint.from("payment-service", topology);
        IncidentFingerprint inventory = IncidentFingerprint.from("inventory-service", topology);

        assertThat(payment).isEqualTo(order);
        assertThat(inventory).isEqualTo(order);
        assertThat(order.chainSummary())
                .isEqualTo("inventory-service→order-service→payment-service");
    }

    @Test
    void fromKeepsDisconnectedServicesSeparate() {
        ServiceTopology topology = new ServiceTopology(Map.of(
                "order-service", List.of("payment-service"),
                "payment-service", List.of(),
                "recommend-service", List.of()
        ));

        IncidentFingerprint order = IncidentFingerprint.from("order-service", topology);
        IncidentFingerprint recommend = IncidentFingerprint.from("recommend-service", topology);

        assertThat(order).isNotEqualTo(recommend);
    }

    @Test
    void fromUsesConfiguredTimeWindowBucket() {
        ServiceTopology topology = new ServiceTopology(Map.of(
                "order-service", List.of("payment-service"),
                "payment-service", List.of()
        ));
        Duration window = Duration.ofMinutes(5);
        Instant start = Instant.parse("2026-05-11T12:00:10Z");

        IncidentFingerprint first = IncidentFingerprint.from("order-service", topology, window, start);
        IncidentFingerprint sameWindow = IncidentFingerprint.from("payment-service", topology, window,
                start.plusSeconds(240));
        IncidentFingerprint nextWindow = IncidentFingerprint.from("payment-service", topology, window,
                start.plusSeconds(301));

        assertThat(sameWindow).isEqualTo(first);
        assertThat(nextWindow).isNotEqualTo(first);
    }
}
