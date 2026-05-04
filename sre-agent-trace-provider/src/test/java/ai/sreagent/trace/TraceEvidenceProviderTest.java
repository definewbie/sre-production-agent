package ai.sreagent.trace;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.trace.client.FixtureTraceQueryClient;
import ai.sreagent.trace.mapper.TraceEvidenceTypes;
import ai.sreagent.trace.query.TraceQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEvidenceProviderTest {

    private FixtureTraceQueryClient fixtureClient;
    private TraceEvidenceProvider provider;

    private static final String INCIDENT_ID = "inc-provider-001";
    private static final String SERVICE = "order-service";
    private static final String NAMESPACE = "production";
    private static final Instant END = Instant.parse("2025-06-01T12:00:00Z");
    private static final Instant START = Instant.parse("2025-06-01T11:30:00Z");

    @BeforeEach
    void setUp() {
        fixtureClient = new FixtureTraceQueryClient();
        provider = new TraceEvidenceProvider(fixtureClient);
    }

    private TraceEvidenceRequest.Builder baseRequest() {
        return TraceEvidenceRequest.builder()
                .incidentId(INCIDENT_ID)
                .service(SERVICE)
                .namespace(NAMESPACE)
                .startTime(START)
                .endTime(END);
    }

    @Nested
    @DisplayName("collect with DOWNSTREAM_SLOW_SPAN")
    class DownstreamSlowSpan {

        @Test
        @DisplayName("should return evidence with source=tracing")
        void shouldReturnTracingSource() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.DOWNSTREAM_SLOW_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();
            assertThat(result.evidence().getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("should return downstream slow span evidence type")
        void shouldReturnDownstreamSlowEvidence() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.DOWNSTREAM_SLOW_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("collect with ERROR_SPAN")
    class ErrorSpan {

        @Test
        @DisplayName("should return error span evidence")
        void shouldReturnErrorSpanEvidence() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.ERROR_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();

            assertThat(result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_ERROR_SPAN)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("collect with empty fixture")
    class EmptyFixture {

        @Test
        @DisplayName("should return trace_no_signal evidence")
        void shouldReturnNoSignal() {
            fixtureClient.setFixtureName("empty_trace.json");

            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.ERROR_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result.evidence().stream()
                    .anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_NO_SIGNAL)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("collect with all query types")
    class AllQueryTypes {

        @Test
        @DisplayName("should return multiple evidence items")
        void shouldReturnMultipleEvidence() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(
                            TraceQueryType.DOWNSTREAM_SLOW_SPAN,
                            TraceQueryType.ERROR_SPAN,
                            TraceQueryType.ROOT_SPAN_SLOW,
                            TraceQueryType.DEPENDENCY_PATH,
                            TraceQueryType.TIMEOUT_SPAN
                    ))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.evidence()).hasSizeGreaterThanOrEqualTo(5);

            Set<String> evidenceTypes = result.evidence().stream()
                    .map(Evidence::evidenceType)
                    .collect(Collectors.toSet());

            assertThat(evidenceTypes).contains(
                    TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW,
                    TraceEvidenceTypes.TRACE_ERROR_SPAN,
                    TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW,
                    TraceEvidenceTypes.TRACE_DEPENDENCY_PATH,
                    TraceEvidenceTypes.TRACE_TIMEOUT_SPAN
            );
        }
    }

    @Nested
    @DisplayName("provider uses FixtureTraceQueryClient correctly")
    class FixtureClientUsage {

        @Test
        @DisplayName("clientName should return 'fixture'")
        void shouldReturnFixtureClientName() {
            assertThat(provider.clientName()).isEqualTo("fixture");
        }

        @Test
        @DisplayName("isHealthy should return true for fixture client")
        void shouldBeHealthy() {
            assertThat(provider.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("rawSummary should contain 'reader' key = fixture")
        void rawSummaryShouldContainReader() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.ERROR_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.rawSummary()).containsEntry("reader", "fixture");
        }
    }

    @Nested
    @DisplayName("rawSummary contains correct keys")
    class RawSummaryKeys {

        @Test
        @DisplayName("should contain reader, service, namespace, queryCount, evidenceCount, evidenceTypes")
        void shouldContainAllExpectedKeys() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.ERROR_SPAN, TraceQueryType.TIMEOUT_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.rawSummary()).containsKey("reader");
            assertThat(result.rawSummary()).containsKey("service");
            assertThat(result.rawSummary()).containsKey("namespace");
            assertThat(result.rawSummary()).containsKey("queryCount");
            assertThat(result.rawSummary()).containsKey("evidenceCount");
            assertThat(result.rawSummary()).containsKey("evidenceTypes");

            assertThat(result.rawSummary()).containsEntry("service", SERVICE);
            assertThat(result.rawSummary()).containsEntry("namespace", NAMESPACE);
            assertThat(result.rawSummary()).containsEntry("queryCount", 2);
        }

        @Test
        @DisplayName("evidenceCount should match evidence list size")
        void evidenceCountShouldMatch() {
            TraceEvidenceRequest request = baseRequest()
                    .queryTypes(List.of(TraceQueryType.ERROR_SPAN))
                    .build();

            TraceEvidenceResult result = provider.collect(request);

            assertThat(result.rawSummary()).containsEntry("evidenceCount", result.evidence().size());
        }
    }
}
