package ai.sreagent.probe;

import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProbeIntentRouterTest {

    private ProbeIntentRouter router;

    @BeforeEach
    void setUp() {
        router = new ProbeIntentRouter();
    }

    @Test
    void shouldCreatePlanWithSupportedTypes() {
        List<ProbeIntent> intents = List.of(
            new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "payment-service", "metric", "Check p95 latency", "metric_latency_p95_spike", "Verify latency"),
            new ProbeIntent(ProbeType.LOKI_QUERY, "payment-service", "log", "Search timeout logs", "log_timeout_error", "Check logs")
        );

        ProbeExecutionPlan plan = router.createPlan("inc-1", "prop-1", intents, ProbeExecutionMode.FIXTURE);

        assertThat(plan.incidentId()).isEqualTo("inc-1");
        assertThat(plan.proposalId()).isEqualTo("prop-1");
        assertThat(plan.probeIntents()).hasSize(2);
        assertThat(plan.mode()).isEqualTo(ProbeExecutionMode.FIXTURE);
        assertThat(plan.canAffectDecision()).isFalse();
    }

    @Test
    void shouldFilterUnsupportedTypes() {
        List<ProbeIntent> intents = List.of(
            new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "metric", "Check latency", "metric_latency_p95_spike", "reason"),
            new ProbeIntent(ProbeType.CMDB_QUERY, "svc", "cmdb", "Check CMDB", "cmdb_record", "reason"),
            new ProbeIntent(ProbeType.HUMAN_REVIEW, "svc", "human", "Manual review", "human_input", "reason")
        );

        ProbeExecutionPlan plan = router.createPlan("inc-1", "prop-1", intents, ProbeExecutionMode.FIXTURE);

        assertThat(plan.probeIntents()).hasSize(1);
        assertThat(plan.probeIntents().get(0).probeType()).isEqualTo(ProbeType.PROMETHEUS_QUERY);
    }

    @Test
    void shouldPreserveDeterministicOrdering() {
        List<ProbeIntent> intents = List.of(
            new ProbeIntent(ProbeType.TRACE_QUERY, "svc", "trace", "Check traces", "trace_downstream_span_slow", "reason"),
            new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "metric", "Check latency", "metric_latency_p95_spike", "reason"),
            new ProbeIntent(ProbeType.LOKI_QUERY, "svc", "log", "Check logs", "log_timeout_error", "reason")
        );

        ProbeExecutionPlan plan = router.createPlan("inc-1", "prop-1", intents, ProbeExecutionMode.FIXTURE);

        assertThat(plan.probeIntents()).hasSize(3);
        assertThat(plan.probeIntents().get(0).probeType()).isEqualTo(ProbeType.TRACE_QUERY);
        assertThat(plan.probeIntents().get(1).probeType()).isEqualTo(ProbeType.PROMETHEUS_QUERY);
        assertThat(plan.probeIntents().get(2).probeType()).isEqualTo(ProbeType.LOKI_QUERY);
    }

    @Test
    void shouldAlwaysSetCanAffectDecisionFalse() {
        ProbeExecutionPlan plan = router.createPlan(
            "inc-1", "prop-1",
            List.of(new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "m", "q", "e", "r")),
            ProbeExecutionMode.MOCK
        );
        assertThat(plan.canAffectDecision()).isFalse();
    }

    @Test
    void shouldHandleEmptyIntents() {
        ProbeExecutionPlan plan = router.createPlan("inc-1", "prop-1", List.of(), ProbeExecutionMode.FIXTURE);
        assertThat(plan.probeIntents()).isEmpty();
    }

    @Test
    void supportedTypesShouldNotIncludeCmdbOrHumanReview() {
        assertThat(ProbeIntentRouter.supportedTypes()).doesNotContain(ProbeType.CMDB_QUERY, ProbeType.HUMAN_REVIEW);
    }
}
