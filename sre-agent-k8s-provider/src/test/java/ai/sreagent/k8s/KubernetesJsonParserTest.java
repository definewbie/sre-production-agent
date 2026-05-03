package ai.sreagent.k8s;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KubernetesJsonParserTest {

    private KubernetesJsonParser parser;
    private FixtureKubernetesResourceReader reader;

    @BeforeEach
    void setUp() {
        parser = new KubernetesJsonParser();
        reader = new FixtureKubernetesResourceReader();
    }

    @Nested
    @DisplayName("Pod parsing")
    class PodParsing {
        @Test
        @DisplayName("should parse CrashLoopBackOff pod")
        void parseCrashLoopPod() throws Exception {
            String json = reader.readResource("pods", "payment", "production", null);
            KubernetesJsonParser.ParsedPod pod = parser.parsePod(json);

            assertThat(pod).isNotNull();
            assertThat(pod.name()).isEqualTo("payment-service-7d9f8b6c4-x2k9p");
            assertThat(pod.namespace()).isEqualTo("production");
            assertThat(pod.phase()).isEqualTo("Running");
            assertThat(pod.restartCount()).isEqualTo(7);
            assertThat(pod.waitingReason()).isEqualTo("CrashLoopBackOff");
            assertThat(pod.terminatedReason()).isEqualTo("OOMKilled");
            assertThat(pod.terminatedExitCode()).isEqualTo(137);
        }

        @Test
        @DisplayName("should parse OOMKilled pod")
        void parseOomKilledPod() throws Exception {
            String json = reader.readResource("pods", "order-oom", "production", null);
            KubernetesJsonParser.ParsedPod pod = parser.parsePod(json);

            assertThat(pod).isNotNull();
            assertThat(pod.terminatedReason()).isEqualTo("OOMKilled");
            assertThat(pod.terminatedExitCode()).isEqualTo(137);
            assertThat(pod.restartCount()).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Deployment parsing")
    class DeploymentParsing {
        @Test
        @DisplayName("should parse deployment with degraded replicas")
        void parseDeployment() throws Exception {
            String json = reader.readResource("deployments", "payment-service", "production", null);
            KubernetesJsonParser.ParsedDeployment dep = parser.parseDeployment(json);

            assertThat(dep).isNotNull();
            assertThat(dep.name()).isEqualTo("payment-service");
            assertThat(dep.namespace()).isEqualTo("production");
            assertThat(dep.replicas()).isEqualTo(3);
            assertThat(dep.readyReplicas()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Event parsing")
    class EventParsing {
        @Test
        @DisplayName("should parse EventList with 3 events")
        void parseEvents() throws Exception {
            String json = reader.readResource("events", "", "production", null);
            List<KubernetesJsonParser.ParsedEvent> events = parser.parseEvents(json);

            assertThat(events).hasSize(3);
            assertThat(events.getFirst().reason()).isNotEmpty();
            assertThat(events).anyMatch(e -> "Warning".equals(e.type()));
        }
    }

    @Nested
    @DisplayName("Fault mode detection")
    class FaultDetection {
        @Test
        @DisplayName("should detect OOM_KILLED and CRASH_LOOP_BACK_OFF from CrashLoop pod")
        void detectCrashLoopFaults() throws Exception {
            String json = reader.readResource("pods", "payment", "production", null);
            KubernetesJsonParser.ParsedPod pod = parser.parsePod(json);
            List<KubernetesFaultMode> faults = parser.detectFaultModes(pod);

            assertThat(faults).contains(KubernetesFaultMode.POD_OOM_KILLED);
            assertThat(faults).contains(KubernetesFaultMode.CRASH_LOOP_BACK_OFF);
            assertThat(faults).contains(KubernetesFaultMode.RESTART_COUNT_INCREASED);
        }

        @Test
        @DisplayName("should detect OOM_KILLED from OOM pod")
        void detectOomFaults() throws Exception {
            String json = reader.readResource("pods", "order-oom", "production", null);
            KubernetesJsonParser.ParsedPod pod = parser.parsePod(json);
            List<KubernetesFaultMode> faults = parser.detectFaultModes(pod);

            assertThat(faults).contains(KubernetesFaultMode.POD_OOM_KILLED);
        }

        @Test
        @DisplayName("should return empty list for null pod")
        void nullPodReturnsEmpty() {
            List<KubernetesFaultMode> faults = parser.detectFaultModes(null);
            assertThat(faults).isEmpty();
        }
    }
}
