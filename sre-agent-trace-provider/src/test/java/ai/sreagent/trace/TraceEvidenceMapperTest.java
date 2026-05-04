package ai.sreagent.trace;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.trace.mapper.TraceEvidenceMapper;
import ai.sreagent.trace.mapper.TraceEvidenceTypes;
import ai.sreagent.trace.parser.ParsedTrace;
import ai.sreagent.trace.parser.TraceResponseParser;
import ai.sreagent.trace.query.TraceQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEvidenceMapperTest {

    private TraceResponseParser parser;
    private TraceEvidenceMapper mapper;

    private static final String INCIDENT_ID = "inc-mapper-001";
    private static final String SERVICE = "order-service";
    private static final String NAMESPACE = "production";
    private static final Instant START = Instant.parse("2025-05-01T10:00:00Z");
    private static final Instant END = Instant.parse("2025-05-01T10:30:00Z");

    @BeforeEach
    void setUp() {
        parser = new TraceResponseParser();
        mapper = new TraceEvidenceMapper();
    }

    private String loadFixture(String name) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/trace/" + name)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fixture: " + name, e);
        }
    }

    private List<Evidence> mapFixture(String fixtureName, TraceQueryType queryType) {
        String json = loadFixture(fixtureName);
        List<ParsedTrace> traces = parser.parse(json);
        return mapper.map(queryType, traces, INCIDENT_ID, SERVICE, NAMESPACE, START, END);
    }

    // --- Fixture-based evidence mapping tests ---

    @Nested
    @DisplayName("downstream_slow_span fixture")
    class DownstreamSlowSpan {

        @Test
        @DisplayName("should map to trace_downstream_span_slow evidence")
        void shouldMapToDownstreamSpanSlow() {
            List<Evidence> evidence = mapFixture("downstream_slow_span.json", TraceQueryType.DOWNSTREAM_SLOW_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW)))
                    .isTrue();
        }

        @Test
        @DisplayName("evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("downstream_slow_span.json", TraceQueryType.DOWNSTREAM_SLOW_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("evidence should have reasonable strength")
        void shouldHaveReasonableStrength() {
            List<Evidence> evidence = mapFixture("downstream_slow_span.json", TraceQueryType.DOWNSTREAM_SLOW_SPAN);

            Evidence downstreamEvidence = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW))
                    .findFirst().orElseThrow();

            assertThat(downstreamEvidence.strength()).isGreaterThan(0.0);
            assertThat(downstreamEvidence.strength()).isLessThanOrEqualTo(1.0);
            assertThat(downstreamEvidence.strength()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("error_span fixture")
    class ErrorSpan {

        @Test
        @DisplayName("should map to trace_error_span evidence")
        void shouldMapToErrorSpan() {
            List<Evidence> evidence = mapFixture("error_span.json", TraceQueryType.ERROR_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_ERROR_SPAN)))
                    .isTrue();
        }

        @Test
        @DisplayName("evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("error_span.json", TraceQueryType.ERROR_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("error evidence should reference inventory-service")
        void shouldReferenceCorrectService() {
            List<Evidence> evidence = mapFixture("error_span.json", TraceQueryType.ERROR_SPAN);

            Evidence errorEvidence = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_ERROR_SPAN))
                    .findFirst().orElseThrow();

            assertThat(errorEvidence.content()).contains("inventory-service");
        }
    }

    @Nested
    @DisplayName("root_span_slow fixture")
    class RootSpanSlow {

        @Test
        @DisplayName("should map to trace_root_span_slow evidence")
        void shouldMapToRootSpanSlow() {
            List<Evidence> evidence = mapFixture("root_span_slow.json", TraceQueryType.ROOT_SPAN_SLOW);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW)))
                    .isTrue();
        }

        @Test
        @DisplayName("evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("root_span_slow.json", TraceQueryType.ROOT_SPAN_SLOW);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("root span slow evidence should have strength 0.70")
        void shouldHaveCorrectStrength() {
            List<Evidence> evidence = mapFixture("root_span_slow.json", TraceQueryType.ROOT_SPAN_SLOW);

            Evidence rootSlowEvidence = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW))
                    .findFirst().orElseThrow();

            assertThat(rootSlowEvidence.strength()).isEqualTo(0.70);
        }
    }

    @Nested
    @DisplayName("dependency_path_order_payment fixture")
    class DependencyPath {

        @Test
        @DisplayName("should map to trace_dependency_path evidence")
        void shouldMapToDependencyPath() {
            // Service is "order-service", child spans are from payment-service and notification-service
            List<Evidence> evidence = mapFixture("dependency_path_order_payment.json", TraceQueryType.DEPENDENCY_PATH);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_DEPENDENCY_PATH)))
                    .isTrue();
        }

        @Test
        @DisplayName("evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("dependency_path_order_payment.json", TraceQueryType.DEPENDENCY_PATH);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("dependency path evidence should have strength 0.65")
        void shouldHaveCorrectStrength() {
            List<Evidence> evidence = mapFixture("dependency_path_order_payment.json", TraceQueryType.DEPENDENCY_PATH);

            Evidence depEvidence = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_DEPENDENCY_PATH))
                    .findFirst().orElseThrow();

            assertThat(depEvidence.strength()).isEqualTo(0.65);
        }
    }

    @Nested
    @DisplayName("timeout_span fixture")
    class TimeoutSpan {

        @Test
        @DisplayName("should map to trace_timeout_span evidence")
        void shouldMapToTimeoutSpan() {
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_TIMEOUT_SPAN)))
                    .isTrue();
        }

        @Test
        @DisplayName("evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }

        @Test
        @DisplayName("timeout span evidence should have strength 0.85")
        void shouldHaveCorrectStrength() {
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            Evidence timeoutEvidence = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_TIMEOUT_SPAN))
                    .findFirst().orElseThrow();

            assertThat(timeoutEvidence.strength()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("child span dominance detection")
    class ChildSpanDominance {

        @Test
        @DisplayName("should detect child span dominates latency when ratio >= 0.70")
        void shouldDetectChildDominance() {
            // timeout_span.json: root duration=5000ms, child duration=4500ms, ratio=0.9 >= 0.70
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            assertThat(evidence.stream()
                    .anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY)))
                    .isTrue();
        }

        @Test
        @DisplayName("child dominance evidence should have strength 0.90")
        void shouldHaveDominanceStrength() {
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            Evidence dominance = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY))
                    .findFirst().orElseThrow();

            assertThat(dominance.strength()).isEqualTo(0.90);
        }

        @Test
        @DisplayName("child dominance content should mention ratio percentage")
        void shouldMentionRatioInContent() {
            List<Evidence> evidence = mapFixture("timeout_span.json", TraceQueryType.TIMEOUT_SPAN);

            Evidence dominance = evidence.stream()
                    .filter(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY))
                    .findFirst().orElseThrow();

            // ratio = 4500/5000 = 0.9 = 90%
            assertThat(dominance.content()).contains("90%");
        }

        @Test
        @DisplayName("should also detect child dominance in downstream_slow_span (1200ms/1500ms = 0.80)")
        void shouldDetectDominanceInDownstreamSlow() {
            List<Evidence> evidence = mapFixture("downstream_slow_span.json", TraceQueryType.DOWNSTREAM_SLOW_SPAN);

            assertThat(evidence.stream()
                    .anyMatch(e -> e.evidenceType().equals(TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("empty trace mapping")
    class EmptyTrace {

        @Test
        @DisplayName("should map empty traces to trace_no_signal evidence")
        void shouldMapEmptyToNoSignal() {
            List<Evidence> evidence = mapFixture("empty_trace.json", TraceQueryType.ERROR_SPAN);

            assertThat(evidence).hasSize(1);
            assertThat(evidence.getFirst().evidenceType()).isEqualTo(TraceEvidenceTypes.TRACE_NO_SIGNAL);
        }

        @Test
        @DisplayName("no signal evidence should have zero strength")
        void shouldHaveZeroStrength() {
            List<Evidence> evidence = mapFixture("empty_trace.json", TraceQueryType.ERROR_SPAN);

            assertThat(evidence.getFirst().strength()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("no signal evidence should have source = tracing")
        void shouldHaveSourceTracing() {
            List<Evidence> evidence = mapFixture("empty_trace.json", TraceQueryType.ERROR_SPAN);

            assertThat(evidence.getFirst().source()).isEqualTo("tracing");
        }
    }

    @Nested
    @DisplayName("source verification on all evidence types")
    class SourceVerification {

        @Test
        @DisplayName("all evidence from any fixture should have source = tracing")
        void allEvidenceShouldHaveTracingSource() {
            List<String> fixtures = List.of(
                    "downstream_slow_span.json",
                    "error_span.json",
                    "root_span_slow.json",
                    "dependency_path_order_payment.json",
                    "timeout_span.json"
            );

            List<TraceQueryType> queryTypes = List.of(
                    TraceQueryType.DOWNSTREAM_SLOW_SPAN,
                    TraceQueryType.ERROR_SPAN,
                    TraceQueryType.ROOT_SPAN_SLOW,
                    TraceQueryType.DEPENDENCY_PATH,
                    TraceQueryType.TIMEOUT_SPAN
            );

            for (int i = 0; i < fixtures.size(); i++) {
                List<Evidence> evidence = mapFixture(fixtures.get(i), queryTypes.get(i));
                for (Evidence e : evidence) {
                    assertThat(e.source())
                            .as("Evidence from %s should have source 'tracing'", fixtures.get(i))
                            .isEqualTo("tracing");
                }
            }
        }
    }

    @Nested
    @DisplayName("strength values are reasonable")
    class StrengthValues {

        @Test
        @DisplayName("all evidence strength values should be between 0 and 1 inclusive")
        void allStrengthsShouldBeReasonable() {
            String[] fixtures = {
                    "downstream_slow_span.json",
                    "error_span.json",
                    "root_span_slow.json",
                    "dependency_path_order_payment.json",
                    "timeout_span.json"
            };

            TraceQueryType[] types = {
                    TraceQueryType.DOWNSTREAM_SLOW_SPAN,
                    TraceQueryType.ERROR_SPAN,
                    TraceQueryType.ROOT_SPAN_SLOW,
                    TraceQueryType.DEPENDENCY_PATH,
                    TraceQueryType.TIMEOUT_SPAN
            };

            for (int i = 0; i < fixtures.length; i++) {
                List<Evidence> evidence = mapFixture(fixtures[i], types[i]);
                for (Evidence e : evidence) {
                    assertThat(e.strength())
                            .as("Strength from %s should be >= 0", fixtures[i])
                            .isGreaterThanOrEqualTo(0.0);
                    assertThat(e.strength())
                            .as("Strength from %s should be <= 1", fixtures[i])
                            .isLessThanOrEqualTo(1.0);
                }
            }
        }
    }
}
