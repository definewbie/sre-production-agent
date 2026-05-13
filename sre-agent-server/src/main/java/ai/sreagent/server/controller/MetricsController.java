package ai.sreagent.server.controller;

import ai.sreagent.prometheus.client.HttpPrometheusQueryClient;
import ai.sreagent.prometheus.client.PrometheusClientConfig;
import ai.sreagent.server.observability.ObservabilityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 实时服务指标查询 API。
 *
 * 从 Prometheus 拉取 demo namespace 下各服务的 errorRate / p95Latency / rps / saturation / restarts，
 * 返回前端 ServiceHealthOverview 可直接使用的结构化数据。
 *
 * Prometheus 不可达时 source="unavailable"，前端保持现有 mock 降级逻辑。
 */
@RestController
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String NAMESPACE = "demo";
    private static final List<String> SERVICE_NAMES = List.of("order-service", "payment-service", "inventory-service");

    private final String prometheusUrl;
    private final HttpPrometheusQueryClient promClient;

    public MetricsController(ObservabilityConfig.ObservabilityProperties obsProps) {
        this.prometheusUrl = obsProps.getPrometheusUrl();
        var config = new PrometheusClientConfig(prometheusUrl, Duration.ofSeconds(10), Map.of());
        this.promClient = new HttpPrometheusQueryClient(config);
    }

    /** GET /api/metrics/services — 实时指标汇总 */
    @GetMapping("/api/metrics/services")
    public ResponseEntity<Map<String, Object>> getServicesMetrics() {
        if (!promClient.isAvailable()) {
            log.warn("Prometheus at {} is unreachable, returning unavailable", prometheusUrl);
            return ResponseEntity.ok(Map.of(
                "source", "unavailable",
                "checkedAt", Instant.now().toString(),
                "services", Map.of()
            ));
        }

        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        for (String svc : SERVICE_NAMES) {
            try {
                services.put(svc, queryServiceMetrics(svc));
            } catch (Exception e) {
                log.warn("Failed to query metrics for {}: {}", svc, e.getMessage());
                services.put(svc, Map.of("error", e.getMessage()));
            }
        }

        return ResponseEntity.ok(Map.of(
            "source", "real",
            "checkedAt", Instant.now().toString(),
            "services", services
        ));
    }

    private Map<String, Object> queryServiceMetrics(String service) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // ── Error Rate ──
        double errorRate = queryScalar(
            "sum(rate(http_server_requests_seconds_count{namespace=\"" + NAMESPACE +
            "\", outcome=\"SERVER_ERROR\", service=\"" + service + "\"}[5m])) / " +
            "sum(rate(http_server_requests_seconds_count{namespace=\"" + NAMESPACE +
            "\", service=\"" + service + "\"}[5m])) * 100"
        );
        // Error rate trend: compare 5m vs 1h
        double errorRatePrev = queryScalar(
            "sum(rate(http_server_requests_seconds_count{namespace=\"" + NAMESPACE +
            "\", outcome=\"SERVER_ERROR\", service=\"" + service + "\"}[1h] offset 5m)) / " +
            "sum(rate(http_server_requests_seconds_count{namespace=\"" + NAMESPACE +
            "\", service=\"" + service + "\"}[1h] offset 5m)) * 100"
        );
        metrics.put("errorRate", formatPercent(errorRate));
        if (errorRatePrev > 0) {
            double trend = errorRatePrev > 0
                ? ((errorRate - errorRatePrev) / errorRatePrev) * 100
                : (errorRate > 0 ? 999 : 0);
            metrics.put("errorRateTrend", formatTrend(trend));
            metrics.put("errorRateDirection", errorRate > errorRatePrev ? "up" : "down");
        } else {
            metrics.put("errorRateTrend", "0%");
            metrics.put("errorRateDirection", errorRate > 0 ? "up" : "down");
        }

        // ── P95 Latency (business endpoints only) ──
        double p95 = queryScalar(
            "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{" +
            "namespace=\"" + NAMESPACE + "\", service=\"" + service + "\", " +
            "uri!~\".*actuator.*|.*health.*|.*fault-config.*\"}[5m])) by (le))"
        );
        double p95Prev = queryScalar(
            "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{" +
            "namespace=\"" + NAMESPACE + "\", service=\"" + service + "\", " +
            "uri!~\".*actuator.*|.*health.*|.*fault-config.*\"}[1h] offset 5m)) by (le))"
        );
        metrics.put("p95Latency", formatDuration(p95));
        if (p95Prev > 0) {
            double trend = ((p95 - p95Prev) / p95Prev) * 100;
            metrics.put("p95Trend", formatTrend(trend));
            metrics.put("p95Direction", p95 > p95Prev ? "up" : "down");
        } else {
            metrics.put("p95Trend", "0%");
            metrics.put("p95Direction", "down");
        }

        // ── RPS (business endpoints, 1m rate) ──
        double rps = queryScalar(
            "sum(rate(http_server_requests_seconds_count{namespace=\"" + NAMESPACE +
            "\", service=\"" + service + "\", " +
            "uri!~\".*actuator.*|.*health.*|.*fault-config.*\"}[1m]))"
        );
        metrics.put("rps", round2(rps));

        // ── CPU Saturation (% of requested) ──
        double cpuUsage = queryScalar(
            "sum(rate(container_cpu_usage_seconds_total{" +
            "namespace=\"" + NAMESPACE + "\", container=\"" + service + "\"}[5m]))"
        );
        double cpuRequest = queryScalar(
            "kube_pod_container_resource_requests{" +
            "namespace=\"" + NAMESPACE + "\", container=\"" + service + "\", resource=\"cpu\"}"
        );
        double saturation = cpuRequest > 0 ? (cpuUsage / cpuRequest) * 100 : 0;
        metrics.put("saturation", (int) Math.round(Math.min(saturation, 100)));

        // ── Restarts ──
        double restarts = queryScalar(
            "kube_pod_container_status_restarts_total{" +
            "namespace=\"" + NAMESPACE + "\", container=\"" + service + "\"}"
        );
        metrics.put("restarts", (int) restarts);

        return metrics;
    }

    // ── Prometheus query helpers ──

    /** Execute a PromQL instant query expecting a single scalar value. Returns 0 on any failure. */
    private double queryScalar(String promql) {
        try {
            String raw = promClient.query(promql, null);
            JsonNode root = MAPPER.readTree(raw);
            JsonNode result = root.path("data").path("result");
            if (result.isArray() && !result.isEmpty()) {
                JsonNode value = result.get(0).path("value");
                if (value.isArray() && value.size() >= 2) {
                    return value.get(1).asDouble();
                }
            }
            return 0;
        } catch (Exception e) {
            log.trace("queryScalar failed for promql={}: {}", promql, e.getMessage());
            return 0;
        }
    }

    // ── Formatting ──

    private static String formatPercent(double v) {
        if (v < 0.01) return "0%";
        if (v < 1) return String.format("%.2f%%", v);
        if (v < 100) return String.format("%.1f%%", v);
        return String.format("%.0f%%", v);
    }

    private static String formatTrend(double v) {
        double abs = Math.abs(v);
        String prefix = v >= 0 ? "+" : "";
        if (abs >= 1000) return prefix + "—";
        if (abs >= 100) return prefix + String.format("%.0f%%", abs);
        return prefix + String.format("%.1f%%", abs);
    }

    private static String formatDuration(double seconds) {
        if (seconds < 0.001) return "<0.001s";
        if (seconds < 1) return String.format("%.0fms", seconds * 1000);
        if (seconds < 10) return String.format("%.3fs", seconds);
        return String.format("%.2fs", seconds);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
