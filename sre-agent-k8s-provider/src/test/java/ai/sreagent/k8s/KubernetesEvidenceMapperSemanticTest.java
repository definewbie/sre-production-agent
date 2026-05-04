package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KubernetesEvidenceMapperSemanticTest {

    private KubernetesEvidenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KubernetesEvidenceMapper();
    }

    // ─── Test 1: CrashLoopBackOff → container_crash_loop_backoff ───

    @Nested
    @DisplayName("CrashLoopBackOff semantic mapping")
    class CrashLoopBackOffMapping {

        @Test
        @DisplayName("should map CrashLoopBackOff pod to container_crash_loop_backoff evidence")
        void shouldMapCrashLoopBackOffToContainerCrashLoopEvidence() {
            KubernetesJsonParser.ParsedPod pod = crashLoopPod(5, 1);

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-001");

            assertThat(evidence).isNotEmpty();
            Evidence crashLoopEv = evidence.stream()
                    .filter(e -> "container_crash_loop_backoff".equals(e.evidenceType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected container_crash_loop_backoff evidence"));

            assertThat(crashLoopEv.source()).isEqualTo("kubernetes");
            assertThat(crashLoopEv.service()).contains("recommend-service");
            assertThat(crashLoopEv.strength()).isGreaterThanOrEqualTo(0.85);
            assertThat(crashLoopEv.attributes()).containsEntry("reason", "CrashLoopBackOff");
            assertThat(crashLoopEv.content()).contains("CrashLoopBackOff");
            assertThat(crashLoopEv.incidentId()).isEqualTo("inc-001");
        }
    }

    // ─── Test 2: RestartCount → pod_restart_count_increased ───

    @Nested
    @DisplayName("Restart count semantic mapping")
    class RestartCountMapping {

        @Test
        @DisplayName("should map high restart count to pod_restart_count_increased evidence")
        void shouldMapRestartCountToRestartEvidence() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 7,
                "", "Error", 1
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-002");

            Evidence restartEv = evidence.stream()
                    .filter(e -> "pod_restart_count_increased".equals(e.evidenceType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected pod_restart_count_increased evidence"));

            assertThat(restartEv.source()).isEqualTo("kubernetes");
            assertThat(restartEv.strength()).isGreaterThanOrEqualTo(0.80);
            assertThat(restartEv.attributes()).containsEntry("restart_count", 7);
            assertThat(restartEv.content()).contains("restarted 7 times");
        }

        @Test
        @DisplayName("should not produce restart evidence when restartCount < 2")
        void shouldNotProduceRestartEvidenceForLowRestartCount() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 1,
                "", "Error", 1
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-003");

            assertThat(evidence.stream()
                    .anyMatch(e -> "pod_restart_count_increased".equals(e.evidenceType())))
                    .isFalse();
        }
    }

    // ─── Test 3: Not Ready → pod_not_ready ───

    @Nested
    @DisplayName("Pod not ready semantic mapping")
    class PodNotReadyMapping {

        @Test
        @DisplayName("should map not-ready pod to pod_not_ready evidence")
        void shouldMapNotReadyPodToPodNotReadyEvidence() {
            // Pending pod = not running = not ready
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Pending",
                "recommend-service", 0,
                "", "", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-004");

            Evidence notReadyEv = evidence.stream()
                    .filter(e -> "pod_not_ready".equals(e.evidenceType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected pod_not_ready evidence"));

            assertThat(notReadyEv.source()).isEqualTo("kubernetes");
            assertThat(notReadyEv.strength()).isGreaterThanOrEqualTo(0.70);
            assertThat(notReadyEv.attributes()).containsEntry("ready", false);
        }
    }

    // ─── Test 4: Healthy pod → no failure evidence ───

    @Nested
    @DisplayName("Healthy pod produces no failure evidence")
    class HealthyPodMapping {

        @Test
        @DisplayName("should not produce failure evidence for healthy running pod")
        void shouldNotProduceFailureEvidenceForHealthyPod() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 0,
                "", "", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-005");

            assertThat(evidence).isEmpty();
        }
    }

    // ─── Test 5: Multiple signals → multiple evidence items ───

    @Nested
    @DisplayName("Multiple signals from one pod")
    class MultipleSignalsMapping {

        @Test
        @DisplayName("should produce multiple semantic evidence items from one CrashLoopBackOff pod")
        void shouldProduceMultipleSemanticEvidenceItemsFromOnePod() {
            // This is the key live-path scenario: one pod produces 3 semantic evidence items
            KubernetesJsonParser.ParsedPod pod = crashLoopPod(3, 1);

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-006");

            List<String> types = evidence.stream().map(Evidence::evidenceType).toList();

            assertThat(types).containsExactlyInAnyOrder(
                "container_crash_loop_backoff",
                "pod_restart_count_increased",
                "pod_not_ready"
            );
            assertThat(evidence).hasSize(3);
        }
    }

    // ─── Test 6: Deployment metadata mapping ───

    @Nested
    @DisplayName("Deployment metadata semantic mapping")
    class DeploymentMetadataMapping {

        @Test
        @DisplayName("should map degraded deployment to deployment_metadata evidence")
        void shouldMapDeploymentToMetadataEvidence() {
            KubernetesJsonParser.ParsedDeployment dep = new KubernetesJsonParser.ParsedDeployment(
                "recommend-service", "demo", 3, 0, 0, 3
            );

            Evidence evidence = mapper.mapDeploymentToMetadataEvidence(dep, "inc-007");

            assertThat(evidence.evidenceType()).isEqualTo("deployment_metadata");
            assertThat(evidence.source()).isEqualTo("kubernetes");
            assertThat(evidence.service()).isEqualTo("recommend-service");
            assertThat(evidence.attributes()).containsEntry("deployment_name", "recommend-service");
            assertThat(evidence.attributes()).containsEntry("replicas", 3);
            assertThat(evidence.attributes()).containsEntry("ready_replicas", 0);
            assertThat(evidence.content()).contains("DEGRADED");
            assertThat(evidence.strength()).isGreaterThanOrEqualTo(0.70);
        }

        @Test
        @DisplayName("should map healthy deployment to deployment_metadata without DEGRADED")
        void shouldMapHealthyDeploymentToMetadataEvidence() {
            KubernetesJsonParser.ParsedDeployment dep = new KubernetesJsonParser.ParsedDeployment(
                "recommend-service", "demo", 3, 3, 3, 3
            );

            Evidence evidence = mapper.mapDeploymentToMetadataEvidence(dep, "inc-008");

            assertThat(evidence.evidenceType()).isEqualTo("deployment_metadata");
            assertThat(evidence.content()).doesNotContain("DEGRADED");
            assertThat(evidence.attributes()).containsEntry("ready_replicas", 3);
        }
    }

    // ─── Fixture-based integration test ───

    @Nested
    @DisplayName("Fixture-based semantic mapping")
    class FixtureBasedMapping {

        @Test
        @DisplayName("should map fixture CrashLoopBackOff pod to semantic evidence via parser")
        void shouldMapFixturePodToSemanticEvidence() throws Exception {
            String json = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(getClass().getResource("/fixtures/pod-crashloopbackoff.json").toURI())
            ));

            KubernetesJsonParser parser = new KubernetesJsonParser();
            KubernetesJsonParser.ParsedPod pod = parser.parsePod(json);

            assertThat(pod).isNotNull();
            assertThat(pod.waitingReason()).isEqualTo("CrashLoopBackOff");

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-fixture");

            List<String> types = evidence.stream().map(Evidence::evidenceType).toList();
            assertThat(types).contains("container_crash_loop_backoff");
            assertThat(types).contains("pod_restart_count_increased");
            assertThat(types).contains("pod_not_ready");

            // Verify CrashLoopBackOff evidence carries correct exit code from fixture
            Evidence crashLoopEv = evidence.stream()
                    .filter(e -> "container_crash_loop_backoff".equals(e.evidenceType()))
                    .findFirst().orElseThrow();
            assertThat(crashLoopEv.attributes()).containsEntry("exit_code", 137);
        }
    }

    // ─── Helpers ───

    private static KubernetesJsonParser.ParsedPod crashLoopPod(int restartCount, int exitCode) {
        return new KubernetesJsonParser.ParsedPod(
            "recommend-service-abc123", "demo", "Running",
            "recommend-service", restartCount,
            "CrashLoopBackOff", "Error", exitCode
        );
    }
}
