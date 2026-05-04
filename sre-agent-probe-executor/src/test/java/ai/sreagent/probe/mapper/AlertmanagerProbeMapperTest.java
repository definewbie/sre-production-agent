package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class AlertmanagerProbeMapperTest {

    private AlertmanagerProbeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlertmanagerProbeMapper();
    }

    @Test
    void shouldMapFiring() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check alert firing status"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("alert_firing");
        assertThat(e.source()).isEqualTo("alertmanager");
    }

    @Test
    void shouldMapSeverity() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check alert severity"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("alert_severity_high");
    }

    private ProbeIntent intent(String query) {
        return new ProbeIntent(ProbeType.ALERTMANAGER_QUERY, "payment-service", "alert", query, "expected", "reason");
    }
}
