package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class KubernetesEvidenceProviderTest {

    private KubernetesEvidenceProvider provider;
    private FixtureKubernetesResourceReader fixtureReader;

    @BeforeEach
    void setUp() {
        fixtureReader = new FixtureKubernetesResourceReader();
        provider = new KubernetesEvidenceProvider(fixtureReader);
    }

    private IncidentTask testIncident() {
        return new IncidentTask(
            "inc-test-001",
            "PodCrashLooping",
            "payment-service",
            "production",
            "critical",
            Instant.now(),
            Map.of("app", "payment-service"),
            Map.of()
        );
    }

    @Nested
    @DisplayName("Evidence collection")
    class CollectEvidence {
        @Test
        @DisplayName("should collect evidence from fixtures")
        void collectFromFixtures() throws Exception {
            List<Evidence> evidence = provider.collectEvidence(testIncident());

            assertThat(evidence).isNotEmpty();
            assertThat(evidence).allMatch(e -> "kubernetes".equals(e.source()));
            assertThat(evidence).anyMatch(e -> "k8s_pod_status".equals(e.evidenceType()));
        }

        @Test
        @DisplayName("should include pod evidence with container status")
        void includesPodEvidence() throws Exception {
            List<Evidence> evidence = provider.collectEvidence(testIncident());

            Evidence podEvidence = evidence.stream()
                .filter(e -> "k8s_pod_status".equals(e.evidenceType()))
                .findFirst().orElse(null);

            assertThat(podEvidence).isNotNull();
            assertThat(podEvidence.content()).isNotEmpty();
            assertThat(podEvidence.attributes()).containsKey("restartCount");
        }

        @Test
        @DisplayName("should include event evidence")
        void includesEventEvidence() throws Exception {
            List<Evidence> evidence = provider.collectEvidence(testIncident());

            List<Evidence> eventEvidence = evidence.stream()
                .filter(e -> "k8s_event".equals(e.evidenceType()))
                .toList();

            assertThat(eventEvidence).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Fault detection")
    class FaultDetection {
        @Test
        @DisplayName("should detect faults from pod status")
        void detectFaultsFromPod() throws Exception {
            List<KubernetesFaultMode> faults = provider.detectFaults("production", "payment-service");

            assertThat(faults).isNotEmpty();
            assertThat(faults).anyMatch(f -> f == KubernetesFaultMode.CRASH_LOOP_BACK_OFF);
        }
    }

    @Nested
    @DisplayName("Health check")
    class HealthCheck {
        @Test
        @DisplayName("should report healthy with fixture reader")
        void healthyWithFixture() {
            assertThat(provider.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should report fixture reader name")
        void readerName() {
            assertThat(provider.readerName()).isEqualTo("fixture");
        }
    }
}
