package ai.sreagent.probe;

import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import ai.sreagent.probe.policy.ProbeExecutionPolicy;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FixtureProbeExecutorTest {

    private FixtureProbeExecutor executor;
    private ProbeExecutionPolicy policy;

    @BeforeEach
    void setUp() {
        executor = new FixtureProbeExecutor();
        policy = new ProbeExecutionPolicy();
    }

    @Test
    void shouldExecutePrometheusProbe() {
        ProbeExecutionResult result = executeSingle(ProbeType.PROMETHEUS_QUERY, "Check p95 latency");
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("prometheus");
        assertThat(result.canAffectDecision()).isFalse();
    }

    @Test
    void shouldExecuteLokiProbe() {
        ProbeExecutionResult result = executeSingle(ProbeType.LOKI_QUERY, "Search retry exhausted and downstream timeout");
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("loki");
    }

    @Test
    void shouldExecuteTraceProbe() {
        ProbeExecutionResult result = executeSingle(ProbeType.TRACE_QUERY, "Inspect span latency downstream");
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("tracing");
    }

    @Test
    void shouldExecuteKubernetesProbe() {
        ProbeExecutionResult result = executeSingle(ProbeType.KUBERNETES_QUERY, "Check pod restart and readiness");
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("kubernetes");
    }

    @Test
    void shouldExecuteAlertmanagerProbe() {
        ProbeExecutionResult result = executeSingle(ProbeType.ALERTMANAGER_QUERY, "Check alert firing and severity");
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("alertmanager");
    }

    @Test
    void shouldExecuteMultipleProbesAndProduceEvidence() {
        List<ProbeIntent> intents = List.of(
            new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "payment-service", "metric", "Check p95 latency", "metric_latency_p95_spike", "reason"),
            new ProbeIntent(ProbeType.LOKI_QUERY, "payment-service", "log", "Search downstream timeout logs", "log_downstream_timeout", "reason"),
            new ProbeIntent(ProbeType.TRACE_QUERY, "payment-service", "trace", "Inspect downstream span latency", "trace_downstream_span_slow", "reason"),
            new ProbeIntent(ProbeType.KUBERNETES_QUERY, "order-service", "k8s", "Check pod restart readiness", "pod_restart_count_increased", "reason")
        );

        ProbeExecutionPlan plan = new ProbeExecutionPlan("inc-1", "prop-1", intents, ProbeExecutionMode.FIXTURE, false);
        assertThat(policy.allows(plan)).isTrue();

        ProbeExecutionResult result = executor.execute(plan);

        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).hasSize(4);
        assertThat(result.normalizedEvidence()).hasSize(4);
        assertThat(result.executedProbeIds()).hasSize(4);
        assertThat(result.skippedProbeIds()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.canAffectDecision()).isFalse();
    }

    @Test
    void shouldProduceNormalizedEvidence() {
        ProbeExecutionResult result = executeSingle(ProbeType.PROMETHEUS_QUERY, "Check p95 latency");
        assertThat(result.normalizedEvidence()).isNotEmpty();
        assertThat(result.normalizedEvidence().get(0).originalEvidenceType()).isNotNull();
    }

    @Test
    void shouldHandleEmptyPlan() {
        ProbeExecutionPlan plan = new ProbeExecutionPlan("inc-1", "prop-1", List.of(), ProbeExecutionMode.FIXTURE, false);
        ProbeExecutionResult result = executor.execute(plan);

        assertThat(result.evidence()).isEmpty();
        assertThat(result.normalizedEvidence()).isEmpty();
        assertThat(result.executedProbeIds()).isEmpty();
        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.SKIPPED_BY_POLICY);
    }

    private ProbeExecutionResult executeSingle(ProbeType type, String query) {
        ProbeIntent intent = new ProbeIntent(type, "payment-service", "entity", query, "expected", "rationale");
        ProbeExecutionPlan plan = new ProbeExecutionPlan("inc-1", "prop-1", List.of(intent), ProbeExecutionMode.FIXTURE, false);
        return executor.execute(plan);
    }
}
