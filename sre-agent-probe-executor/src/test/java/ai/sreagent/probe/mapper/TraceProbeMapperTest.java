package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class TraceProbeMapperTest {

    private TraceProbeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TraceProbeMapper();
    }

    @Test
    void shouldMapDownstreamSlowSpan() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Inspect downstream span latency"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("trace_downstream_span_slow");
        assertThat(e.source()).isEqualTo("tracing");
    }

    @Test
    void shouldMapErrorSpan() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Find error spans"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("trace_error_span");
    }

    @Test
    void shouldMapTimeoutSpan() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Check timeout spans"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("trace_timeout_span");
    }

    @Test
    void shouldMapDependencyPath() {
        Evidence e = mapper.mapToFixtureEvidence(intent("Trace dependency path"), "inc-1");
        assertThat(e.evidenceType()).isEqualTo("trace_dependency_path");
    }

    private ProbeIntent intent(String query) {
        return new ProbeIntent(ProbeType.TRACE_QUERY, "order-service", "trace", query, "expected", "reason");
    }
}
