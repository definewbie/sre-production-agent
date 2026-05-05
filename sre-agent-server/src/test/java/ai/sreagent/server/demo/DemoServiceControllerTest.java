package ai.sreagent.server.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoServiceController.class)
class DemoServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoServiceClient client;

    @Test
    @DisplayName("GET /api/demo-services/status returns service list")
    void statusReturnsServices() throws Exception {
        when(client.checkAllServices()).thenReturn(new DemoServicesStatusResponse(
                List.of(
                        new DemoServiceStatus("order-service",
                                "http://localhost:18081", "UP", "normal", true),
                        new DemoServiceStatus("payment-service",
                                "http://localhost:18082", "UP", "normal", true),
                        new DemoServiceStatus("inventory-service",
                                "http://localhost:18083", "UP", "normal", true)
                ),
                "order-service → payment-service → inventory-service"
        ));

        mockMvc.perform(get("/api/demo-services/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(3))
                .andExpect(jsonPath("$.services[0].service").value("order-service"))
                .andExpect(jsonPath("$.services[1].service").value("payment-service"))
                .andExpect(jsonPath("$.services[2].service").value("inventory-service"))
                .andExpect(jsonPath("$.topology").value("order-service → payment-service → inventory-service"));
    }

    @Test
    @DisplayName("GET /api/demo-services/status handles unreachable services")
    void statusHandlesUnreachableServices() throws Exception {
        when(client.checkAllServices()).thenReturn(new DemoServicesStatusResponse(
                List.of(
                        new DemoServiceStatus("order-service",
                                "http://localhost:18081", "unreachable", "unknown", false),
                        new DemoServiceStatus("payment-service",
                                "http://localhost:18082", "unreachable", "unknown", false),
                        new DemoServiceStatus("inventory-service",
                                "http://localhost:18083", "unreachable", "unknown", false)
                ),
                "order-service → payment-service → inventory-service"
        ));

        mockMvc.perform(get("/api/demo-services/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].reachable").value(false))
                .andExpect(jsonPath("$.services[0].health").value("unreachable"));
    }

    @Test
    @DisplayName("POST /api/demo-services/fault/normal sets all to normal")
    void faultNormalSetsAllToNormal() throws Exception {
        when(client.setAllFaultConfig(any())).thenReturn(new DemoServicesStatusResponse(
                List.of(
                        new DemoServiceStatus("order-service",
                                "http://localhost:18081", "UP", "normal", true),
                        new DemoServiceStatus("payment-service",
                                "http://localhost:18082", "UP", "normal", true),
                        new DemoServiceStatus("inventory-service",
                                "http://localhost:18083", "UP", "normal", true)
                ),
                "order-service → payment-service → inventory-service"
        ));

        mockMvc.perform(post("/api/demo-services/fault/normal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].faultConfig").value("normal"))
                .andExpect(jsonPath("$.services[1].faultConfig").value("normal"))
                .andExpect(jsonPath("$.services[2].faultConfig").value("normal"));
    }

    @Test
    @DisplayName("POST /api/demo-services/fault/payment-latency injects latency")
    void faultPaymentLatency() throws Exception {
        when(client.setNamedServiceFaultConfig(anyString(), anyMap()))
                .thenReturn(new DemoServicesStatusResponse(
                        List.of(
                                new DemoServiceStatus("order-service",
                                        "http://localhost:18081", "UP", "normal", true),
                                new DemoServiceStatus("payment-service",
                                        "http://localhost:18082", "UP", "latency", true),
                                new DemoServiceStatus("inventory-service",
                                        "http://localhost:18083", "UP", "normal", true)
                        ),
                        "order-service → payment-service → inventory-service"
                ));

        mockMvc.perform(post("/api/demo-services/fault/payment-latency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[1].faultConfig").value("latency"));

        verify(client).setNamedServiceFaultConfig("payment-service",
                java.util.Map.of("mode", "latency", "delayMs", 1500));
    }

    @Test
    @DisplayName("POST /api/demo-services/fault/payment-error injects errors")
    void faultPaymentError() throws Exception {
        when(client.setNamedServiceFaultConfig(anyString(), anyMap()))
                .thenReturn(new DemoServicesStatusResponse(
                        List.of(
                                new DemoServiceStatus("order-service",
                                        "http://localhost:18081", "UP", "normal", true),
                                new DemoServiceStatus("payment-service",
                                        "http://localhost:18082", "UP", "error", true),
                                new DemoServiceStatus("inventory-service",
                                        "http://localhost:18083", "UP", "normal", true)
                        ),
                        "order-service → payment-service → inventory-service"
                ));

        mockMvc.perform(post("/api/demo-services/fault/payment-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[1].faultConfig").value("error"));

        verify(client).setNamedServiceFaultConfig("payment-service",
                java.util.Map.of("mode", "error", "errorRate", "80%"));
    }

    @Test
    @DisplayName("POST /api/demo-services/fault/payment-timeout injects timeout")
    void faultPaymentTimeout() throws Exception {
        when(client.setNamedServiceFaultConfig(anyString(), anyMap()))
                .thenReturn(new DemoServicesStatusResponse(
                        List.of(
                                new DemoServiceStatus("order-service",
                                        "http://localhost:18081", "UP", "normal", true),
                                new DemoServiceStatus("payment-service",
                                        "http://localhost:18082", "UP", "timeout", true),
                                new DemoServiceStatus("inventory-service",
                                        "http://localhost:18083", "UP", "normal", true)
                        ),
                        "order-service → payment-service → inventory-service"
                ));

        mockMvc.perform(post("/api/demo-services/fault/payment-timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[1].faultConfig").value("timeout"));

        verify(client).setNamedServiceFaultConfig("payment-service",
                java.util.Map.of("mode", "timeout", "timeoutMs", 5000));
    }

    @Test
    @DisplayName("POST /api/demo-services/fault/reset resets all services")
    void faultReset() throws Exception {
        when(client.setAllFaultConfig(any())).thenReturn(new DemoServicesStatusResponse(
                List.of(
                        new DemoServiceStatus("order-service",
                                "http://localhost:18081", "UP", "normal", true),
                        new DemoServiceStatus("payment-service",
                                "http://localhost:18082", "UP", "normal", true),
                        new DemoServiceStatus("inventory-service",
                                "http://localhost:18083", "UP", "normal", true)
                ),
                "order-service → payment-service → inventory-service"
        ));

        mockMvc.perform(post("/api/demo-services/fault/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].faultConfig").value("normal"))
                .andExpect(jsonPath("$.services[1].faultConfig").value("normal"))
                .andExpect(jsonPath("$.services[2].faultConfig").value("normal"));
    }

    @Test
    @DisplayName("POST /api/demo-services/traffic generates test traffic")
    void generateTraffic() throws Exception {
        when(client.generateTraffic(5)).thenReturn(3);

        mockMvc.perform(post("/api/demo-services/traffic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.successes").value(3))
                .andExpect(jsonPath("$.failures").value(2));

        verify(client).generateTraffic(5);
    }
}
