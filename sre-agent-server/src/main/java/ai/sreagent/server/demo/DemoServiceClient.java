package ai.sreagent.server.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client that communicates with demo microservices to check health,
 * read fault configuration, and inject faults.
 */
@Service
public class DemoServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String TOPOLOGY =
            "order-service → payment-service; order-service → inventory-service";

    private final DemoServiceConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DemoServiceClient(DemoServiceConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check status of all three demo services.
     */
    public DemoServicesStatusResponse checkAllServices() {
        List<DemoServiceStatus> statuses = new ArrayList<>();
        statuses.add(checkService("order-service", config.getOrderServiceUrl()));
        statuses.add(checkService("payment-service", config.getPaymentServiceUrl()));
        statuses.add(checkService("inventory-service", config.getInventoryServiceUrl()));
        return new DemoServicesStatusResponse(statuses, TOPOLOGY);
    }

    /**
     * Set fault configuration on a specific service via POST /fault-config.
     */
    public DemoServiceStatus setFaultConfig(String serviceName, String serviceUrl,
                                            Map<String, Object> faultConfig) {
        try {
            String body = objectMapper.writeValueAsString(faultConfig);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/fault-config"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // Re-fetch status after setting fault
            return checkService(serviceName, serviceUrl);
        } catch (Exception e) {
            return new DemoServiceStatus(serviceName, serviceUrl,
                    "unknown", "unknown", false);
        }
    }

    /**
     * Set fault config on all three services.
     */
    public DemoServicesStatusResponse setAllFaultConfig(Map<String, Object> faultConfig) {
        setFaultConfig("order-service", config.getOrderServiceUrl(), faultConfig);
        setFaultConfig("payment-service", config.getPaymentServiceUrl(), faultConfig);
        setFaultConfig("inventory-service", config.getInventoryServiceUrl(), faultConfig);
        return checkAllServices();
    }

    /**
     * Set fault config on a specific named service.
     */
    public DemoServicesStatusResponse setNamedServiceFaultConfig(String serviceName,
                                                                  Map<String, Object> faultConfig) {
        Map<String, String> serviceUrls = getServiceUrlMap();
        String url = serviceUrls.get(serviceName);
        if (url != null) {
            setFaultConfig(serviceName, url, faultConfig);
        }
        return checkAllServices();
    }

    private DemoServiceStatus checkService(String serviceName, String serviceUrl) {
        String health = "unknown";
        String faultConfig = "normal";
        boolean reachable = false;

        // Check health
        try {
            HttpRequest healthRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> healthResponse = httpClient.send(healthRequest,
                    HttpResponse.BodyHandlers.ofString());
            health = healthResponse.body();
            reachable = true;
        } catch (Exception e) {
            return new DemoServiceStatus(serviceName, serviceUrl,
                    "unreachable", "unknown", false);
        }

        // Check fault config
        try {
            HttpRequest faultRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/fault-config"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> faultResponse = httpClient.send(faultRequest,
                    HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(faultResponse.body());
            faultConfig = node.has("mode") ? node.get("mode").asText() : "normal";
        } catch (Exception e) {
            faultConfig = "unknown";
        }

        return new DemoServiceStatus(serviceName, serviceUrl, health, faultConfig, reachable);
    }

    private Map<String, String> getServiceUrlMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("order-service", config.getOrderServiceUrl());
        map.put("payment-service", config.getPaymentServiceUrl());
        map.put("inventory-service", config.getInventoryServiceUrl());
        return map;
    }

    /**
     * Generate synthetic traffic by calling order-service checkout endpoint.
     * Uses virtual threads (Java 21) for concurrent request dispatch.
     * This ensures Prometheus/Loki/Jaeger capture metrics under fault conditions.
     *
     * @param requests number of checkout requests to send concurrently
     * @return number of successful requests
     */
    public int generateTraffic(int requests) {
        String orderUrl = config.getOrderServiceUrl();
        AtomicInteger success = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                executor.submit(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(orderUrl + "/checkout"))
                                .timeout(Duration.ofSeconds(15))
                                .GET()
                                .build();
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        success.incrementAndGet();
                    } catch (Exception e) {
                        // Expected under fault conditions — still counts as traffic
                    }
                });
            }
            executor.shutdown();
            // Wait up to 20s for all requests to complete (15s timeout + overhead)
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return success.get();
    }

    /**
     * Start background checkout traffic for the requested duration.
     * Returns immediately so the chaos API does not block for the full experiment.
     */
    public void generateTrafficAsync(int rps, int durationSeconds) {
        int safeRps = Math.max(rps, 1);
        int safeDuration = Math.max(durationSeconds, 1);
        Thread.startVirtualThread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(safeDuration);
            while (System.nanoTime() < deadline) {
                generateTraffic(safeRps);
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }
}
