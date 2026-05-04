package ai.sreagent.prometheus;

import ai.sreagent.prometheus.client.HttpPrometheusQueryClient;
import ai.sreagent.prometheus.client.PrometheusClientConfig;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPrometheusQueryClientTest {

    @Nested
    class ConfigHandling {

        @Test
        void shouldReportNotAvailableWhenNoPrometheus() {
            PrometheusClientConfig config = PrometheusClientConfig.of("http://localhost:9999");
            HttpPrometheusQueryClient client = new HttpPrometheusQueryClient(config);

            // No Prometheus running on 9999, should be unavailable
            assertThat(client.isAvailable()).isFalse();
        }

        @Test
        void shouldReturnHttpClientName() {
            PrometheusClientConfig config = PrometheusClientConfig.defaults();
            HttpPrometheusQueryClient client = new HttpPrometheusQueryClient(config);

            assertThat(client.clientName()).isEqualTo("http");
        }
    }

    @Nested
    class ConfigCreation {

        @Test
        void shouldCreateDefaultConfig() {
            PrometheusClientConfig config = PrometheusClientConfig.defaults();
            assertThat(config.baseUrl()).isEqualTo("http://localhost:9090");
            assertThat(config.timeout()).isNotNull();
            assertThat(config.headers()).isEmpty();
        }

        @Test
        void shouldCreateConfigWithBaseUrl() {
            PrometheusClientConfig config = PrometheusClientConfig.of("http://prometheus:9090");
            assertThat(config.baseUrl()).isEqualTo("http://prometheus:9090");
        }

        @Test
        void shouldHandleNullTimeout() {
            PrometheusClientConfig config = new PrometheusClientConfig("http://localhost:9090", null, null);
            assertThat(config.timeout()).isNotNull();
            assertThat(config.headers()).isEmpty();
        }
    }
}
