package ai.sreagent.trace.query;

/**
 * Types of trace queries the Trace provider can execute.
 */
public enum TraceQueryType {

    DOWNSTREAM_SLOW_SPAN("downstream_slow_span"),
    ERROR_SPAN("error_span"),
    ROOT_SPAN_SLOW("root_span_slow"),
    DEPENDENCY_PATH("dependency_path"),
    TIMEOUT_SPAN("timeout_span");

    private final String key;

    TraceQueryType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static TraceQueryType fromString(String name) {
        if (name == null || name.isBlank()) return null;
        for (TraceQueryType t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.key.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
