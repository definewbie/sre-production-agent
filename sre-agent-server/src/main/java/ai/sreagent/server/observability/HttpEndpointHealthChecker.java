package ai.sreagent.server.observability;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP-based endpoint health checker using Java standard HttpClient.
 */
public class HttpEndpointHealthChecker implements EndpointHealthChecker {

    private final HttpClient httpClient;

    public HttpEndpointHealthChecker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    HttpEndpointHealthChecker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ObservabilityEndpointStatus check(ObservabilityEndpointConfig config) {
        if (config == null) {
            return new ObservabilityEndpointStatus(
                    "unknown", "unknown", "", "not_configured", 0, "No configuration provided");
        }

        if (config.url() == null || config.url().isBlank()) {
            return new ObservabilityEndpointStatus(
                    config.name(), config.type(), "", "not_configured", 0, "URL not configured");
        }

        String checkUrl = config.fullUrl();
        long start = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 500) {
                return new ObservabilityEndpointStatus(
                        config.name(), config.type(), config.url(), "connected",
                        latency, config.name() + " ready (HTTP " + statusCode + ")");
            } else {
                return new ObservabilityEndpointStatus(
                        config.name(), config.type(), config.url(), "disconnected",
                        latency, config.name() + " returned HTTP " + statusCode);
            }

        } catch (IOException e) {
            long latency = System.currentTimeMillis() - start;
            return new ObservabilityEndpointStatus(
                    config.name(), config.type(), config.url(), "disconnected",
                    latency, config.name() + " unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ObservabilityEndpointStatus(
                    config.name(), config.type(), config.url(), "unknown",
                    0, config.name() + " check interrupted");
        }
    }
}
