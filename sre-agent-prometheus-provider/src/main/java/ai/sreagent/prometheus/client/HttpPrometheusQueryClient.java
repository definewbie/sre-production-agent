package ai.sreagent.prometheus.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * HTTP-based Prometheus query client for optional live validation.
 * Uses Java standard HttpClient — no external HTTP library required.
 */
public class HttpPrometheusQueryClient implements PrometheusQueryClient {

    private final PrometheusClientConfig config;
    private final HttpClient httpClient;

    public HttpPrometheusQueryClient(PrometheusClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    @Override
    public String query(String promql, Instant time) {
        String encodedQuery = URLEncoder.encode(promql, StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(config.baseUrl())
                .append("/api/v1/query?query=")
                .append(encodedQuery);
        if (time != null) {
            url.append("&time=").append(time.getEpochSecond());
        }
        return executeGet(url.toString());
    }

    @Override
    public String queryRange(String promql, Instant start, Instant end, Duration step) {
        String encodedQuery = URLEncoder.encode(promql, StandardCharsets.UTF_8);
        String url = config.baseUrl()
                + "/api/v1/query_range?query=" + encodedQuery
                + "&start=" + start.getEpochSecond()
                + "&end=" + end.getEpochSecond()
                + "&step=" + step.getSeconds();
        return executeGet(url);
    }

    @Override
    public String clientName() {
        return "http";
    }

    @Override
    public boolean isAvailable() {
        try {
            executeGet(config.baseUrl() + "/api/v1/query?query=up");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String executeGet(String url) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(config.timeout())
                .GET();

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Prometheus returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to Prometheus at " + config.baseUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while querying Prometheus", e);
        }
    }
}
