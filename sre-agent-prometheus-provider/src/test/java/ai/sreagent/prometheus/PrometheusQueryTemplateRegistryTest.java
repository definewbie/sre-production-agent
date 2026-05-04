package ai.sreagent.prometheus;

import ai.sreagent.prometheus.query.PrometheusQueryTemplate;
import ai.sreagent.prometheus.query.PrometheusQueryTemplateRegistry;
import ai.sreagent.prometheus.query.PrometheusQueryType;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusQueryTemplateRegistryTest {

    private PrometheusQueryTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrometheusQueryTemplateRegistry();
    }

    @Nested
    class TemplateRetrieval {

        @Test
        void shouldReturnAllDefaultTemplates() {
            assertThat(registry.getAllTemplates()).hasSize(8);
        }

        @Test
        void shouldSupportAllQueryTypes() {
            Set<PrometheusQueryType> supported = registry.getSupportedTypes();
            assertThat(supported).containsExactlyInAnyOrder(
                    PrometheusQueryType.ERROR_RATE,
                    PrometheusQueryType.LATENCY_P95,
                    PrometheusQueryType.LATENCY_P99,
                    PrometheusQueryType.DOWNSTREAM_LATENCY_P95,
                    PrometheusQueryType.MEMORY_USAGE,
                    PrometheusQueryType.CPU_USAGE,
                    PrometheusQueryType.RESTART_RATE,
                    PrometheusQueryType.REQUEST_RATE);
        }

        @Test
        void shouldReturnTemplateByType() {
            Optional<PrometheusQueryTemplate> template = registry.getTemplate(PrometheusQueryType.ERROR_RATE);
            assertThat(template).isPresent();
            assertThat(template.get().queryType()).isEqualTo(PrometheusQueryType.ERROR_RATE);
            assertThat(template.get().description()).isNotEmpty();
        }
    }

    @Nested
    class VariableSubstitution {

        @Test
        void shouldSubstituteServiceVariable() {
            PrometheusQueryTemplate template = registry.getTemplate(PrometheusQueryType.ERROR_RATE).orElseThrow();
            String query = template.buildQuery("payment-service", "demo");

            assertThat(query).contains("payment-service");
            assertThat(query).doesNotContain("$service");
        }

        @Test
        void shouldSubstituteNamespaceVariable() {
            PrometheusQueryTemplate template = registry.getTemplate(PrometheusQueryType.MEMORY_USAGE).orElseThrow();
            String query = template.buildQuery("payment-service", "production");

            assertThat(query).contains("production");
            assertThat(query).doesNotContain("$namespace");
        }

        @Test
        void shouldContainExpectedMetricNames() {
            PrometheusQueryTemplate errorRate = registry.getTemplate(PrometheusQueryType.ERROR_RATE).orElseThrow();
            assertThat(errorRate.template()).contains("http_requests_total");

            PrometheusQueryTemplate latency = registry.getTemplate(PrometheusQueryType.LATENCY_P95).orElseThrow();
            assertThat(latency.template()).contains("http_request_duration_seconds_bucket");

            PrometheusQueryTemplate memory = registry.getTemplate(PrometheusQueryType.MEMORY_USAGE).orElseThrow();
            assertThat(memory.template()).contains("container_memory_working_set_bytes");

            PrometheusQueryTemplate restart = registry.getTemplate(PrometheusQueryType.RESTART_RATE).orElseThrow();
            assertThat(restart.template()).contains("kube_pod_container_status_restarts_total");
        }

        @Test
        void shouldHandleNullValues() {
            PrometheusQueryTemplate template = registry.getTemplate(PrometheusQueryType.ERROR_RATE).orElseThrow();
            String query = template.buildQuery(null, null);

            assertThat(query).doesNotContain("$service");
            assertThat(query).doesNotContain("$namespace");
        }
    }

    @Nested
    class CustomRegistration {

        @Test
        void shouldRegisterCustomTemplate() {
            registry.register(PrometheusQueryType.ERROR_RATE,
                    "custom_metric{service=\"$service\"}", "Custom error rate query");

            PrometheusQueryTemplate template = registry.getTemplate(PrometheusQueryType.ERROR_RATE).orElseThrow();
            assertThat(template.template()).isEqualTo("custom_metric{service=\"$service\"}");
        }
    }
}
