package ai.sreagent.trace.client;

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
 * HTTP-based trace backend client for optional live validation.
 * Supports Jaeger and Tempo-style APIs.
 * Uses Java standard HttpClient.
 */
public class HttpTraceQueryClient implements TraceQueryClient {

    private final TraceClientConfig config;
    private final HttpClient httpClient;

    public HttpTraceQueryClient(TraceClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    @Override
    public String findTraces(String service, Instant start, Instant end, int limit) {
        String url = switch (config.backendType().toLowerCase()) {
            case "jaeger" -> buildJaegerFindTracesUrl(service, start, end, limit);
            case "tempo" -> buildTempoFindTracesUrl(service, start, end, limit);
            default -> buildJaegerFindTracesUrl(service, start, end, limit);
        };
        return executeRequest(url);
    }

    @Override
    public String getTrace(String traceId) {
        String url = switch (config.backendType().toLowerCase()) {
            case "jaeger" -> config.baseUrl() + "/api/traces/" + encode(traceId);
            case "tempo" -> config.baseUrl() + "/api/traces/" + encode(traceId);
            default -> config.baseUrl() + "/api/traces/" + encode(traceId);
        };
        return executeRequest(url);
    }

    private String buildJaegerFindTracesUrl(String service, Instant start, Instant end, int limit) {
        StringBuilder sb = new StringBuilder(config.baseUrl());
        sb.append("/api/traces?service=").append(encode(service));
        if (start != null) {
            sb.append("&start=").append(start.toEpochMilli() * 1000); // Jaeger uses microseconds
        }
        if (end != null) {
            sb.append("&end=").append(end.toEpochMilli() * 1000);
        }
        if (limit > 0) {
            sb.append("&limit=").append(limit);
        }
        return sb.toString();
    }

    private String buildTempoFindTracesUrl(String service, Instant start, Instant end, int limit) {
        StringBuilder sb = new StringBuilder(config.baseUrl());
        sb.append("/api/search?tags=service%3D").append(encode(service));
        if (start != null) {
            sb.append("&start=").append(start.getEpochSecond());
        }
        if (end != null) {
            sb.append("&end=").append(end.getEpochSecond());
        }
        if (limit > 0) {
            sb.append("&limit=").append(limit);
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
            throw new RuntimeException("Trace backend HTTP error: status=" + response.statusCode()
                    + " body=" + response.body());
        } catch (IOException e) {
            throw new RuntimeException("Trace backend HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Trace backend HTTP request interrupted", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl()))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String clientName() {
        return "http";
    }
}
