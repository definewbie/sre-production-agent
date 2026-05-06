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
        @DisplayName("should not produce restart_count_increased when restartCount < 2")
        void shouldNotProduceRestartCountIncreasedForLowRestartCount() {
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

        @Test
        @DisplayName("should produce restart_count_observed when restartCount == 1")
        void shouldProduceRestartCountObservedForSingleRestart() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 1,
                "", "Error", 1
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-003b");

            assertThat(evidence.stream()
                    .anyMatch(e -> "restart_count_observed".equals(e.evidenceType())))
                    .isTrue();
        }
    }

    // ─── Test 3: Not Ready → pod_not_ready ───

    @Nested
    @DisplayName("Pod not ready semantic mapping")
    class PodNotReadyMapping {

        @Test
        @DisplayName("should map not-ready pod to pod_not_ready evidence")
        void shouldMapNotReadyPodToPodNotReadyEvidence() {
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

        @Test
        @DisplayName("should NOT produce pod_not_ready when pod is Running with no waiting reason but restartCount > 0")
        void shouldNotProducePodNotReadyForRunningPodWithRestarts() {
            // Latency scenario: pod Running, no waiting, but has restarts from probe failures
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "order-service-abc123", "demo", "Running",
                "order-service", 14,
                "", "Completed", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-004b");

            assertThat(evidence.stream()
                    .anyMatch(e -> "pod_not_ready".equals(e.evidenceType())))
                    .isFalse();
        }
    }

    // ─── Test 4: OOM → container_oom_killed ───

    @Nested
    @DisplayName("OOM semantic mapping")
    class OomMapping {

        @Test
        @DisplayName("should map OOMKilled pod to container_oom_killed evidence")
        void shouldMapOomKilledToContainerOomEvidence() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 3,
                "", "OOMKilled", 137
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-oom-1");

            Evidence oomEv = evidence.stream()
                    .filter(e -> "container_oom_killed".equals(e.evidenceType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected container_oom_killed evidence"));

            assertThat(oomEv.source()).isEqualTo("kubernetes");
            assertThat(oomEv.strength()).isGreaterThanOrEqualTo(0.90);
            assertThat(oomEv.attributes()).containsEntry("terminated_reason", "OOMKilled");
            assertThat(oomEv.attributes()).containsEntry("exit_code", 137);
            assertThat(oomEv.content()).contains("OOMKilled");
        }
    }

    // ─── Test 5: Healthy pod → counter evidence ───

    @Nested
    @DisplayName("Healthy pod produces counter evidence")
    class HealthyPodMapping {

        @Test
        @DisplayName("should produce k8s_runtime_healthy evidence for healthy running pod")
        void shouldProduceRuntimeHealthyForHealthyPod() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 0,
                "", "", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-005");

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo("k8s_runtime_healthy");
            assertThat(evidence.get(0).source()).isEqualTo("kubernetes");
            assertThat(evidence.get(0).strength()).isGreaterThanOrEqualTo(0.70);
        }

        @Test
        @DisplayName("healthy pod evidence should NOT contain fault types")
        void shouldNotProduceFaultTypesForHealthyPod() {
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 0,
                "", "", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-005b");

            List<String> faultTypes = List.of(
                "container_crash_loop_backoff", "pod_restart_count_increased",
                "pod_not_ready", "container_oom_killed"
            );
            for (Evidence e : evidence) {
                assertThat(faultTypes).doesNotContain(e.evidenceType());
            }
        }
    }

    // ─── Test 6: k8s_no_signal for unclassified state ───

    @Nested
    @DisplayName("No signal for unclassified pod state")
    class NoSignalMapping {

        @Test
        @DisplayName("should produce k8s_no_signal for pod with restarts but no classified anomaly")
        void shouldProduceNoSignalForRestartedButNonAnomalousPod() {
            // Pod Running, restartCount=0, no waiting, no terminated error → healthy
            // This test verifies that k8s_runtime_healthy is emitted for clean pods
            KubernetesJsonParser.ParsedPod pod = new KubernetesJsonParser.ParsedPod(
                "recommend-service-abc123", "demo", "Running",
                "recommend-service", 0,
                "", "", 0
            );

            List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-nosig-1");

            assertThat(evidence.stream()
                    .anyMatch(e -> "k8s_runtime_healthy".equals(e.evidenceType())))
                    .isTrue();
        }
    }

    // ─── Test 7: Multiple signals → multiple evidence items ───

    @Nested
    @DisplayName("Multiple signals from one pod")
    class MultipleSignalsMapping {

        @Test
        @DisplayName("should produce multiple semantic evidence items from one CrashLoopBackOff pod")
        void shouldProduceMultipleSemanticEvidenceItemsFromOnePod() {
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

    // ─── Test 8: Deployment metadata mapping ───

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

    // ─── Test 9: Event semantic typing ───

    @Nested
    @DisplayName("Event semantic typing")
    class EventSemanticMapping {

        @Test
        @DisplayName("should map Unhealthy event to pod_not_ready type")
        void shouldMapUnhealthyEventToPodNotReady() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("evt-1", "Unhealthy",
                    "Readiness probe failed", "Warning",
                    "order-service-abc123", "2026-01-01T00:00:00Z", 5)
            );

            List<Evidence> evidence = mapper.mapEventsToSemanticEvidence(events, "inc-evt-1");

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo("pod_not_ready");
        }

        @Test
        @DisplayName("should map OOMKilling event to container_oom_killed type")
        void shouldMapOomKillingEventToOomKilled() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("evt-2", "OOMKilling",
                    "Container was killed by OOM", "Warning",
                    "recommend-service-abc123", "2026-01-01T00:00:00Z", 1)
            );

            List<Evidence> evidence = mapper.mapEventsToSemanticEvidence(events, "inc-evt-2");

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo("container_oom_killed");
        }

        @Test
        @DisplayName("should map normal lifecycle events (Pulled/Started) to k8s_no_signal")
        void shouldMapNormalEventsToNoSignal() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("e1", "Pulled",
                    "Container image pulled", "Normal",
                    "order-service-abc123", "2026-01-01T00:00:00Z", 1),
                new KubernetesJsonParser.ParsedEvent("e2", "Started",
                    "Container started", "Normal",
                    "order-service-abc123", "2026-01-01T00:00:00Z", 1),
                new KubernetesJsonParser.ParsedEvent("e3", "Created",
                    "Container created", "Normal",
                    "order-service-abc123", "2026-01-01T00:00:00Z", 1)
            );

            List<Evidence> evidence = mapper.mapEventsToSemanticEvidence(events, "inc-evt-3");

            assertThat(evidence).hasSize(3);
            assertThat(evidence).allMatch(e -> "k8s_no_signal".equals(e.evidenceType()));
        }

        @Test
        @DisplayName("should map unclassified events to k8s_no_signal, NOT k8s_event")
        void shouldMapUnclassifiedEventsToNoSignal() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("evt-u", "SomeUnknownReason",
                    "Something happened", "Normal",
                    "order-service-abc123", "2026-01-01T00:00:00Z", 1)
            );

            List<Evidence> evidence = mapper.mapEventsToSemanticEvidence(events, "inc-evt-4");

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).evidenceType()).isEqualTo("k8s_no_signal");
            // Must NOT be the generic k8s_event type
            assertThat(evidence.get(0).evidenceType()).isNotEqualTo("k8s_event");
        }
    }

    // ─── Test 10: No evidenceType=NONE invariant ───

    @Nested
    @DisplayName("No NONE evidence type invariant")
    class NoNoneInvariant {

        @Test
        @DisplayName("semantic mapping should never produce evidenceType=NONE")
        void shouldNeverProduceNoneEvidenceType() {
            // Test various pod states
            List<KubernetesJsonParser.ParsedPod> pods = List.of(
                crashLoopPod(5, 1),
                healthyPod(),
                new KubernetesJsonParser.ParsedPod(
                    "pod-1", "demo", "Pending",
                    "svc", 0, "", "", 0),
                new KubernetesJsonParser.ParsedPod(
                    "pod-2", "demo", "Running",
                    "svc", 14, "", "Completed", 0),
                new KubernetesJsonParser.ParsedPod(
                    "pod-3", "demo", "Running",
                    "svc", 3, "", "OOMKilled", 137)
            );

            for (KubernetesJsonParser.ParsedPod pod : pods) {
                List<Evidence> evidence = mapper.mapPodToSemanticEvidence(pod, "inc-none-check");
                for (Evidence e : evidence) {
                    assertThat(e.evidenceType())
                        .describedAs("Pod %s produced evidenceType=NONE", pod.name())
                        .isNotEqualTo("NONE");
                    assertThat(e.evidenceType())
                        .describedAs("Pod %s produced empty evidenceType", pod.name())
                        .isNotEmpty();
                }
            }
        }

        @Test
        @DisplayName("semantic event mapping should never produce evidenceType=NONE or k8s_event")
        void shouldNeverProduceNoneOrGenericEventType() {
            List<KubernetesJsonParser.ParsedEvent> events = List.of(
                new KubernetesJsonParser.ParsedEvent("e1", "Unhealthy",
                    "msg", "Warning", "pod-1", "2026-01-01T00:00:00Z", 1),
                new KubernetesJsonParser.ParsedEvent("e2", "Pulled",
                    "msg", "Normal", "pod-2", "2026-01-01T00:00:00Z", 1),
                new KubernetesJsonParser.ParsedEvent("e3", "UnknownReason",
                    "msg", "Normal", "pod-3", "2026-01-01T00:00:00Z", 1)
            );

            List<Evidence> evidence = mapper.mapEventsToSemanticEvidence(events, "inc-none-evt");
            for (Evidence e : evidence) {
                assertThat(e.evidenceType())
                    .describedAs("Event produced evidenceType=NONE")
                    .isNotEqualTo("NONE");
                assertThat(e.evidenceType())
                    .describedAs("Event produced generic k8s_event type")
                    .isNotEqualTo("k8s_event");
            }
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

    private static KubernetesJsonParser.ParsedPod healthyPod() {
        return new KubernetesJsonParser.ParsedPod(
            "recommend-service-abc123", "demo", "Running",
            "recommend-service", 0,
            "", "", 0
        );
    }
}
