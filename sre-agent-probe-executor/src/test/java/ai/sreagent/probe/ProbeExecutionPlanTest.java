package ai.sreagent.probe;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class ProbeExecutionPlanTest {

    @Test
    void shouldRejectCanAffectDecisionTrue() {
        assertThatThrownBy(() -> new ProbeExecutionPlan(
            "inc-1", "prop-1", null, ProbeExecutionMode.FIXTURE, true
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("canAffectDecision must be false");
    }

    @Test
    void shouldAllowCanAffectDecisionFalse() {
        ProbeExecutionPlan plan = new ProbeExecutionPlan(
            "inc-1", "prop-1", null, ProbeExecutionMode.FIXTURE, false
        );
        assertThat(plan.canAffectDecision()).isFalse();
    }
}
