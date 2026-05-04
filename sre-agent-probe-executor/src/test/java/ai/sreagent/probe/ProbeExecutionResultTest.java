package ai.sreagent.probe;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class ProbeExecutionResultTest {

    @Test
    void shouldRejectCanAffectDecisionTrue() {
        assertThatThrownBy(() -> new ProbeExecutionResult(
            "inc-1", "prop-1", ProbeExecutionStatus.EXECUTED,
            null, null, null, null, null, true
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("canAffectDecision must be false");
    }

    @Test
    void shouldAllowCanAffectDecisionFalse() {
        ProbeExecutionResult result = new ProbeExecutionResult(
            "inc-1", "prop-1", ProbeExecutionStatus.EXECUTED,
            null, null, null, null, null, false
        );
        assertThat(result.canAffectDecision()).isFalse();
    }
}
