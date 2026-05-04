package ai.sreagent.prometheus;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.prometheus.client.FixturePrometheusQueryClient;
import ai.sreagent.prometheus.mapper.PrometheusEvidenceTypes;
import ai.sreagent.prometheus.query.PrometheusQueryType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEvidenceProviderTest {

    private PrometheusEvidenceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PrometheusEvidenceProvider(new FixturePrometheusQueryClient());
    }

    @Nested
    class HealthCheck {

        @Test
        void shouldReportHealthy() {
            assertThat(provider.isHealthy()).isTrue();
        }

        @Test
        void shouldReportFixtureClientName() {
            assertThat(provider.clientName()).isEqualTo("fixture");
        }
    }

    @Nested
    class CollectEvidence {

        @Test
        void shouldCollectLatencyEvidence() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-001")
                    .service("payment-service")
                    .namespace("demo")
                    .startTime(Instant.parse("2024-04-28T10:00:00Z"))
                    .endTime(Instant.parse("2024-04-28T10:30:00Z"))
                    .queryTypes(List.of(PrometheusQueryType.LATENCY_P95))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.incidentId()).isEqualTo("inc-001");
            assertThat(result.evidence()).isNotEmpty();
            assertThat(result.evidence().get(0).source()).isEqualTo("prometheus");
            assertThat(result.evidenceCount()).isGreaterThan(0);
        }

        @Test
        void shouldCollectMultipleQueryTypes() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-002")
                    .service("payment-service")
                    .namespace("demo")
                    .queryTypes(List.of(
                            PrometheusQueryType.ERROR_RATE,
                            PrometheusQueryType.LATENCY_P95,
                            PrometheusQueryType.MEMORY_USAGE,
                            PrometheusQueryType.RESTART_RATE))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.evidenceCount()).isGreaterThan(0);
            assertThat(result.rawSummary()).containsEntry("queryCount", 4);
        }

        @Test
        void shouldCollectErrorRateEvidence() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-003")
                    .service("payment-service")
                    .namespace("demo")
                    .queryTypes(List.of(PrometheusQueryType.ERROR_RATE))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();
            boolean hasErrorRateSpike = result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(PrometheusEvidenceTypes.METRIC_ERROR_RATE_SPIKE));
            assertThat(hasErrorRateSpike).isTrue();
        }

        @Test
        void shouldCollectRestartRateEvidence() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-004")
                    .service("payment-service")
                    .namespace("demo")
                    .queryTypes(List.of(PrometheusQueryType.RESTART_RATE))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();
            boolean hasRestart = result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(PrometheusEvidenceTypes.METRIC_RESTART_RATE_INCREASED));
            assertThat(hasRestart).isTrue();
        }

        @Test
        void shouldCollectDownstreamLatencyEvidence() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-005")
                    .service("order-service")
                    .namespace("demo")
                    .queryTypes(List.of(PrometheusQueryType.DOWNSTREAM_LATENCY_P95))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();
            boolean hasDownstream = result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(PrometheusEvidenceTypes.METRIC_DOWNSTREAM_LATENCY_SPIKE));
            assertThat(hasDownstream).isTrue();
        }

        @Test
        void shouldUseDefaultsWhenNoQueryTypesSpecified() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-006")
                    .service("payment-service")
                    .namespace("demo")
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            // Default query types are ERROR_RATE, LATENCY_P95, MEMORY_USAGE, RESTART_RATE
            assertThat(result.rawSummary()).containsEntry("queryCount", 4);
        }
    }

    @Nested
    class RawSummary {

        @Test
        void shouldIncludeMetadataInSummary() {
            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("inc-007")
                    .service("payment-service")
                    .namespace("demo")
                    .queryTypes(List.of(PrometheusQueryType.LATENCY_P95))
                    .build();

            PrometheusEvidenceResult result = provider.collect(request);

            assertThat(result.rawSummary()).containsEntry("reader", "fixture");
            assertThat(result.rawSummary()).containsEntry("service", "payment-service");
            assertThat(result.rawSummary()).containsEntry("namespace", "demo");
            assertThat(result.rawSummary()).containsKey("evidenceTypes");
        }
    }
}
