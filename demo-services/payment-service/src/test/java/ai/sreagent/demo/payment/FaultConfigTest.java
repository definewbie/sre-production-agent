package ai.sreagent.demo.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultConfigTest {

    @Test
    void defaultConfigShouldHaveNormalMode() {
        FaultConfig config = FaultConfig.DEFAULT;
        assertThat(config.mode()).isEqualTo("normal");
        assertThat(config.latencyMs()).isEqualTo(0);
        assertThat(config.errorRate()).isEqualTo(0.0);
        assertThat(config.timeoutRate()).isEqualTo(0.0);
    }

    @Test
    void shouldCreateCustomConfig() {
        FaultConfig config = new FaultConfig("error", 0, 0.8, 0.2);
        assertThat(config.mode()).isEqualTo("error");
        assertThat(config.errorRate()).isEqualTo(0.8);
    }

    @Test
    void recordEqualityShouldWork() {
        FaultConfig a = new FaultConfig("timeout", 0, 0.0, 1.0);
        FaultConfig b = new FaultConfig("timeout", 0, 0.0, 1.0);
        assertThat(a).isEqualTo(b);
    }
}
