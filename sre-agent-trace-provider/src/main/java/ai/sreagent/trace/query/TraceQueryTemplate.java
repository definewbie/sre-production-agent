package ai.sreagent.trace.query;

/**
 * A trace query template describing the query intent.
 * Trace queries are intent-based rather than query-language-based
 * because different backends (Jaeger, Tempo, etc.) use different APIs.
 */
public record TraceQueryTemplate(
    TraceQueryType queryType,
    String description
) {}
