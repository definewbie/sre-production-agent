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
        register(PrometheusQueryType.ERROR_RATE,
                "sum(rate(http_requests_total{service=\"$service\",status=~\"5..\"}[5m]))"
                        + " / sum(rate(http_requests_total{service=\"$service\"}[5m]))",
                "Error rate (5xx / total requests)");

        register(PrometheusQueryType.LATENCY_P95,
                "histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{service=\"$service\"}[5m])) by (le))",
                "P95 latency from request duration histogram");

        register(PrometheusQueryType.LATENCY_P99,
                "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{service=\"$service\"}[5m])) by (le))",
                "P99 latency from request duration histogram");

        register(PrometheusQueryType.DOWNSTREAM_LATENCY_P95,
                "histogram_quantile(0.95, sum(rate(http_client_request_duration_seconds_bucket{service=\"$service\"}[5m])) by (le, downstream))",
                "P95 downstream latency from client request duration histogram");

        register(PrometheusQueryType.MEMORY_USAGE,
                "container_memory_working_set_bytes{namespace=\"$namespace\",pod=~\"$service.*\"}",
                "Container memory working set bytes");

        register(PrometheusQueryType.CPU_USAGE,
                "rate(container_cpu_usage_seconds_total{namespace=\"$namespace\",pod=~\"$service.*\"}[5m])",
                "Container CPU usage rate");

        register(PrometheusQueryType.RESTART_RATE,
                "increase(kube_pod_container_status_restarts_total{namespace=\"$namespace\",pod=~\"$service.*\"}[10m])",
                "Pod container restart count increase over 10 minutes");

        register(PrometheusQueryType.REQUEST_RATE,
                "sum(rate(http_requests_total{service=\"$service\"}[5m]))",
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
