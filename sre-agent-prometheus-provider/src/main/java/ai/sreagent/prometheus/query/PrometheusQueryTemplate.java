package ai.sreagent.prometheus.query;

/**
 * A Prometheus query template with variable substitution.
 * Variables like $service and $namespace are replaced at query time.
 */
public record PrometheusQueryTemplate(
    PrometheusQueryType queryType,
    String template,
    String description
) {

    /**
     * Build the actual PromQL query by substituting variables.
     */
    public String buildQuery(String service, String namespace) {
        return template
                .replace("$service", service != null ? service : "")
                .replace("$namespace", namespace != null ? namespace : "");
    }
}
