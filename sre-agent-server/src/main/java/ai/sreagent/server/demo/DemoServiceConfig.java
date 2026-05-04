package ai.sreagent.server.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for demo microservice URLs.
 */
@Configuration
public class DemoServiceConfig {

    @Value("${sre-agent.demo.order-service-url:http://localhost:18081}")
    private String orderServiceUrl;

    @Value("${sre-agent.demo.payment-service-url:http://localhost:18082}")
    private String paymentServiceUrl;

    @Value("${sre-agent.demo.inventory-service-url:http://localhost:18083}")
    private String inventoryServiceUrl;

    public String getOrderServiceUrl() {
        return orderServiceUrl;
    }

    public String getPaymentServiceUrl() {
        return paymentServiceUrl;
    }

    public String getInventoryServiceUrl() {
        return inventoryServiceUrl;
    }
}
