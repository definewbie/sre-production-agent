package ai.sreagent.demo.order;

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
        FaultConfig config = new FaultConfig("latency", 500, 0.5, 0.1);
        assertThat(config.mode()).isEqualTo("latency");
        assertThat(config.latencyMs()).isEqualTo(500);
        assertThat(config.errorRate()).isEqualTo(0.5);
        assertThat(config.timeoutRate()).isEqualTo(0.1);
    }

    @Test
    void recordEqualityShouldWork() {
        FaultConfig a = new FaultConfig("error", 0, 0.3, 0.0);
        FaultConfig b = new FaultConfig("error", 0, 0.3, 0.0);
        assertThat(a).isEqualTo(b);
    }
}
