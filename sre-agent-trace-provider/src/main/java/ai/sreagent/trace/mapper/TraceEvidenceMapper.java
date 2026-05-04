package ai.sreagent.trace.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.trace.parser.ParsedSpan;
import ai.sreagent.trace.parser.ParsedTrace;
import ai.sreagent.trace.query.TraceQueryType;

import java.time.Instant;
import java.util.*;

/**
 * Maps parsed traces/spans to semantic Evidence objects.
 * Uses span duration, status, parent/child relationships, and attributes
 * to determine evidence type and strength.
 */
public class TraceEvidenceMapper {

    // Duration thresholds in milliseconds
    private static final long SLOW_SPAN_THRESHOLD_MS = 1000;
    private static final double CHILD_DOMINANCE_RATIO = 0.70;

    // Strength values per evidence type
    private static final Map<String, Double> STRENGTH_MAP = Map.of(
            TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW, 0.85,
            TraceEvidenceTypes.TRACE_ERROR_SPAN, 0.80,
            TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW, 0.70,
            TraceEvidenceTypes.TRACE_DEPENDENCY_PATH, 0.65,
            TraceEvidenceTypes.TRACE_TIMEOUT_SPAN, 0.85,
            TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY, 0.90
    );

    /**
     * Map parsed traces to Evidence based on the query type.
     * Returns empty list if no relevant evidence found.
     * Returns trace_no_signal evidence if traces are empty.
     */
    public List<Evidence> map(TraceQueryType queryType,
                               List<ParsedTrace> traces,
                               String incidentId,
                               String service,
                               String namespace,
                               Instant startTime,
                               Instant endTime) {
        if (traces == null || traces.isEmpty()) {
            return List.of(buildNoSignalEvidence(queryType, incidentId, service, namespace, startTime, endTime));
        }

        List<Evidence> evidence = new ArrayList<>();

        for (ParsedTrace trace : traces) {
            evidence.addAll(analyzeTrace(queryType, trace, incidentId, service, namespace, startTime, endTime));
        }

        // If no specific evidence was generated, return no-signal
        if (evidence.isEmpty()) {
            return List.of(buildNoSignalEvidence(queryType, incidentId, service, namespace, startTime, endTime));
        }

        return evidence;
    }

    private List<Evidence> analyzeTrace(TraceQueryType queryType,
                                          ParsedTrace trace,
                                          String incidentId,
                                          String service,
                                          String namespace,
                                          Instant startTime,
                                          Instant endTime) {
        List<Evidence> evidence = new ArrayList<>();
        ParsedSpan rootSpan = trace.rootSpan();

        for (ParsedSpan span : trace.spans()) {
            switch (queryType) {
                case DOWNSTREAM_SLOW_SPAN -> {
                    if (!span.isRoot() && span.durationMs() >= SLOW_SPAN_THRESHOLD_MS) {
                        evidence.add(buildSpanEvidence(TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW,
                                span, rootSpan, incidentId, service, namespace));
                    }
                }
                case ERROR_SPAN -> {
                    if (span.hasError()) {
                        evidence.add(buildSpanEvidence(TraceEvidenceTypes.TRACE_ERROR_SPAN,
                                span, rootSpan, incidentId, service, namespace));
                    }
                }
                case ROOT_SPAN_SLOW -> {
                    if (span.isRoot() && span.durationMs() >= SLOW_SPAN_THRESHOLD_MS) {
                        evidence.add(buildSpanEvidence(TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW,
                                span, null, incidentId, service, namespace));
                    }
                }
                case DEPENDENCY_PATH -> {
                    if (!span.isRoot() && !span.service().equals(service != null ? service : "")) {
                        evidence.add(buildSpanEvidence(TraceEvidenceTypes.TRACE_DEPENDENCY_PATH,
                                span, rootSpan, incidentId, service, namespace));
                    }
                }
                case TIMEOUT_SPAN -> {
                    if (span.hasTimeout()) {
                        evidence.add(buildSpanEvidence(TraceEvidenceTypes.TRACE_TIMEOUT_SPAN,
                                span, rootSpan, incidentId, service, namespace));
                    }
                }
            }
        }

        // Check for child-span-dominates-latency regardless of query type
        if (rootSpan != null && rootSpan.durationMs() > 0) {
            for (ParsedSpan child : trace.childSpansOf(rootSpan.spanId())) {
                double ratio = (double) child.durationMs() / rootSpan.durationMs();
                if (ratio >= CHILD_DOMINANCE_RATIO) {
                    evidence.add(buildDominanceEvidence(child, rootSpan, ratio,
                            incidentId, service, namespace));
                }
            }
        }

        return evidence;
    }

