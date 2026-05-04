package ai.sreagent.server.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for controlling demo microservices — check status and inject faults.
 */
@RestController
@RequestMapping("/api/demo-services")
public class DemoServiceController {

    private final DemoServiceClient client;

    public DemoServiceController(DemoServiceClient client) {
        this.client = client;
    }

    @GetMapping("/status")
    public ResponseEntity<DemoServicesStatusResponse> getStatus() {
        return ResponseEntity.ok(client.checkAllServices());
    }

    @PostMapping("/fault/normal")
    public ResponseEntity<DemoServicesStatusResponse> setNormal() {
        return ResponseEntity.ok(
                client.setAllFaultConfig(Map.of("mode", "normal")));
    }

    @PostMapping("/fault/payment-latency")
    public ResponseEntity<DemoServicesStatusResponse> setPaymentLatency() {
        return ResponseEntity.ok(
                client.setNamedServiceFaultConfig("payment-service",
                        Map.of("mode", "latency", "delayMs", 1500)));
    }

    @PostMapping("/fault/payment-error")
    public ResponseEntity<DemoServicesStatusResponse> setPaymentError() {
        return ResponseEntity.ok(
                client.setNamedServiceFaultConfig("payment-service",
                        Map.of("mode", "error", "errorRate", "80%")));
    }

    @PostMapping("/fault/payment-timeout")
    public ResponseEntity<DemoServicesStatusResponse> setPaymentTimeout() {
        return ResponseEntity.ok(
                client.setNamedServiceFaultConfig("payment-service",
                        Map.of("mode", "timeout", "timeoutMs", 5000)));
    }

    @PostMapping("/fault/reset")
    public ResponseEntity<DemoServicesStatusResponse> reset() {
        return ResponseEntity.ok(
                client.setAllFaultConfig(Map.of("mode", "normal")));
    }
}
