package ai.sreagent.probe.policy;

import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import ai.sreagent.probe.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class ProbeExecutionPolicyTest {

    private ProbeExecutionPolicy defaultPolicy;

    @BeforeEach
    void setUp() {
        defaultPolicy = new ProbeExecutionPolicy();
    }

    @Test
    void shouldAllowFixtureMode() {
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", List.of(sampleIntent()), ProbeExecutionMode.FIXTURE, false
        );
        assertThat(defaultPolicy.allows(plan)).isTrue();
    }

    @Test
    void shouldAllowMockMode() {
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", List.of(sampleIntent()), ProbeExecutionMode.MOCK, false
        );
        assertThat(defaultPolicy.allows(plan)).isTrue();
    }

    @Test
    void shouldRejectLiveModeByDefault() {
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", List.of(sampleIntent()), ProbeExecutionMode.LIVE, false
        );
        assertThat(defaultPolicy.allows(plan)).isFalse();
    }

    @Test
    void shouldAllowLiveModeWhenExplicitlyEnabled() {
        ProbeExecutionPolicy livePolicy = new ProbeExecutionPolicy(true, 10);
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", List.of(sampleIntent()), ProbeExecutionMode.LIVE, false
        );
        assertThat(livePolicy.allows(plan)).isTrue();
    }

    @Test
    void shouldRejectPlanExceedingMaxProbes() {
        List<ProbeIntent> tooMany = IntStream.range(0, 11)
            .mapToObj(i -> new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "m", "query " + i, "e", "r"))
            .toList();
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", tooMany, ProbeExecutionMode.FIXTURE, false
        );
        assertThat(defaultPolicy.allows(plan)).isFalse();
    }

    @Test
    void shouldEnforceMaxProbesAtBoundary() {
        List<ProbeIntent> exactLimit = IntStream.range(0, 10)
            .mapToObj(i -> new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "m", "query " + i, "e", "r"))
            .toList();
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", exactLimit, ProbeExecutionMode.FIXTURE, false
        );
        assertThat(defaultPolicy.allows(plan)).isTrue();
    }

    @Test
    void shouldReportDefaults() {
        assertThat(defaultPolicy.liveEnabled()).isFalse();
        assertThat(defaultPolicy.maxProbes()).isEqualTo(10);
    }

    private ProbeIntent sampleIntent() {
        return new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "metric", "Check latency", "metric_latency_p95_spike", "reason");
    }
}
