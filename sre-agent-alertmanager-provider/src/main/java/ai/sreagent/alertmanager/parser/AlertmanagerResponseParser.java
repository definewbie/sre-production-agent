package ai.sreagent.alertmanager.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Parses Alertmanager v2 API JSON responses into AlertmanagerAlert objects.
 * Handles firing/resolved alerts, missing fields, and empty responses.
 */
public class AlertmanagerResponseParser {

    private final ObjectMapper objectMapper;

    public AlertmanagerResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public AlertmanagerResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse Alertmanager v2 alerts JSON (array of alert objects).
     * Returns list of parsed alerts. Returns empty list for null/empty input.
     */
    public List<AlertmanagerAlert> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            // Handle both array and wrapped response
            JsonNode alertsArray;
            if (root.isArray()) {
                alertsArray = root;
            } else {
                // Could be a wrapped response or single alert
                if (root.isObject() && root.has("alerts")) {
                    alertsArray = root.get("alerts");
                } else if (root.isObject()) {
                    // Single alert object — wrap in array
                    alertsArray = objectMapper.createArrayNode().add(root);
                } else {
                    return List.of();
                }
            }

            if (alertsArray == null || !alertsArray.isArray()) {
                return List.of();
            }

            List<AlertmanagerAlert> alerts = new ArrayList<>();
            for (JsonNode alertNode : alertsArray) {
                try {
                    AlertmanagerAlert alert = parseAlert(alertNode);
                    if (alert != null) {
                        alerts.add(alert);
                    }
                } catch (Exception e) {
                    // Skip malformed alerts, don't crash the whole parse
                }
            }
            return alerts;

        } catch (Exception e) {
            return List.of();
        }
    }

    private AlertmanagerAlert parseAlert(JsonNode node) {
        Map<String, String> labels = parseStringMap(node.get("labels"));
        Map<String, String> annotations = parseStringMap(node.get("annotations"));
        Instant startsAt = parseTimestamp(getTextField(node, "startsAt"));
        Instant endsAt = parseTimestamp(getTextField(node, "endsAt"));
        String fingerprint = getTextField(node, "fingerprint");

        // Parse status
        String state = parseStatus(node.get("status"));
        List<String> silencedBy = parseStringList(node.path("status").path("silencedBy"));
        List<String> inhibitedBy = parseStringList(node.path("status").path("inhibitedBy"));

        // If status.silencedBy/inhibitedBy not found, try top-level
        if (silencedBy.isEmpty() && node.has("silencedBy")) {
            silencedBy = parseStringList(node.get("silencedBy"));
        }
        if (inhibitedBy.isEmpty() && node.has("inhibitedBy")) {
            inhibitedBy = parseStringList(node.get("inhibitedBy"));
        }

        return new AlertmanagerAlert(labels, annotations, startsAt, endsAt, state,
                fingerprint, silencedBy, inhibitedBy);
    }

    private Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            map.put(field.getKey(), field.getValue().asText(""));
        }
        return map;
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(item.asText(""));
        }
        return list;
    }

    private String parseStatus(JsonNode statusNode) {
        if (statusNode == null) return "active";
        if (statusNode.isObject()) {
            return getTextField(statusNode, "state");
        }
        if (statusNode.isTextual()) {
            return statusNode.asText();
        }
        return "active";
    }

    /**
     * Parse ISO-8601 timestamp. Returns null for invalid/empty strings.
     * Handles Alertmanager's "0001-01-01T00:00:00Z" as null (zero time).
     */
    private Instant parseTimestamp(String tsStr) {
        if (tsStr == null || tsStr.isBlank()) return null;
        try {
            Instant ts = Instant.parse(tsStr);
            // Alertmanager uses zero time for "not set"
            if (ts.equals(Instant.parse("0001-01-01T00:00:00Z"))) {
                return null;
            }
            return ts;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTextField(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode f = node.get(field);
        return f != null ? f.asText(null) : null;
    }
}
