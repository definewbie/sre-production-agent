package ai.sreagent.core.evidence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EvidenceTaxonomyRegistryTest {

    @Nested
    @DisplayName("Known type lookups")
    class KnownTypeLookups {

        @Test
        @DisplayName("container_crash_loop_backoff → KUBERNETES / CRASH_LOOP / KUBERNETES / CAUSE_CANDIDATE")
        void crashLoopBackoff() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("container_crash_loop_backoff"))
                    .isEqualTo(EvidenceCategory.KUBERNETES);
            assertThat(EvidenceTaxonomyRegistry.getSignal("container_crash_loop_backoff"))
                    .isEqualTo(EvidenceSignal.CRASH_LOOP);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("container_crash_loop_backoff", null))
                    .isEqualTo(EvidenceSourceKind.KUBERNETES);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("container_crash_loop_backoff"))
                    .isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("metric_latency_p95_spike → METRIC / LATENCY_SPIKE / PROMETHEUS / SYMPTOM")
        void metricLatencyP95Spike() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("metric_latency_p95_spike"))
                    .isEqualTo(EvidenceCategory.METRIC);
            assertThat(EvidenceTaxonomyRegistry.getSignal("metric_latency_p95_spike"))
                    .isEqualTo(EvidenceSignal.LATENCY_SPIKE);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("metric_latency_p95_spike", null))
                    .isEqualTo(EvidenceSourceKind.PROMETHEUS);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("metric_latency_p95_spike"))
                    .isEqualTo(EvidenceCausalRole.SYMPTOM);
        }

        @Test
        @DisplayName("log_downstream_timeout → LOG / DOWNSTREAM_LATENCY / LOKI / CAUSE_CANDIDATE")
        void logDownstreamTimeout() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("log_downstream_timeout"))
                    .isEqualTo(EvidenceCategory.LOG);
            assertThat(EvidenceTaxonomyRegistry.getSignal("log_downstream_timeout"))
                    .isEqualTo(EvidenceSignal.DOWNSTREAM_LATENCY);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("log_downstream_timeout", null))
                    .isEqualTo(EvidenceSourceKind.LOKI);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("log_downstream_timeout"))
                    .isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("trace_downstream_span_slow → TRACE / DOWNSTREAM_LATENCY / TRACE / CAUSE_CANDIDATE")
        void traceDownstreamSpanSlow() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("trace_downstream_span_slow"))
                    .isEqualTo(EvidenceCategory.TRACE);
            assertThat(EvidenceTaxonomyRegistry.getSignal("trace_downstream_span_slow"))
                    .isEqualTo(EvidenceSignal.DOWNSTREAM_LATENCY);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("trace_downstream_span_slow", null))
                    .isEqualTo(EvidenceSourceKind.TRACE);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("trace_downstream_span_slow"))
                    .isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE);
        }

        @Test
        @DisplayName("alert_firing → ALERT / ALERT_FIRING / ALERTMANAGER / SYMPTOM")
        void alertFiring() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("alert_firing"))
                    .isEqualTo(EvidenceCategory.ALERT);
            assertThat(EvidenceTaxonomyRegistry.getSignal("alert_firing"))
                    .isEqualTo(EvidenceSignal.ALERT_FIRING);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("alert_firing", null))
                    .isEqualTo(EvidenceSourceKind.ALERTMANAGER);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("alert_firing"))
                    .isEqualTo(EvidenceCausalRole.SYMPTOM);
        }

        @Test
        @DisplayName("trace_dependency_path → TRACE / DEPENDENCY_PATH / TRACE / TOPOLOGY_CONTEXT")
        void traceDependencyPath() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("trace_dependency_path"))
                    .isEqualTo(EvidenceCategory.TRACE);
            assertThat(EvidenceTaxonomyRegistry.getSignal("trace_dependency_path"))
                    .isEqualTo(EvidenceSignal.DEPENDENCY_PATH);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("trace_dependency_path", null))
                    .isEqualTo(EvidenceSourceKind.TRACE);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("trace_dependency_path"))
                    .isEqualTo(EvidenceCausalRole.TOPOLOGY_CONTEXT);
        }

        @Test
        @DisplayName("deployment_metadata → KUBERNETES / DEPLOYMENT_METADATA / KUBERNETES / CONTEXT")
        void deploymentMetadata() {
            assertThat(EvidenceTaxonomyRegistry.getCategory("deployment_metadata"))
                    .isEqualTo(EvidenceCategory.KUBERNETES);
            assertThat(EvidenceTaxonomyRegistry.getSignal("deployment_metadata"))
                    .isEqualTo(EvidenceSignal.DEPLOYMENT_METADATA);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind("deployment_metadata", null))
                    .isEqualTo(EvidenceSourceKind.KUBERNETES);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole("deployment_metadata"))
                    .isEqualTo(EvidenceCausalRole.CONTEXT);
        }
    }

    @Nested
    @DisplayName("Unknown type handling")
    class UnknownTypeHandling {

        @Test
        @DisplayName("Unknown type returns UNKNOWN for category, signal, role; source inferred from source string")
        void unknownTypeReturnsUnknown() {
            String unknownType = "some_completely_unknown_type_xyz";
            assertThat(EvidenceTaxonomyRegistry.getCategory(unknownType))
                    .isEqualTo(EvidenceCategory.UNKNOWN);
            assertThat(EvidenceTaxonomyRegistry.getSignal(unknownType))
                    .isEqualTo(EvidenceSignal.UNKNOWN);
            assertThat(EvidenceTaxonomyRegistry.getCausalRole(unknownType))
                    .isEqualTo(EvidenceCausalRole.UNKNOWN);
            // Source kind inferred from the source string
            assertThat(EvidenceTaxonomyRegistry.getSourceKind(unknownType, "prometheus"))
                    .isEqualTo(EvidenceSourceKind.PROMETHEUS);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind(unknownType, "loki"))
                    .isEqualTo(EvidenceSourceKind.LOKI);
            assertThat(EvidenceTaxonomyRegistry.getSourceKind(unknownType, "kubernetes"))
                    .isEqualTo(EvidenceSourceKind.KUBERNETES);
        }
    }

    @Nested
    @DisplayName("Registry metadata")
    class RegistryMetadata {

        @Test
        @DisplayName("isRegistered returns true for known types")
        void isRegisteredTrueForKnown() {
            assertThat(EvidenceTaxonomyRegistry.isRegistered("container_crash_loop_backoff")).isTrue();
            assertThat(EvidenceTaxonomyRegistry.isRegistered("metric_latency_p95_spike")).isTrue();
            assertThat(EvidenceTaxonomyRegistry.isRegistered("alert_firing")).isTrue();
        }

        @Test
        @DisplayName("isRegistered returns false for unknown types")
        void isRegisteredFalseForUnknown() {
            assertThat(EvidenceTaxonomyRegistry.isRegistered("nonexistent_type_xyz")).isFalse();
        }

        @Test
        @DisplayName("isRegistered returns false for null")
        void isRegisteredFalseForNull() {
            assertThat(EvidenceTaxonomyRegistry.isRegistered(null)).isFalse();
        }

        @Test
        @DisplayName("registeredTypes() returns non-empty set")
        void registeredTypesNonEmpty() {
            assertThat(EvidenceTaxonomyRegistry.registeredTypes()).isNotEmpty();
        }

        @Test
        @DisplayName("size() is greater than 50")
        void sizeGreaterThan50() {
            assertThat(EvidenceTaxonomyRegistry.size()).isGreaterThan(50);
        }
    }
}
