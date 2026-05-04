package ai.sreagent.trace.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Parses trace backend JSON responses into structured ParsedTrace objects.
 * Supports Jaeger-style JSON format with processes and references.
 * Backend-neutral output — parser absorbs vendor differences.
 *
 * Jaeger duration is in microseconds. Convert to milliseconds carefully.
 */
public class TraceResponseParser {

    private final ObjectMapper objectMapper;

    public TraceResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public TraceResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a trace backend JSON response into a list of ParsedTrace objects.
     * Supports Jaeger-style format: { "data": [ { "traceID": "...", "spans": [...], "processes": {...} } ] }
     * Also supports single trace format and empty responses.
     */
    public List<ParsedTrace> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            // Handle array directly: [ { traceID, spans, processes }, ... ]
            if (root.isArray()) {
                return parseTraceArray(root);
            }

            // Handle wrapped format: { "data": [ ... ] }
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                return parseTraceArray(data);
            }

            // Handle single trace: { "traceID": "...", "spans": [...] }
            if (root.has("traceID")) {
                ParsedTrace trace = parseSingleTrace(root);
                return trace != null ? List.of(trace) : List.of();
            }

            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ParsedTrace> parseTraceArray(JsonNode array) {
        List<ParsedTrace> traces = new ArrayList<>();
        for (JsonNode traceNode : array) {
            ParsedTrace trace = parseSingleTrace(traceNode);
            if (trace != null) {
                traces.add(trace);
            }
        }
        return traces;
    }

    private ParsedTrace parseSingleTrace(JsonNode traceNode) {
        if (traceNode == null) return null;

        String traceId = getTextField(traceNode, "traceID");
        if (traceId == null || traceId.isBlank()) return null;

        // Parse processes map: { "p1": { "serviceName": "order-service", ... }, ... }
        Map<String, String> processServices = parseProcesses(traceNode.get("processes"));

        // Parse spans
        JsonNode spansNode = traceNode.get("spans");
        if (spansNode == null || !spansNode.isArray()) {
            return new ParsedTrace(traceId, List.of());
        }

        List<ParsedSpan> spans = new ArrayList<>();
        for (JsonNode spanNode : spansNode) {
            ParsedSpan span = parseSpan(spanNode, traceId, processServices);
            if (span != null) {
                spans.add(span);
            }
        }

        return new ParsedTrace(traceId, spans);
    }

    private Map<String, String> parseProcesses(JsonNode processesNode) {
        if (processesNode == null || !processesNode.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = processesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String processId = entry.getKey();
            String serviceName = getTextField(entry.getValue(), "serviceName");
            result.put(processId, serviceName != null ? serviceName : "unknown");
        }
        return result;
    }

    private ParsedSpan parseSpan(JsonNode spanNode, String traceId, Map<String, String> processServices) {
        if (spanNode == null) return null;

        String spanId = getTextField(spanNode, "spanID");
        String operationName = getTextField(spanNode, "operationName");
        String processId = getTextField(spanNode, "processID");

        // Resolve service name from processes map
        String service = processServices.getOrDefault(processId != null ? processId : "", "unknown");

        // Parse parent span from references
        String parentSpanId = parseParentSpanId(spanNode.get("references"));

        // Jaeger: startTime is microseconds since epoch, duration is microseconds
        Instant startTime = parseJaegerTimestamp(getLongField(spanNode, "startTime"));
        long durationMicros = getLongField(spanNode, "duration");
        long durationMs = durationMicros / 1000;

        // Parse tags/attributes
        Map<String, String> attributes = parseTags(spanNode.get("tags"));

        // Determine status from tags
        String status = "ok";
        if ("true".equalsIgnoreCase(attributes.get("error"))) {
            status = "error";
        }

        return new ParsedSpan(
                traceId,
                spanId,
                parentSpanId,
                service,
                operationName,
                durationMs,
                startTime,
                status,
                attributes
        );
    }

    private String parseParentSpanId(JsonNode referencesNode) {
        if (referencesNode == null || !referencesNode.isArray()) {
            return null;
        }
        for (JsonNode ref : referencesNode) {
            String refType = getTextField(ref, "refType");
            if ("CHILD_OF".equals(refType)) {
                return getTextField(ref, "spanID");
            }
        }
        return null;
    }

    private Map<String, String> parseTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return Map.of();
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (JsonNode tag : tagsNode) {
            String key = getTextField(tag, "key");
            String value = getTextField(tag, "value");
            if (key != null) {
                tags.put(key, value != null ? value : "");
            }
        }
        return tags;
    }

    /**
     * Jaeger timestamps are microseconds since epoch.
     */
    private Instant parseJaegerTimestamp(long micros) {
        if (micros <= 0) return null;
        long seconds = micros / 1_000_000;
        long microsRemaining = micros % 1_000_000;
        return Instant.ofEpochSecond(seconds, microsRemaining * 1000);
    }

    private String getTextField(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode f = node.get(field);
        return f != null ? f.asText(null) : null;
    }

    private long getLongField(JsonNode node, String field) {
        if (node == null) return 0;
        JsonNode f = node.get(field);
        return f != null ? f.asLong(0) : 0;
    }
}
