package ai.sreagent.trace.parser;

import java.util.List;

/**
 * A parsed trace containing its spans.
 * Backend-neutral representation.
 */
public record ParsedTrace(
    String traceId,
    List<ParsedSpan> spans
) {
    public ParsedTrace {
        if (spans == null) spans = List.of();
    }

    public boolean isEmpty() {
        return spans == null || spans.isEmpty();
    }

    public int spanCount() {
        return spans != null ? spans.size() : 0;
    }

    /**
     * Find the root span (span with no parent).
     */
    public ParsedSpan rootSpan() {
        if (spans == null) return null;
        return spans.stream()
                .filter(ParsedSpan::isRoot)
                .findFirst()
                .orElse(null);
    }

    /**
     * Find all child spans of a given parent span ID.
     */
    public List<ParsedSpan> childSpansOf(String parentSpanId) {
        if (spans == null) return List.of();
        return spans.stream()
                .filter(s -> parentSpanId.equals(s.parentSpanId()))
                .toList();
    }
}