    private Evidence buildSpanEvidence(String evidenceType,
                                         ParsedSpan span,
                                         ParsedSpan rootSpan,
                                         String incidentId,
                                         String service,
                                         String namespace) {
        double strength = STRENGTH_MAP.getOrDefault(evidenceType, 0.70);
        String content = buildContent(evidenceType, span, rootSpan, service);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("traceId", span.traceId());
        attrs.put("spanId", span.spanId());
        attrs.put("parentSpanId", span.parentSpanId() != null ? span.parentSpanId() : "");
        attrs.put("service", span.service());
        attrs.put("operation", span.operation() != null ? span.operation() : "");
        attrs.put("durationMs", span.durationMs());
        if (rootSpan != null) {
            attrs.put("rootDurationMs", rootSpan.durationMs());
        }
        attrs.put("status", span.status() != null ? span.status() : "ok");
        attrs.put("attributes", span.attributes());
        if (namespace != null) attrs.put("namespace", namespace);

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                TraceEvidenceTypes.SOURCE,
                evidenceType,
                service,
                span.startTime() != null ? span.startTime() : Instant.now(),
                content,
                attrs,
                Math.round(strength * 100.0) / 100.0
        );
    }

    private Evidence buildDominanceEvidence(ParsedSpan child,
                                              ParsedSpan rootSpan,
                                              double ratio,
                                              String incidentId,
                                              String service,
                                              String namespace) {
        String content = String.format(
                "Trace shows %s span consumed %d%% of %s request latency.",
                child.service(),
                Math.round(ratio * 100),
                rootSpan.service()
        );

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("traceId", child.traceId());
        attrs.put("spanId", child.spanId());
        attrs.put("service", child.service());
        attrs.put("operation", child.operation() != null ? child.operation() : "");
        attrs.put("durationMs", child.durationMs());
        attrs.put("rootDurationMs", rootSpan.durationMs());
        attrs.put("childDurationRatio", Math.round(ratio * 100.0) / 100.0);
        attrs.put("rootService", rootSpan.service());
        if (namespace != null) attrs.put("namespace", namespace);

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                TraceEvidenceTypes.SOURCE,
                TraceEvidenceTypes.TRACE_CHILD_SPAN_DOMINATES_LATENCY,
                service,
                child.startTime() != null ? child.startTime() : Instant.now(),
                content,
                attrs,
                0.90
        );
    }

    private String buildContent(String evidenceType, ParsedSpan span, ParsedSpan rootSpan, String service) {
        return switch (evidenceType) {
            case TraceEvidenceTypes.TRACE_DOWNSTREAM_SPAN_SLOW ->
                    String.format("Trace shows slow downstream span: %s %s took %dms.",
                            span.service(), span.operation(), span.durationMs());
            case TraceEvidenceTypes.TRACE_ERROR_SPAN ->
                    String.format("Trace shows error span: %s %s (status=%s).",
                            span.service(), span.operation(), span.status());
            case TraceEvidenceTypes.TRACE_ROOT_SPAN_SLOW ->
                    String.format("Trace shows slow root span: %s %s took %dms.",
                            span.service(), span.operation(), span.durationMs());
            case TraceEvidenceTypes.TRACE_DEPENDENCY_PATH ->
                    String.format("Trace shows dependency path: %s -> %s (%s).",
                            rootSpan != null ? rootSpan.service() : "unknown",
                            span.service(), span.operation());
            case TraceEvidenceTypes.TRACE_TIMEOUT_SPAN ->
                    String.format("Trace shows timeout span: %s %s (duration=%dms).",
                            span.service(), span.operation(), span.durationMs());
            default -> "Trace evidence: " + span.service() + " " + span.operation();
        };
    }

    private Evidence buildNoSignalEvidence(TraceQueryType queryType, String incidentId,
                                             String service, String namespace,
                                             Instant startTime, Instant endTime) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("queryType", queryType.getKey());
        attrs.put("service", service != null ? service : "unknown");
        if (namespace != null) attrs.put("namespace", namespace);
        if (startTime != null) attrs.put("startTime", startTime.toString());
        if (endTime != null) attrs.put("endTime", endTime.toString());

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                TraceEvidenceTypes.SOURCE,
                TraceEvidenceTypes.TRACE_NO_SIGNAL,
                service,
                Instant.now(),
                "Trace backend returned no traces for " + queryType.getKey() + " query on " + service + ".",
                attrs,
                0.0
        );
    }
}
