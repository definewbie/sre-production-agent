package ai.sreagent.core.evidence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class NormalizedEvidenceViewTest {

    private static final Instant FIXED_TS = Instant.parse("2025-01-01T00:00:00Z");

    private NormalizedEvidence metricSymptom;
    private NormalizedEvidence traceCause;
    private NormalizedEvidence alertSymptom;
    private NormalizedEvidence traceTopology;
    private NormalizedEvidenceView view;

    private NormalizedEvidence makeNormalized(String id, EvidenceCategory category,
                                               EvidenceSignal signal,
                                               EvidenceCausalRole causalRole) {
        return new NormalizedEvidence(
                id, id, category, signal, EvidenceSourceKind.UNKNOWN,
                EvidenceSeverity.INFO, causalRole, "entity-" + id, "svc-" + id,
                null, 0.50, FIXED_TS, "content", Map.of()
        );
    }

    @BeforeEach
    void setUp() {
        // METRIC + LATENCY_SPIKE + SYMPTOM
        metricSymptom = makeNormalized("metric-1", EvidenceCategory.METRIC,
                EvidenceSignal.LATENCY_SPIKE, EvidenceCausalRole.SYMPTOM);

        // TRACE + DOWNSTREAM_LATENCY + CAUSE_CANDIDATE
        traceCause = makeNormalized("trace-1", EvidenceCategory.TRACE,
                EvidenceSignal.DOWNSTREAM_LATENCY, EvidenceCausalRole.CAUSE_CANDIDATE);

        // ALERT + ALERT_FIRING + SYMPTOM
        alertSymptom = makeNormalized("alert-1", EvidenceCategory.ALERT,
                EvidenceSignal.ALERT_FIRING, EvidenceCausalRole.SYMPTOM);

        // TRACE + DEPENDENCY_PATH + TOPOLOGY_CONTEXT
        traceTopology = makeNormalized("trace-2", EvidenceCategory.TRACE,
                EvidenceSignal.DEPENDENCY_PATH, EvidenceCausalRole.TOPOLOGY_CONTEXT);

        view = new NormalizedEvidenceView(List.of(metricSymptom, traceCause, alertSymptom, traceTopology));
    }

    @Nested
    @DisplayName("hasSignal checks")
    class HasSignalChecks {

        @Test
        @DisplayName("hasSignal(LATENCY_SPIKE) returns true")
        void hasSignalTrue() {
            assertThat(view.hasSignal(EvidenceSignal.LATENCY_SPIKE)).isTrue();
        }

        @Test
        @DisplayName("hasSignal(OOM) returns false")
        void hasSignalFalse() {
            assertThat(view.hasSignal(EvidenceSignal.OOM)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasCategory checks")
    class HasCategoryChecks {

        @Test
        @DisplayName("hasCategory(METRIC) returns true")
        void hasCategoryTrue() {
            assertThat(view.hasCategory(EvidenceCategory.METRIC)).isTrue();
        }

        @Test
        @DisplayName("hasCategory(DEPLOYMENT) returns false")
        void hasCategoryFalse() {
            assertThat(view.hasCategory(EvidenceCategory.DEPLOYMENT)).isFalse();
        }
    }

    @Nested
    @DisplayName("Filtering by signal, category, and causal role")
    class Filtering {

        @Test
        @DisplayName("bySignal(LATENCY_SPIKE) returns correct subset")
        void bySignal() {
            List<NormalizedEvidence> result = view.bySignal(EvidenceSignal.LATENCY_SPIKE);

            assertThat(result).hasSize(1)
                    .allSatisfy(n -> assertThat(n.signal()).isEqualTo(EvidenceSignal.LATENCY_SPIKE));
        }

        @Test
        @DisplayName("byCategory(ALERT) returns correct subset")
        void byCategory() {
            List<NormalizedEvidence> result = view.byCategory(EvidenceCategory.ALERT);

            assertThat(result).hasSize(1)
                    .allSatisfy(n -> assertThat(n.category()).isEqualTo(EvidenceCategory.ALERT));
        }

        @Test
        @DisplayName("byCausalRole(CAUSE_CANDIDATE) returns correct subset")
        void byCausalRole() {
            List<NormalizedEvidence> result = view.byCausalRole(EvidenceCausalRole.CAUSE_CANDIDATE);

            assertThat(result).hasSize(1)
                    .allSatisfy(n -> assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE));
        }
    }

    @Nested
    @DisplayName("Convenience accessors")
    class ConvenienceAccessors {

        @Test
        @DisplayName("causeCandidates() returns CAUSE_CANDIDATE items")
        void causeCandidates() {
            List<NormalizedEvidence> result = view.causeCandidates();

            assertThat(result).hasSize(1)
                    .allSatisfy(n -> assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.CAUSE_CANDIDATE));
            assertThat(result.get(0).originalEvidenceType()).isEqualTo("trace-1");
        }

        @Test
        @DisplayName("symptoms() returns SYMPTOM items")
        void symptoms() {
            List<NormalizedEvidence> result = view.symptoms();

            assertThat(result).hasSize(2)
                    .allSatisfy(n -> assertThat(n.causalRole()).isEqualTo(EvidenceCausalRole.SYMPTOM));
        }

        @Test
        @DisplayName("counterSignals() returns empty when no counter signals")
        void counterSignalsEmpty() {
            List<NormalizedEvidence> result = view.counterSignals();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Size and all()")
    class SizeAndAll {

        @Test
        @DisplayName("size() returns correct count")
        void size() {
            assertThat(view.size()).isEqualTo(4);
        }

        @Test
        @DisplayName("all() returns the full list")
        void all() {
            assertThat(view.all()).hasSize(4)
                    .containsExactly(metricSymptom, traceCause, alertSymptom, traceTopology);
        }
    }

    @Nested
    @DisplayName("Null and empty handling")
    class NullHandling {

        @Test
        @DisplayName("Constructing with null produces empty view")
        void nullConstructor() {
            NormalizedEvidenceView nullView = new NormalizedEvidenceView(null);

            assertThat(nullView.size()).isZero();
            assertThat(nullView.all()).isEmpty();
        }
    }
}
