package ai.sreagent.prometheus.query;

import java.util.*;

/**
 * Registry of predefined Prometheus query templates.
 * Templates use $service and $namespace as placeholder variables.
 *
 * IMPORTANT: These are template examples for MVP.
 * Real environments may use different metric names.
 * Metric names should be customized per deployment environment.
 */
public class PrometheusQueryTemplateRegistry {

    private final Map<PrometheusQueryType, PrometheusQueryTemplate> templates;

    public PrometheusQueryTemplateRegistry() {
        this.templates = new EnumMap<>(PrometheusQueryType.class);
        registerDefaults();
    }

    private void registerDefaults() {
        // Spring Boot Actuator / Micrometer metric names (http_server_requests_seconds_*)
        register(PrometheusQueryType.ERROR_RATE,
                "sum(rate(http_server_requests_seconds_count{service=\"$service\",status=~\"5..\"}[5m]))"
                        + " / sum(rate(http_server_requests_seconds_count{service=\"$service\"}[5m]))",
                "Error rate (5xx / total requests)");

        register(PrometheusQueryType.LATENCY_P95,
                "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{service=\"$service\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\",uri!=\"/actuator/info\",uri!=\"/health\"}[5m])) by (le))",
                "P95 latency from request duration histogram");

        register(PrometheusQueryType.LATENCY_P99,
                "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{service=\"$service\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\",uri!=\"/actuator/info\",uri!=\"/health\"}[5m])) by (le))",
                "P99 latency from request duration histogram");

        // Downstream latency: use order-service latency as proxy for downstream dependency latency
        // (Spring Boot does not emit http_client_* metrics without micrometer-tracing dependency)
        register(PrometheusQueryType.DOWNSTREAM_LATENCY_P95,
                "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{service=\"$service\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\"}[5m])) by (le))",
                "P95 latency (used as downstream dependency latency proxy)");

        register(PrometheusQueryType.MEMORY_USAGE,
                "container_memory_working_set_bytes{namespace=\"$namespace\",pod=~\"$service.*\",container!=\"POD\",container!=\"\"}",
                "Container memory working set bytes");

        register(PrometheusQueryType.CPU_USAGE,
                "rate(container_cpu_usage_seconds_total{namespace=\"$namespace\",pod=~\"$service.*\",container!=\"POD\",container!=\"\"}[5m])",
                "Container CPU usage rate");

        register(PrometheusQueryType.RESTART_RATE,
                "increase(kube_pod_container_status_restarts_total{namespace=\"$namespace\",pod=~\"$service.*\"}[10m])",
                "Pod container restart count increase over 10 minutes");

        register(PrometheusQueryType.REQUEST_RATE,
                "sum(rate(http_server_requests_seconds_count{service=\"$service\"}[5m]))",
                "Total request rate (requests per second)");
    }

    public void register(PrometheusQueryType type, String template, String description) {
        templates.put(type, new PrometheusQueryTemplate(type, template, description));
    }

    public Optional<PrometheusQueryTemplate> getTemplate(PrometheusQueryType type) {
        return Optional.ofNullable(templates.get(type));
    }

    public List<PrometheusQueryTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    public Set<PrometheusQueryType> getSupportedTypes() {
        return templates.keySet();
    }
}
