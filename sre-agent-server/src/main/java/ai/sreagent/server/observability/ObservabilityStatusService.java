package ai.sreagent.server.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Service that checks health of all configured observability endpoints.
 * Endpoint URLs are configured via application properties.
 */
@Service
public class ObservabilityStatusService {

    private final List<ObservabilityEndpointConfig> endpointConfigs;
    private final EndpointHealthChecker healthChecker;

    public ObservabilityStatusService(
            List<ObservabilityEndpointConfig> endpointConfigs,
            EndpointHealthChecker healthChecker) {
        this.endpointConfigs = endpointConfigs;
        this.healthChecker = healthChecker;
    }

    /**
     * Create with default configs from properties.
     */
    public ObservabilityStatusService(
            String prometheusUrl,
            String alertmanagerUrl,
            String lokiUrl,
            String traceUrl,
            String traceBackend,
            String grafanaUrl) {
        this.endpointConfigs = buildConfigs(prometheusUrl, alertmanagerUrl, lokiUrl, traceUrl, traceBackend, grafanaUrl);
        this.healthChecker = new HttpEndpointHealthChecker();
    }

    public ObservabilityStatusResponse checkAll() {
        List<ObservabilityEndpointStatus> statuses = new ArrayList<>();

        // Kubernetes check — special handling
        statuses.add(checkKubernetes());

        // HTTP endpoints
        for (ObservabilityEndpointConfig config : endpointConfigs) {
            statuses.add(healthChecker.check(config));
        }

        String overall = computeOverallStatus(statuses);
        return new ObservabilityStatusResponse(overall, Instant.now(), statuses);
    }

    private ObservabilityEndpointStatus checkKubernetes() {
        try {
            String context = new ProcessBuilder("kubectl", "config", "current-context")
                    .redirectErrorStream(true)
                    .start()
                    .inputReader()
                    .readLine();
            if (context != null && !context.isBlank()) {
                return new ObservabilityEndpointStatus(
                        "Kubernetes", "kubernetes", context.trim(),
                        "connected", 0, "Kubernetes context: " + context.trim());
            }
            return new ObservabilityEndpointStatus(
                    "Kubernetes", "kubernetes", "", "disconnected", 0, "No active Kubernetes context");
        } catch (Exception e) {
            return new ObservabilityEndpointStatus(
                    "Kubernetes", "kubernetes", "", "disconnected", 0, "kubectl not available: " + e.getMessage());
        }
    }

    static String computeOverallStatus(List<ObservabilityEndpointStatus> statuses) {
        if (statuses.isEmpty()) {
            return "unknown";
        }
        long connected = statuses.stream()
                .filter(s -> "connected".equals(s.status()))
                .count();
        long notConfigured = statuses.stream()
                .filter(s -> "not_configured".equals(s.status()))
                .count();

        long checkable = statuses.size() - notConfigured;
        if (checkable == 0) {
            return "unknown";
        }
        if (connected == checkable) {
            return "healthy";
        }
        if (connected == 0) {
            return "down";
        }
        return "partial";
    }

    static List<ObservabilityEndpointConfig> buildConfigs(
            String prometheusUrl, String alertmanagerUrl, String lokiUrl,
            String traceUrl, String traceBackend, String grafanaUrl) {
        List<ObservabilityEndpointConfig> configs = new ArrayList<>();

        if (prometheusUrl != null && !prometheusUrl.isBlank()) {
            configs.add(new ObservabilityEndpointConfig("Prometheus", "prometheus", prometheusUrl, "/-/ready"));
        }
        if (alertmanagerUrl != null && !alertmanagerUrl.isBlank()) {
            configs.add(new ObservabilityEndpointConfig("Alertmanager", "alertmanager", alertmanagerUrl, "/-/ready"));
        }
        if (lokiUrl != null && !lokiUrl.isBlank()) {
            configs.add(new ObservabilityEndpointConfig("Loki", "loki", lokiUrl, "/ready"));
        }
        if (traceUrl != null && !traceUrl.isBlank()) {
            String healthPath = "jaeger".equals(traceBackend) ? "/api/services" : "/ready";
            String name = "jaeger".equals(traceBackend) ? "Jaeger" : "Tempo";
            configs.add(new ObservabilityEndpointConfig(name, "trace", traceUrl, healthPath));
        }
        if (grafanaUrl != null && !grafanaUrl.isBlank()) {
            configs.add(new ObservabilityEndpointConfig("Grafana", "grafana", grafanaUrl, "/api/health"));
        }

        return configs;
    }

    List<ObservabilityEndpointConfig> getEndpointConfigs() {
        return endpointConfigs;
    }
}
