package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KubernetesEvidenceMapperTest {

    private KubernetesEvidenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KubernetesEvidenceMapper();
    }

    @Nested
    @DisplayName("Pod to Evidence mapping")
    class PodMapping {
        @Test
        @DisplayName("should map ParsedPod to Evidence with correct fields")
        void mapPod() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "my-pod", "production", "Running", "app", 5,
                "CrashLoopBackOff", "OOMKilled", 137
            );

            Evidence evidence = mapper.mapPodToEvidence(pod, "inc-001");

            assertThat(evidence.source()).isEqualTo("kubernetes");
            assertThat(evidence.evidenceType()).isEqualTo("k8s_pod_status");
            assertThat(evidence.service()).isEqualTo("my-pod");
            assertThat(evidence.incidentId()).isEqualTo("inc-001");
            assertThat(evidence.strength()).isGreaterThan(0);
            assertThat(evidence.content()).contains("my-pod").contains("CrashLoopBackOff");
            assertThat(evidence.attributes()).containsEntry("restartCount", 5);
            assertThat(evidence.attributes()).containsEntry("terminatedReason", "OOMKilled");
        }
    }

    @Nested
    @DisplayName("Deployment to Evidence mapping")
    class DeploymentMapping {
        @Test
        @DisplayName("should map ParsedDeployment to Evidence")
        void mapDeployment() {
            KubernetesJsonParser.ParsedDeployment dep = new KubernetesJsonParser.ParsedDeployment(
                "my-service", "production", 3, 2, 2, 3
            );

            Evidence evidence = mapper.mapDeploymentToEvidence(dep, "inc-001");

            assertThat(evidence.source()).isEqualTo("kubernetes");
            assertThat(evidence.evidenceType()).isEqualTo("k8s_deployment_status");
            assertThat(evidence.content()).contains("2/3").contains("DEGRADED");
            assertThat(evidence.attributes()).containsEntry("degraded", true);
        }

        @Test
        @DisplayName("should not mark as degraded when all replicas ready")
        void healthyDeployment() {
            KubernetesJsonParser.ParsedDeployment dep = new KubernetesJsonParser.ParsedDeployment(
                "my-service", "production", 3, 3, 3, 3
            );

            Evidence evidence = mapper.mapDeploymentToEvidence(dep, "inc-001");
            assertThat(evidence.attributes()).containsEntry("degraded", false);
            assertThat(evidence.content()).doesNotContain("DEGRADED");
        }
    }

    @Nested
    @DisplayName("Events to Evidence mapping")
    class EventMapping {
        @Test
        @DisplayName("should map events with Warning type having higher strength")
        void mapEvents() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("evt1", "BackOff", "Back-off restarting", "Warning", "my-pod", "2025-01-01T00:00:00Z", 5),
                new KubernetesJsonParser.ParsedEvent("evt2", "Normal", "Started container", "Normal", "my-pod", "2025-01-01T00:01:00Z", 1)
            );

            List<Evidence> evidenceList = mapper.mapEventsToEvidence(events, "inc-001");

            assertThat(evidenceList).hasSize(2);
            Evidence warning = evidenceList.getFirst();
            assertThat(warning.strength()).isGreaterThan(0.7);
            assertThat(warning.evidenceType()).isEqualTo("k8s_event");
        }
    }
}
