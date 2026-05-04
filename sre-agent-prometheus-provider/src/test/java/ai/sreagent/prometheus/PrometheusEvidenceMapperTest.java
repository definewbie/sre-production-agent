package ai.sreagent.prometheus;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.prometheus.mapper.PrometheusEvidenceMapper;
import ai.sreagent.prometheus.mapper.PrometheusEvidenceTypes;
import ai.sreagent.prometheus.parser.PrometheusQueryResult;
import ai.sreagent.prometheus.parser.PrometheusSample;
import ai.sreagent.prometheus.query.PrometheusQueryType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEvidenceMapperTest {

    private PrometheusEvidenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PrometheusEvidenceMapper();
    }

    private PrometheusQueryResult singleResult(double value, Map<String, String> labels) {
        PrometheusSample sample = new PrometheusSample(
                labels, Instant.ofEpochSecond(1714292400L), value);
        return new PrometheusQueryResult("vector", List.of(sample));
    }

    private PrometheusQueryResult emptyResult() {
        return new PrometheusQueryResult("vector", List.of());
    }

    @Nested
    class ErrorRateMapping {

        @Test
        void shouldMapErrorRateSpike() {
            PrometheusQueryResult result = singleResult(0.12, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.ERROR_RATE, result,
                    "sum(rate(http_requests_total{service=\"payment-service\"}[5m]))",
                    "inc-001", "payment-service", "demo",
                    Instant.now().minusSeconds(1800), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_ERROR_RATE_SPIKE);
            assertThat(evidence.get(0).source()).isEqualTo("prometheus");
            assertThat(evidence.get(0).strength()).isGreaterThan(0.3);
            assertThat(evidence.get(0).content()).contains("payment-service");
        }

        @Test
        void shouldNotMapBelowThreshold() {
            PrometheusQueryResult result = singleResult(0.02, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.ERROR_RATE, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).isEmpty();
        }
    }

    @Nested
    class LatencyMapping {

        @Test
        void shouldMapLatencyP95Spike() {
            PrometheusQueryResult result = singleResult(1.25, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.LATENCY_P95, result, "histogram_quantile(...)",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_LATENCY_P95_SPIKE);
        }

        @Test
        void shouldNotMapBelowThreshold() {
            PrometheusQueryResult result = singleResult(0.5, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.LATENCY_P95, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).isEmpty();
        }

        @Test
        void shouldMapLatencyP99Spike() {
            PrometheusQueryResult result = singleResult(3.0, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.LATENCY_P99, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_LATENCY_P99_SPIKE);
        }
    }

    @Nested
    class DownstreamLatencyMapping {

        @Test
        void shouldMapDownstreamLatencySpike() {
            PrometheusQueryResult result = singleResult(2.5, Map.of("service", "order-service", "downstream", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.DOWNSTREAM_LATENCY_P95, result, "query",
                    "inc-001", "order-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_DOWNSTREAM_LATENCY_SPIKE);
        }
    }

    @Nested
    class MemoryMapping {

        @Test
        void shouldMapMemoryUsageHigh() {
            PrometheusQueryResult result = singleResult(2_147_483_648.0, Map.of("pod", "payment-service-abc"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.MEMORY_USAGE, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_MEMORY_USAGE_HIGH);
        }

        @Test
        void shouldNotMapBelowMemoryThreshold() {
            PrometheusQueryResult result = singleResult(500_000_000.0, Map.of("pod", "payment-service-abc"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.MEMORY_USAGE, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).isEmpty();
        }
    }

    @Nested
    class RestartRateMapping {

        @Test
        void shouldMapRestartRateIncreased() {
            PrometheusQueryResult result = singleResult(5.0, Map.of("pod", "payment-service-abc"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.RESTART_RATE, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_RESTART_RATE_INCREASED);
        }

        @Test
        void shouldNotMapZeroRestarts() {
            PrometheusQueryResult result = singleResult(0.0, Map.of("pod", "payment-service-abc"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.RESTART_RATE, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).isEmpty();
        }
    }

    @Nested
    class NoSignalMapping {

        @Test
        void shouldMapEmptyResultToNoSignal() {
            PrometheusQueryResult result = emptyResult();

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.LATENCY_P95, result, "query",
                    "inc-001", "payment-service", "demo",
                    Instant.now(), Instant.now());

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo(PrometheusEvidenceTypes.METRIC_NO_SIGNAL);
            assertThat(evidence.get(0).strength()).isEqualTo(0.1);
        }
    }

    @Nested
    class AttributesMapping {

        @Test
        void shouldIncludeRequiredAttributes() {
            PrometheusQueryResult result = singleResult(1.25, Map.of("service", "payment-service"));

            List<Evidence> evidence = mapper.map(
                    PrometheusQueryType.LATENCY_P95, result, "test_promql",
                    "inc-001", "payment-service", "demo",
                    Instant.parse("2024-04-28T10:00:00Z"), Instant.parse("2024-04-28T10:30:00Z"));

            assertThat(evidence).hasSize(1);
            Map<String, Object> attrs = evidence.get(0).attributes();
            assertThat(attrs).containsEntry("promql", "test_promql");
            assertThat(attrs).containsEntry("unit", "seconds");
            assertThat(attrs).containsKey("threshold");
            assertThat(attrs).containsKey("value");
            assertThat(attrs).containsEntry("service", "payment-service");
            assertThat(attrs).containsEntry("namespace", "demo");
        }
    }
}
