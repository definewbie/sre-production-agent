package ai.sreagent.demo.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutControllerTest {

    @Test
    void checkoutControllerCanBeConstructed() {
        // Tests that the controller can be instantiated (env var reads don't fail)
        CheckoutController controller = new CheckoutController();
        assertThat(controller).isNotNull();
    }
}
