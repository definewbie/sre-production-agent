package ai.sreagent.demo.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultConfigControllerTest {

    private FaultConfigController controller;

    @BeforeEach
    void resetConfig() {
        controller = new FaultConfigController();
        controller.updateFaultConfig(new FaultConfig("normal", 0, 0.0, 0.0));
    }

    @Test
    void getFaultConfigShouldReturnDefault() {
        assertThat(controller.getFaultConfig().mode()).isEqualTo("normal");
    }

    @Test
    void postFaultConfigShouldUpdate() {
        FaultConfig updated = controller.updateFaultConfig(
                new FaultConfig("error", 0, 0.5, 0.0));

        assertThat(updated.mode()).isEqualTo("error");
        assertThat(updated.errorRate()).isEqualTo(0.5);
        assertThat(controller.getFaultConfig().mode()).isEqualTo("error");
    }
}
