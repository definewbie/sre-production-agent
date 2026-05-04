package ai.sreagent.demo.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReserveControllerTest {

    private FaultConfigController faultConfigController;
    private ReserveController reserveController;

    @BeforeEach
    void setUp() throws Exception {
        faultConfigController = new FaultConfigController();
        reserveController = new ReserveController(faultConfigController);
    }

    @Test
    void normalModeShouldReturnSuccess() {
        var response = reserveController.reserve();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("reserved");
    }

    @Test
    void errorModeWithFullRateShouldReturn500() throws Exception {
        setFaultConfig(new FaultConfig("error", 0, 1.0, 0.0));

        var response = reserveController.reserve();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("failed");
    }

    @Test
    void latencyModeShouldReturnSuccess() throws Exception {
        setFaultConfig(new FaultConfig("latency", 10, 0.0, 0.0));

        long start = System.currentTimeMillis();
        var response = reserveController.reserve();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(elapsed).isGreaterThanOrEqualTo(10);
    }

    @Test
    void normalModeShouldNeverFail() throws Exception {
        setFaultConfig(new FaultConfig("normal", 0, 0.0, 0.0));
        for (int i = 0; i < 20; i++) {
            var response = reserveController.reserve();
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }
    }

    private void setFaultConfig(FaultConfig config) throws Exception {
        Field field = FaultConfigController.class.getDeclaredField("config");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<FaultConfig> ref = (AtomicReference<FaultConfig>) field.get(faultConfigController);
        ref.set(config);
    }
}
