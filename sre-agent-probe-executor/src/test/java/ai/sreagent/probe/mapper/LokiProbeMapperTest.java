package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class LokiProbeMapperTest {

    private LokiProbeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new LokiProbeMapper();
    }

    @Test
    void shouldMapDownstreamTimeout() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Search downstream timeout logs"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("log_downstream_timeout");
        assertThat(e.source()).isEqualTo("loki");
    }

    @Test
    void shouldMapRetryExhausted() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Search retry exhausted"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("log_retry_exhausted");
    }

    @Test
    void shouldMapGenericTimeout() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check timeout errors"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("log_timeout_error");
    }

    @Test
    void shouldMapException() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Find exception in logs"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("log_exception");
    }

    private ProbeIntent intent(String query) {
        return new ProbeIntent(ProbeType.LOKI_QUERY, "payment-service", "log", query, "expected", "reason");
    }
}
