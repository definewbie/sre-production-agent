package ai.sreagent.alertmanager.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * HTTP-based Alertmanager client for optional live validation.
 * Uses Java standard HttpClient. Read-only: only GET /api/v2/alerts.
 */
public class HttpAlertmanagerClient implements AlertmanagerClient {

    private final AlertmanagerClientConfig config;
    private final HttpClient httpClient;

    public HttpAlertmanagerClient(AlertmanagerClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    @Override
    public String getAlerts(Map<String, String> labelMatchers, boolean includeResolved) {
        String url = buildAlertsUrl(labelMatchers, includeResolved);
        return executeRequest(url);
    }

    private String buildAlertsUrl(Map<String, String> labelMatchers, boolean includeResolved) {
        StringBuilder sb = new StringBuilder(config.baseUrl());
        sb.append("/api/v2/alerts");

        StringJoiner paramJoiner = new StringJoiner("&");
        if (labelMatchers != null && !labelMatchers.isEmpty()) {
            // Build filter param: labelMatchers as repeated filter params
            for (Map.Entry<String, String> entry : labelMatchers.entrySet()) {
                paramJoiner.add("filter=" + URLEncoder.encode(
                        entry.getKey() + "=\"" + entry.getValue() + "\"", StandardCharsets.UTF_8));
            }
        }
        if (!includeResolved) {
            paramJoiner.add("active=true");
        }

        String params = paramJoiner.toString();
        if (!params.isEmpty()) {
            sb.append("?").append(params);
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
            throw new RuntimeException("Alertmanager HTTP error: status=" + response.statusCode()
                    + " body=" + response.body());
        } catch (IOException e) {
            throw new RuntimeException("Alertmanager HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Alertmanager HTTP request interrupted", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/v2/status"))
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
