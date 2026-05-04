package ai.sreagent.core.evidence;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EvidenceNormalizerTest {

    private static final Instant FIXED_TS = Instant.parse("2025-01-01T00:00:00Z");

    private Evidence makeEvidence(String id, String source, String evidenceType,
                                   String service, double strength,
                                   Map<String, Object> attributes) {
        return new Evidence(
                id, "incident-1", source, evidenceType, service,
                FIXED_TS, "content-" + id, attributes, strength
        );
    }

    @Nested
    @DisplayName("Single evidence normalization")
    class SingleNormalization {

        @Test
        @DisplayName("Prometheus metric evidence normalizes correctly")
        void prometheusEvidence() {
            Evidence e = makeEvidence("e1", "prometheus", "metric_latency_p95_spike",
                    "payment-service", 0.90, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.METRIC);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.LATENCY_SPIKE);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.PROMETHEUS);
            assertThat(n.severity()).isEqualTo(EvidenceSeverity.CRITICAL);
            assertThat(n.service()).isEqualTo("payment-service");
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.SYMPTOM);
            assertThat(n.originalEvidenceType()).isEqualTo("metric_latency_p95_spike");
            assertThat(n.strength()).isEqualTo(0.90);
            assertThat(n.timestamp()).isEqualTo(FIXED_TS);
            assertThat(n.content()).isEqualTo("content-e1");
        }

        @Test
        @DisplayName("Kubernetes evidence normalizes correctly")
        void kubernetesEvidence() {
            Evidence e = makeEvidence("e2", "kubernetes", "container_crash_loop_backoff",
                    "api-gateway", 0.95, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.KUBERNETES);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.CRASH_LOOP);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.KUBERNETES);
            assertThat(n.severity()).isEqualTo(EvidenceSeverity.CRITICAL);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("Loki log evidence normalizes correctly")
        void lokiEvidence() {
            Evidence e = makeEvidence("e3", "loki", "log_downstream_timeout",
                    "order-service", 0.75, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.LOG);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.DOWNSTREAM_LATENCY);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.LOKI);
            assertThat(n.severity()).isEqualTo(EvidenceSeverity.WARNING);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("Alertmanager evidence normalizes correctly")
        void alertEvidence() {
            Evidence e = makeEvidence("e4", "alertmanager", "alert_firing",
                    "payment-service", 0.80, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.ALERT);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.ALERT_FIRING);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.ALERTMANAGER);
            assertThat(n.severity()).isEqualTo(EvidenceSeverity.WARNING);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.SYMPTOM);
        }

        @Test
        @DisplayName("Trace evidence normalizes correctly")
        void traceEvidence() {
            Evidence e = makeEvidence("e5", "tracing", "trace_downstream_span_slow",
                    "cart-service", 0.85, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.TRACE);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.DOWNSTREAM_LATENCY);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.TRACE);
            assertThat(n.severity()).isEqualTo(EvidenceSeverity.CRITICAL);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("Unknown evidenceType maps to UNKNOWN; sourceKind inferred from source string")
        void unknownEvidenceType() {
            Evidence e = makeEvidence("e6", "prometheus", "totally_unknown_type",
                    "svc", 0.50, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.UNKNOWN);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.UNKNOWN);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.PROMETHEUS);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.UNKNOWN);
        }

        @Test
        @DisplayName("null evidenceType does not crash, maps to UNKNOWN")
        void nullEvidenceType() {
            Evidence e = new Evidence("e7", "incident-1", "prometheus", null,
                    "svc", FIXED_TS, "content", Map.of(), 0.50);

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.category()).isEqualTo(EvidenceCategory.UNKNOWN);
            assertThat(n.signal()).isEqualTo(EvidenceSignal.UNKNOWN);
            assertThat(n.sourceKind()).isEqualTo(EvidenceSourceKind.PROMETHEUS);
            assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.UNKNOWN);
            assertThat(n.originalEvidenceType()).isNull();
        }
    }

    @Nested
    @DisplayName("Batch normalization")
    class BatchNormalization {

        @Test
        @DisplayName("normalizeAll with 3 evidences returns 3 NormalizedEvidence")
        void normalizeAllReturnsCorrectCount() {
            List<Evidence> evidences = List.of(
                    makeEvidence("a1", "prometheus", "metric_latency_p95_spike", "svc-a", 0.90, Map.of()),
                    makeEvidence("a2", "loki", "log_downstream_timeout", "svc-b", 0.75, Map.of()),
                    makeEvidence("a3", "alertmanager", "alert_firing", "svc-c", 0.80, Map.of())
            );

            List<NormalizedEvidence> result = EvidenceNormalizer.normalizeAll(evidences);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).signal()).isEqualTo(EvidenceSignal.LATENCY_SPIKE);
            assertThat(result.get(1).signal()).isEqualTo(EvidenceSignal.DOWNSTREAM_LATENCY);
            assertThat(result.get(2).signal()).isEqualTo(EvidenceSignal.ALERT_FIRING);
        }

        @Test
        @DisplayName("normalizeAll with null returns empty list")
        void normalizeAllNullReturnsEmpty() {
            List<NormalizedEvidence> result = EvidenceNormalizer.normalizeAll(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Attributes and namespace handling")
    class AttributesHandling {

        @Test
        @DisplayName("Attributes are preserved in normalized evidence")
        void attributesPreserved() {
            Map<String, Object> attrs = Map.of(
                    "region", "us-east-1",
                    "env", "production",
                    "statusCode", 500
            );
            Evidence e = makeEvidence("e10", "prometheus", "metric_latency_p95_spike",
                    "svc", 0.90, attrs);

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.attributes())
                    .containsEntry("region", "us-east-1")
                    .containsEntry("env", "production")
                    .containsEntry("statusCode", 500);
        }

        @Test
        @DisplayName("Namespace extracted from attributes when present")
        void namespaceExtractedFromAttributes() {
            Map<String, Object> attrs = Map.of("namespace", "production");
            Evidence e = makeEvidence("e11", "kubernetes", "container_crash_loop_backoff",
                    "svc", 0.95, attrs);

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.namespace()).isEqualTo("production");
        }

        @Test
        @DisplayName("Namespace is null when not in attributes")
        void namespaceNullWhenAbsent() {
            Evidence e = makeEvidence("e12", "kubernetes", "container_crash_loop_backoff",
                    "svc", 0.95, Map.of());

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.namespace()).isNull();
        }

        @Test
        @DisplayName("Null attributes in Evidence produce empty map in NormalizedEvidence")
        void nullAttributesProduceEmptyMap() {
            Evidence e = new Evidence("e13", "incident-1", "prometheus",
                    "metric_latency_p95_spike", "svc", FIXED_TS, "content",
                    null, 0.90);

            NormalizedEvidence n = EvidenceNormalizer.normalize(e);

            assertThat(n.attributes()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Null evidence rejection")
    class NullRejection {

        @Test
        @DisplayName("normalize(null) throws NullPointerException")
        void normalizeNullThrows() {
            assertThatThrownBy(() -> EvidenceNormalizer.normalize(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("evidence must not be null");
        }
    }
}
