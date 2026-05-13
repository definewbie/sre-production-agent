package ai.sreagent.demo.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutControllerTest {

    @Test
    void checkoutControllerCanBeConstructed() {
        // Tests that the controller can be instantiated (env var reads don't fail)
        CheckoutController controller = new CheckoutController(new FaultConfigController());
        assertThat(controller).isNotNull();
    }

    @Test
    void errorModeShouldRespectErrorRate() {
        FaultConfigController faultConfigController = new FaultConfigController();
        faultConfigController.updateFaultConfig(new FaultConfig("error", 0, 1.0, 0.0));
        CheckoutController controller = new CheckoutController(faultConfigController);

        var response = controller.checkout();

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("failed");
        assertThat(response.getBody().get("failedService")).isEqualTo("order");
    }

    @Test
    void errorModeWithZeroErrorRateShouldContinuePastSelfInjection() {
        FaultConfigController faultConfigController = new FaultConfigController();
        faultConfigController.updateFaultConfig(new FaultConfig("error", 0, 0.0, 0.0));
        CheckoutController controller = new CheckoutController(faultConfigController);

        var response = controller.checkout();

        assertThat(response.getStatusCode().value()).isNotEqualTo(500);
    }
}
