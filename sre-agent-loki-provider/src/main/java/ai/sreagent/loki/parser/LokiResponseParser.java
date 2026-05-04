package ai.sreagent.loki.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Parses Loki API JSON responses into structured LokiQueryResult objects.
 * Handles streams result type, empty results, error responses,
 * and safely converts nanosecond timestamps.
 */
public class LokiResponseParser {

    private final ObjectMapper objectMapper;

    public LokiResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public LokiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a Loki API JSON response into a LokiQueryResult.
     */
    public LokiQueryResult parse(String json) {
        if (json == null || json.isBlank()) {
            return new LokiQueryResult("empty", List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            // Check for error response
            String status = getTextField(root, "status");
            if ("error".equals(status)) {
                String errorType = getTextField(root, "errorType");
                String errorMsg = getTextField(root, "error");
                return new LokiQueryResult("error", List.of());
            }

            JsonNode data = root.get("data");
            if (data == null) {
                return new LokiQueryResult("empty", List.of());
            }

            String resultType = getTextField(data, "resultType");
            JsonNode results = data.get("result");

            if (results == null || !results.isArray() || results.isEmpty()) {
                return new LokiQueryResult(resultType != null ? resultType : "streams", List.of());
            }

            List<LokiLogEntry> entries = new ArrayList<>();
            for (JsonNode streamNode : results) {
                Map<String, String> streamLabels = parseStreamLabels(streamNode.get("stream"));
                JsonNode values = streamNode.get("values");

                if (values != null && values.isArray()) {
                    for (JsonNode valuePair : values) {
                        if (valuePair.isArray() && valuePair.size() >= 2) {
                            Instant timestamp = parseNanosecondTimestamp(valuePair.get(0).asText());
                            String message = valuePair.get(1).asText("");
                            entries.add(new LokiLogEntry(streamLabels, timestamp, message));
                        }
                    }
                }
            }

            return new LokiQueryResult(resultType, entries);

        } catch (Exception e) {
            return new LokiQueryResult("error", List.of());
        }
    }

    private Map<String, String> parseStreamLabels(JsonNode streamNode) {
        if (streamNode == null || !streamNode.isObject()) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = streamNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            labels.put(field.getKey(), field.getValue().asText(""));
        }
        return labels;
    }

    /**
     * Loki timestamps are nanosecond strings like "1714292400000000000".
     * Convert to Instant by dividing by 1_000_000_000.
     */
    private Instant parseNanosecondTimestamp(String tsStr) {
        if (tsStr == null || tsStr.isBlank()) {
            return null;
        }
        try {
            long nanos = Long.parseLong(tsStr);
            long seconds = nanos / 1_000_000_000L;
            long nanoAdjustment = nanos % 1_000_000_000L;
            return Instant.ofEpochSecond(seconds, nanoAdjustment);
        } catch (NumberFormatException e) {
            // Try as epoch seconds
            try {
                return Instant.ofEpochSecond(Long.parseLong(tsStr));
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    private String getTextField(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode f = node.get(field);
        return f != null ? f.asText(null) : null;
    }
}
