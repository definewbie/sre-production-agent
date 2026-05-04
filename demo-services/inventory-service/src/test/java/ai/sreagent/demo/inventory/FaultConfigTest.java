package ai.sreagent.demo.inventory;

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
        FaultConfig config = new FaultConfig("latency", 300, 0.2, 0.0);
        assertThat(config.mode()).isEqualTo("latency");
        assertThat(config.latencyMs()).isEqualTo(300);
        assertThat(config.errorRate()).isEqualTo(0.2);
    }
}
