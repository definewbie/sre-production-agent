package ai.sreagent.trace.parser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single parsed span from a trace backend response.
 * Backend-neutral representation.
 */
public record ParsedSpan(
    String traceId,
    String spanId,
    String parentSpanId,
    String service,
    String operation,
    long durationMs,
    Instant startTime,
    String status,
    Map<String, String> attributes
) {
    /**
     * @return true if this span has no parent (root span)
     */
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isBlank();
    }

    /**
     * @return true if this span indicates an error
     */
    public boolean hasError() {
        if ("error".equalsIgnoreCase(status)) return true;
        if (attributes != null) {
            String err = attributes.get("error");
            if ("true".equalsIgnoreCase(err)) return true;
        }
        return false;
    }

    /**
     * @return true if span has timeout-related attributes
     */
    public boolean hasTimeout() {
        if (attributes == null) return false;
        String timeout = attributes.get("timeout");
        if ("true".equalsIgnoreCase(timeout)) return true;
        if (operation != null && operation.toLowerCase().contains("timeout")) return true;
        return false;
    }
}
