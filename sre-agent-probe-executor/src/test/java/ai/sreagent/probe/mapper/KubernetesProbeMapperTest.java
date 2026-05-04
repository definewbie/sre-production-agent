package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class KubernetesProbeMapperTest {

    private KubernetesProbeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KubernetesProbeMapper();
    }

    @Test
    void shouldMapRestart() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check pod restart count"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("pod_restart_count_increased");
        assertThat(e.source()).isEqualTo("kubernetes");
    }

    @Test
    void shouldMapReadiness() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check pod readiness"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("pod_not_ready");
    }

    @Test
    void shouldMapOom() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check OOM events"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("kubernetes_event_oomkilled");
    }

    private ProbeIntent intent(String query) {
        return new ProbeIntent(ProbeType.KUBERNETES_QUERY, "order-service", "k8s", query, "expected", "reason");
    }
}
