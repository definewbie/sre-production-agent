package ai.sreagent.trace.query;

import java.util.*;

/**
 * Registry of predefined trace query templates.
 * Templates describe query intents rather than backend-specific query syntax.
 */
public class TraceQueryTemplateRegistry {

    private final Map<TraceQueryType, TraceQueryTemplate> templates;

    public TraceQueryTemplateRegistry() {
        this.templates = new EnumMap<>(TraceQueryType.class);
        registerDefaults();
    }

    private void registerDefaults() {
        register(TraceQueryType.DOWNSTREAM_SLOW_SPAN,
                "Find traces where service has child spans exceeding latency threshold");

        register(TraceQueryType.ERROR_SPAN,
                "Find traces with span status error or error=true tag");

        register(TraceQueryType.ROOT_SPAN_SLOW,
                "Find traces where root span duration exceeds threshold");

        register(TraceQueryType.DEPENDENCY_PATH,
                "Find traces involving service and its downstream dependency path");

        register(TraceQueryType.TIMEOUT_SPAN,
                "Find spans with timeout-related attributes or operation names");
    }

    public void register(TraceQueryType type, String description) {
        templates.put(type, new TraceQueryTemplate(type, description));
    }

    public Optional<TraceQueryTemplate> getTemplate(TraceQueryType type) {
        return Optional.ofNullable(templates.get(type));
    }

    public List<TraceQueryTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    public Set<TraceQueryType> getSupportedTypes() {
        return templates.keySet();
    }
}
