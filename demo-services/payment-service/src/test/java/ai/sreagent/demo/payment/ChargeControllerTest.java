package ai.sreagent.demo.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeControllerTest {

    private FaultConfigController faultConfigController;
    private ChargeController chargeController;

    @BeforeEach
    void setUp() throws Exception {
        faultConfigController = new FaultConfigController();
        chargeController = new ChargeController(faultConfigController);
    }

    @Test
    void normalModeShouldReturnSuccess() {
        var response = chargeController.charge();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("charged");
    }

    @Test
    void errorModeShouldReturn500WhenErrorRateIsHigh() throws Exception {
        // Set error rate to 1.0 so it always fails
        setFaultConfig(new FaultConfig("error", 0, 1.0, 0.0));

        var response = chargeController.charge();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("failed");
    }

    @Test
    void latencyModeShouldReturnSuccess() throws Exception {
        setFaultConfig(new FaultConfig("latency", 10, 0.0, 0.0));

        long start = System.currentTimeMillis();
        var response = chargeController.charge();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(elapsed).isGreaterThanOrEqualTo(10);
    }

    @Test
    void normalModeWithZeroErrorRateShouldNotFail() throws Exception {
        setFaultConfig(new FaultConfig("normal", 0, 0.0, 0.0));

        // Run multiple times to be confident
        for (int i = 0; i < 20; i++) {
            var response = chargeController.charge();
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
