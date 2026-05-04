package ai.sreagent.demo.order;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final String paymentUrl;
    private final String inventoryUrl;
    private final int timeoutMs;
    private final HttpClient httpClient;

    public CheckoutController() {
        this.paymentUrl = env("PAYMENT_URL", "http://localhost:8082");
        this.inventoryUrl = env("INVENTORY_URL", "http://localhost:8083");
        this.timeoutMs = Integer.parseInt(env("ORDER_TIMEOUT_MS", "1000"));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @GetMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout() {
        Instant start = Instant.now();
        String orderId = "ORD-" + System.currentTimeMillis();
        log.info("[order] [checkout] orderId={} started", orderId);

        // Call payment-service /charge
        Map<String, Object> paymentResult;
        try {
            log.info("[order] [checkout] orderId={} calling payment-service /charge url={}", orderId, paymentUrl + "/charge");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(paymentUrl + "/charge"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("[order] [checkout] orderId={} payment-service responded status={}", orderId, resp.statusCode());
            if (resp.statusCode() >= 400) {
                log.warn("[order] [checkout] orderId={} payment-service error status={}", orderId, resp.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(errorBody(orderId, "payment", resp.statusCode(), "upstream error"));
            }
            paymentResult = Map.of("status", "success", "httpStatus", resp.statusCode());
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[order] [checkout] orderId={} payment-service timeout", orderId, e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(errorBody(orderId, "payment", 504, "upstream timeout"));
        } catch (Exception e) {
            log.error("[order] [checkout] orderId={} payment-service exception", orderId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(orderId, "payment", 502, e.getMessage()));
        }

        // Call inventory-service /reserve
        Map<String, Object> inventoryResult;
        try {
            log.info("[order] [checkout] orderId={} calling inventory-service /reserve url={}", orderId, inventoryUrl + "/reserve");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(inventoryUrl + "/reserve"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("[order] [checkout] orderId={} inventory-service responded status={}", orderId, resp.statusCode());
            if (resp.statusCode() >= 400) {
                log.warn("[order] [checkout] orderId={} inventory-service error status={}", orderId, resp.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(errorBody(orderId, "inventory", resp.statusCode(), "upstream error"));
            }
            inventoryResult = Map.of("status", "success", "httpStatus", resp.statusCode());
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[order] [checkout] orderId={} inventory-service timeout", orderId, e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(errorBody(orderId, "inventory", 504, "upstream timeout"));
        } catch (Exception e) {
            log.error("[order] [checkout] orderId={} inventory-service exception", orderId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(orderId, "inventory", 502, e.getMessage()));
        }

        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        log.info("[order] [checkout] orderId={} completed in {}ms", orderId, elapsedMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", "completed");
        result.put("elapsedMs", elapsedMs);
        result.put("payment", paymentResult);
        result.put("inventory", inventoryResult);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> errorBody(String orderId, String service, int status, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("status", "failed");
        body.put("failedService", service);
        body.put("error", error);
        body.put("httpStatus", status);
        return body;
    }

    private static String env(String key, String def) {
        String val = System.getenv(key);
        return val != null ? val : def;
    }
}
