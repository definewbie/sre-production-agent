package ai.sreagent.loki.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * HTTP-based Loki client for optional live validation.
 * Uses Java standard HttpClient.
 */
public class HttpLokiQueryClient implements LokiQueryClient {

    private final LokiClientConfig config;
    private final HttpClient httpClient;

    public HttpLokiQueryClient(LokiClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    @Override
    public String query(String logql, Instant time) {
        String url = buildQueryUrl(logql, time);
        return executeRequest(url);
    }

    @Override
    public String queryRange(String logql, Instant start, Instant end, Duration step) {
        String url = buildQueryRangeUrl(logql, start, end, step);
        return executeRequest(url);
    }

    private String buildQueryUrl(String logql, Instant time) {
        StringBuilder sb = new StringBuilder(config.baseUrl());
        sb.append("/loki/api/v1/query?query=").append(java.net.URLEncoder.encode(logql, java.nio.charset.StandardCharsets.UTF_8));
        if (time != null) {
            sb.append("&time=").append(time.getEpochSecond());
        }
        return sb.toString();
    }

    private String buildQueryRangeUrl(String logql, Instant start, Instant end, Duration step) {
        StringBuilder sb = new StringBuilder(config.baseUrl());
        sb.append("/loki/api/v1/query_range?query=").append(java.net.URLEncoder.encode(logql, java.nio.charset.StandardCharsets.UTF_8));
        if (start != null) {
            sb.append("&start=").append(start.getEpochSecond() * 1_000_000_000L);
        }
        if (end != null) {
            sb.append("&end=").append(end.getEpochSecond() * 1_000_000_000L);
        }
        if (step != null) {
            sb.append("&step=").append(step.getSeconds());
        }
        return sb.toString();
    }

    private String executeRequest(String url) {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(config.timeout())
                .GET();

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            reqBuilder.header(header.getKey(), header.getValue());
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new RuntimeException("Loki HTTP error: status=" + response.statusCode()
                    + " body=" + response.body());
        } catch (IOException e) {
            throw new RuntimeException("Loki HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Loki HTTP request interrupted", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/ready"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String clientName() {
        return "http";
    }
}
