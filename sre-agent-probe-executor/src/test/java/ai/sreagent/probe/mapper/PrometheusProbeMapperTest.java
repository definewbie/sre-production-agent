package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class PrometheusProbeMapperTest {

    private PrometheusProbeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PrometheusProbeMapper();
    }

    @Test
    void shouldMapP95Latency() {
        ProbeIntent intent = intent("Check p95 latency for payment-service");
        Evidence e = mapper.mapToFixtureEvidence(intent, "inc-1");
        assertThat(e.evidenceType()).isEqualTo("metric_latency_p95_spike");
        assertThat(e.source()).isEqualTo("prometheus");
        assertThat(e.service()).isEqualTo("payment-service");
    }

    @Test
    void shouldMapDownstreamLatency() {
        ProbeIntent intent = intent("Check downstream p95 latency");
        Evidence e = mapper.mapToFixtureEvidence(intent, "inc-1");
        assertThat(e.evidenceType()).isEqualTo("metric_downstream_latency_spike");
    }

    @Test
    void shouldMapErrorRate() {
        ProbeIntent intent = intent("Check error rate spike");
        Evidence e = mapper.mapToFixtureEvidence(intent, "inc-1");
        assertThat(e.evidenceType()).isEqualTo("metric_error_rate_spike");
    }

    @Test
    void shouldMapRestartRate() {
        ProbeIntent intent = intent("Check restart rate");
        Evidence e = mapper.mapToFixtureEvidence(intent, "inc-1");
        assertThat(e.evidenceType()).isEqualTo("metric_restart_rate_increased");
    }

    @Test
    void shouldMapMemory() {
        ProbeIntent intent = intent("Check memory usage high");
        Evidence e = mapper.mapToFixtureEvidence(intent, "inc-1");
        assertThat(e.evidenceType()).isEqualTo("metric_memory_usage_high");
    }

    private ProbeIntent intent(String query) {
        return new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "payment-service", "metric", query, "expected", "reason");
    }
}
