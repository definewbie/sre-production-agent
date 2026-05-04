package ai.sreagent.server.observability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityEndpointStatusTest {

    @Test
    void shouldCreateEndpointStatus() {
        var status = new ObservabilityEndpointStatus(
                "Prometheus", "prometheus", "http://localhost:9090",
                "connected", 20, "Prometheus ready");

        assertThat(status.name()).isEqualTo("Prometheus");
        assertThat(status.type()).isEqualTo("prometheus");
        assertThat(status.status()).isEqualTo("connected");
        assertThat(status.latencyMs()).isEqualTo(20);
    }
}
