package ai.sreagent.loki.query;

/**
 * A LogQL query template with variable substitution.
 */
public record LokiQueryTemplate(
    LokiQueryType queryType,
    String template
) {
    /**
     * Build a concrete LogQL query by substituting service and namespace.
     */
    public String buildQuery(String service, String namespace) {
        String query = template;
        if (service != null) query = query.replace("$service", service);
        if (namespace != null) query = query.replace("$namespace", namespace);
        return query;
    }
}
