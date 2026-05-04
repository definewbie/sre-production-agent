package ai.sreagent.server.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityEndpointConfigTest {

    @Test
    void shouldBuildFullUrlWithHealthPath() {
        var config = new ObservabilityEndpointConfig("Prometheus", "prometheus", "http://localhost:9090", "/-/ready");
        assertThat(config.fullUrl()).isEqualTo("http://localhost:9090/-/ready");
    }

    @Test
    void shouldReturnUrlWhenHealthPathIsNull() {
        var config = new ObservabilityEndpointConfig("Test", "test", "http://localhost:9999", null);
        assertThat(config.fullUrl()).isEqualTo("http://localhost:9999");
    }

    @Test
    void shouldReturnUrlWhenHealthPathIsBlank() {
        var config = new ObservabilityEndpointConfig("Test", "test", "http://localhost:9999", "  ");
        assertThat(config.fullUrl()).isEqualTo("http://localhost:9999");
    }
}
