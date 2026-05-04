package ai.sreagent.server.observability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityStatusServiceTest {

    @Test
    void shouldComputeHealthyWhenAllConnected() {
        var statuses = List.of(
                new ObservabilityEndpointStatus("A", "a", "", "connected", 0, ""),
                new ObservabilityEndpointStatus("B", "b", "", "connected", 0, "")
        );
        assertThat(ObservabilityStatusService.computeOverallStatus(statuses)).isEqualTo("healthy");
    }

    @Test
    void shouldComputePartialWhenSomeConnected() {
        var statuses = List.of(
                new ObservabilityEndpointStatus("A", "a", "", "connected", 0, ""),
                new ObservabilityEndpointStatus("B", "b", "", "disconnected", 0, "")
        );
        assertThat(ObservabilityStatusService.computeOverallStatus(statuses)).isEqualTo("partial");
    }

    @Test
    void shouldComputeDownWhenNoneConnected() {
        var statuses = List.of(
                new ObservabilityEndpointStatus("A", "a", "", "disconnected", 0, ""),
                new ObservabilityEndpointStatus("B", "b", "", "disconnected", 0, "")
        );
        assertThat(ObservabilityStatusService.computeOverallStatus(statuses)).isEqualTo("down");
    }

    @Test
    void shouldIgnoreNotConfigured() {
        var statuses = List.of(
                new ObservabilityEndpointStatus("A", "a", "", "connected", 0, ""),
                new ObservabilityEndpointStatus("B", "b", "", "not_configured", 0, "")
        );
        // only A is checkable, A is connected → healthy
        assertThat(ObservabilityStatusService.computeOverallStatus(statuses)).isEqualTo("healthy");
    }

    @Test
    void shouldReturnUnknownWhenEmpty() {
        assertThat(ObservabilityStatusService.computeOverallStatus(List.of())).isEqualTo("unknown");
    }

    @Test
    void shouldReturnUnknownWhenAllNotConfigured() {
        var statuses = List.of(
                new ObservabilityEndpointStatus("A", "a", "", "not_configured", 0, ""),
                new ObservabilityEndpointStatus("B", "b", "", "not_configured", 0, "")
        );
        assertThat(ObservabilityStatusService.computeOverallStatus(statuses)).isEqualTo("unknown");
    }

    @Test
    void shouldBuildConfigsWithAllUrls() {
        var configs = ObservabilityStatusService.buildConfigs(
                "http://localhost:9090", "http://localhost:9093", "http://localhost:3100",
                "http://localhost:16686", "jaeger", "http://localhost:3000");
        assertThat(configs).hasSize(5);
        assertThat(configs.get(0).name()).isEqualTo("Prometheus");
        assertThat(configs.get(1).name()).isEqualTo("Alertmanager");
        assertThat(configs.get(2).name()).isEqualTo("Loki");
        assertThat(configs.get(3).name()).isEqualTo("Jaeger");
        assertThat(configs.get(4).name()).isEqualTo("Grafana");
    }

    @Test
    void shouldBuildConfigsWithOnlyPrometheus() {
        var configs = ObservabilityStatusService.buildConfigs(
                "http://localhost:9090", null, null, null, null, null);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).name()).isEqualTo("Prometheus");
    }

    @Test
    void shouldUseTempoHealthPathWhenBackendIsTempo() {
        var configs = ObservabilityStatusService.buildConfigs(
                null, null, null, "http://localhost:3200", "tempo", null);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).name()).isEqualTo("Tempo");
        assertThat(configs.get(0).healthPath()).isEqualTo("/ready");
    }

    @Test
    void shouldSkipBlankUrls() {
        var configs = ObservabilityStatusService.buildConfigs(
                "  ", "http://localhost:9093", "", null, null, null);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).name()).isEqualTo("Alertmanager");
    }

    @Test
    void shouldReturnEmptyWhenNoUrls() {
        var configs = ObservabilityStatusService.buildConfigs(null, null, null, null, null, null);
        assertThat(configs).isEmpty();
    }
}
