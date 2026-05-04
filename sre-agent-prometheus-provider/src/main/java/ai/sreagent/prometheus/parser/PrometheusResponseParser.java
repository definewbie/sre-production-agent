package ai.sreagent.prometheus.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Parses Prometheus API JSON responses into structured query results.
 * Handles both instant vector and range vector responses.
 */
public class PrometheusResponseParser {

    private final ObjectMapper objectMapper;

    public PrometheusResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public PrometheusResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a Prometheus API response JSON string.
     * Supports both /api/v1/query (vector) and /api/v1/query_range (matrix) responses.
     *
     * @param json raw Prometheus API response
     * @return parsed query result
     */
    public PrometheusQueryResult parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Check status
            String status = root.path("status").asText("");
            if (!"success".equals(status)) {
                String errorType = root.path("errorType").asText("unknown");
                String error = root.path("error").asText("unknown error");
                return new PrometheusQueryResult("error", List.of());
            }

            JsonNode data = root.path("data");
            String resultType = data.path("resultType").asText("");
            JsonNode results = data.path("result");

            if (!results.isArray() || results.isEmpty()) {
                return new PrometheusQueryResult(resultType, List.of());
            }

            List<PrometheusSample> samples = new ArrayList<>();
            for (JsonNode result : results) {
                JsonNode metric = result.path("metric");
                Map<String, String> labels = parseLabels(metric);

                // Handle instant vector: "value": [timestamp, "value"]
                JsonNode valueNode = result.path("value");
                if (valueNode.isArray()) {
                    PrometheusSample sample = parseSampleValue(labels, valueNode);
                    if (sample != null) {
                        samples.add(sample);
                    }
                    continue;
                }

                // Handle range vector: "values": [[timestamp, "value"], ...]
                JsonNode valuesNode = result.path("values");
                if (valuesNode.isArray()) {
                    for (JsonNode v : valuesNode) {
                        PrometheusSample sample = parseSampleValue(labels, v);
                        if (sample != null) {
                            samples.add(sample);
                        }
                    }
                }
            }

            return new PrometheusQueryResult(resultType, samples);
        } catch (Exception e) {
            return new PrometheusQueryResult("error", List.of());
        }
    }

    private Map<String, String> parseLabels(JsonNode metric) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metric != null && metric.isObject()) {
            metric.fields().forEachRemaining(entry ->
                    labels.put(entry.getKey(), entry.getValue().asText("")));
        }
        return labels;
    }

    private PrometheusSample parseSampleValue(Map<String, String> labels, JsonNode valueNode) {
        if (!valueNode.isArray() || valueNode.size() != 2) {
            return null;
        }

        double timestampEpoch = valueNode.get(0).asDouble();
        String valueStr = valueNode.get(1).asText("");

        double value = parseDoubleSafe(valueStr);
        if (Double.isNaN(value)) {
            // Return NaN samples with NaN value — let the caller decide
            return new PrometheusSample(labels, Instant.ofEpochSecond((long) timestampEpoch), value);
        }

        return new PrometheusSample(labels, Instant.ofEpochSecond((long) timestampEpoch), value);
    }

    /**
     * Parse a double value from Prometheus response, handling special values.
     * Returns NaN for "+Inf", "-Inf", "NaN", and unparseable values.
     */
    private double parseDoubleSafe(String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
