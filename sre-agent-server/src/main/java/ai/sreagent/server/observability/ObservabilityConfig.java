package ai.sreagent.server.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservabilityStatusService observabilityStatusService(
            ObservabilityProperties properties) {
        return new ObservabilityStatusService(
                properties.getPrometheusUrl(),
                properties.getAlertmanagerUrl(),
                properties.getLokiUrl(),
                properties.getTraceUrl(),
                properties.getTraceBackend(),
                properties.getGrafanaUrl()
        );
    }

    @Bean
    public EndpointHealthChecker endpointHealthChecker() {
        return new HttpEndpointHealthChecker();
    }

    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "sre-agent.observability")
    @org.springframework.context.annotation.Configuration
    public static class ObservabilityProperties {
        private String prometheusUrl = "http://localhost:9090";
        private String alertmanagerUrl = "http://localhost:9093";
        private String lokiUrl = "http://localhost:3100";
        private String traceUrl = "http://localhost:16686";
        private String traceBackend = "jaeger";
        private String grafanaUrl = "http://localhost:3000";

        public String getPrometheusUrl() { return prometheusUrl; }
        public void setPrometheusUrl(String prometheusUrl) { this.prometheusUrl = prometheusUrl; }
        public String getAlertmanagerUrl() { return alertmanagerUrl; }
        public void setAlertmanagerUrl(String alertmanagerUrl) { this.alertmanagerUrl = alertmanagerUrl; }
        public String getLokiUrl() { return lokiUrl; }
        public void setLokiUrl(String lokiUrl) { this.lokiUrl = lokiUrl; }
        public String getTraceUrl() { return traceUrl; }
        public void setTraceUrl(String traceUrl) { this.traceUrl = traceUrl; }
        public String getTraceBackend() { return traceBackend; }
        public void setTraceBackend(String traceBackend) { this.traceBackend = traceBackend; }
        public String getGrafanaUrl() { return grafanaUrl; }
        public void setGrafanaUrl(String grafanaUrl) { this.grafanaUrl = grafanaUrl; }
    }
}
