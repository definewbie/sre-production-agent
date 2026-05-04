package ai.sreagent.loki.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.loki.parser.LokiLogEntry;
import ai.sreagent.loki.parser.LokiQueryResult;
import ai.sreagent.loki.query.LokiQueryType;

import java.time.Instant;
import java.util.*;

/**
 * Maps Loki log query results to semantic Evidence objects.
 * Uses match count and log content to determine evidence type and strength.
 */
public class LokiEvidenceMapper {

    // Strength values per query type
    private static final Map<LokiQueryType, Double> DEFAULT_STRENGTHS = Map.of(
            LokiQueryType.TIMEOUT_ERROR, 0.75,
            LokiQueryType.DOWNSTREAM_TIMEOUT, 0.85,
            LokiQueryType.EXCEPTION_LOGS, 0.70,
            LokiQueryType.CRASH_LOGS, 0.90,
            LokiQueryType.OOM_LOGS, 0.90,
            LokiQueryType.DB_CONNECTION_TIMEOUT, 0.85,
            LokiQueryType.RETRY_EXHAUSTED, 0.80,
            LokiQueryType.HTTP_5XX_LOGS, 0.75
    );

    private static final int MAX_SAMPLE_MESSAGES = 3;

    /**
     * Map a Loki query result to a list of Evidence objects.
     * Returns empty list if no log entries match.
     * Returns log_no_signal evidence if result is empty.
     */
    public List<Evidence> map(LokiQueryType queryType,
                               LokiQueryResult result,
                               String logql,
                               String incidentId,
                               String service,
                               String namespace,
                               Instant startTime,
                               Instant endTime) {
        if (result.isEmpty()) {
            return List.of(buildNoSignalEvidence(queryType, logql, incidentId, service, namespace, startTime, endTime));
        }

        // Build one evidence per result, aggregating entries
        return List.of(buildLogEvidence(queryType, result, logql, incidentId, service, namespace, startTime, endTime));
    }

    private Evidence buildLogEvidence(LokiQueryType queryType,
                                       LokiQueryResult result,
                                       String logql,
                                       String incidentId,
                                       String service,
                                       String namespace,
                                       Instant startTime,
                                       Instant endTime) {
        int matchCount = result.entryCount();
        List<LokiLogEntry> entries = result.entries();

        // Collect sample messages (max 3)
        List<String> sampleMessages = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_SAMPLE_MESSAGES, entries.size()); i++) {
            sampleMessages.add(entries.get(i).message());
        }

        // Find time range
        Instant firstTs = null;
        Instant lastTs = null;
        for (LokiLogEntry entry : entries) {
            if (entry.timestamp() != null) {
                if (firstTs == null || entry.timestamp().isBefore(firstTs)) firstTs = entry.timestamp();
                if (lastTs == null || entry.timestamp().isAfter(lastTs)) lastTs = entry.timestamp();
            }
        }

        // Stream labels from first entry
        Map<String, String> streamLabels = entries.isEmpty() ? Map.of() : entries.get(0).labels();

        String evidenceType = resolveEvidenceType(queryType);
        double strength = DEFAULT_STRENGTHS.getOrDefault(queryType, 0.70);
        String content = buildContent(queryType, service, matchCount);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("queryType", queryType.getKey());
        attrs.put("logql", logql != null ? logql : "");
        attrs.put("matchCount", matchCount);
        attrs.put("sampleMessages", sampleMessages);
        attrs.put("service", service != null ? service : "unknown");
        attrs.put("namespace", namespace != null ? namespace : "default");
        if (startTime != null) attrs.put("startTime", startTime.toString());
        if (endTime != null) attrs.put("endTime", endTime.toString());
        attrs.put("streamLabels", streamLabels);
        if (firstTs != null) attrs.put("firstTimestamp", firstTs.toString());
        if (lastTs != null) attrs.put("lastTimestamp", lastTs.toString());

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                LokiEvidenceTypes.SOURCE,
                evidenceType,
                service,
                firstTs != null ? firstTs : Instant.now(),
                content,
                attrs,
                Math.round(strength * 100.0) / 100.0
        );
    }

    private String resolveEvidenceType(LokiQueryType queryType) {
        return switch (queryType) {
            case TIMEOUT_ERROR -> LokiEvidenceTypes.LOG_TIMEOUT_ERROR;
            case DOWNSTREAM_TIMEOUT -> LokiEvidenceTypes.LOG_DOWNSTREAM_TIMEOUT;
            case EXCEPTION_LOGS -> LokiEvidenceTypes.LOG_EXCEPTION_SPIKE;
            case CRASH_LOGS -> LokiEvidenceTypes.LOG_CRASH_LOOP;
            case OOM_LOGS -> LokiEvidenceTypes.LOG_OOM_MESSAGE;
            case DB_CONNECTION_TIMEOUT -> LokiEvidenceTypes.LOG_DB_CONNECTION_TIMEOUT;
            case RETRY_EXHAUSTED -> LokiEvidenceTypes.LOG_RETRY_EXHAUSTED;
            case HTTP_5XX_LOGS -> LokiEvidenceTypes.LOG_HTTP_5XX;
        };
    }

    private String buildContent(LokiQueryType queryType, String service, int matchCount) {
        String svc = service != null ? service : "unknown";
        return switch (queryType) {
            case TIMEOUT_ERROR -> "Loki logs show " + matchCount + " timeout error(s) for " + svc + ".";
            case DOWNSTREAM_TIMEOUT -> "Loki logs show " + matchCount + " downstream timeout(s) for " + svc + " calls to dependencies.";
            case EXCEPTION_LOGS -> "Loki logs show " + matchCount + " exception/error log entries for " + svc + ".";
            case CRASH_LOGS -> "Loki logs show " + matchCount + " crash/panic/fatal log entries for " + svc + ".";
            case OOM_LOGS -> "Loki logs show " + matchCount + " OOM-related log entries for " + svc + ".";
            case DB_CONNECTION_TIMEOUT -> "Loki logs show " + matchCount + " database connection timeout log entries for " + svc + ".";
            case RETRY_EXHAUSTED -> "Loki logs show " + matchCount + " retry exhausted log entries for " + svc + ".";
            case HTTP_5XX_LOGS -> "Loki logs show " + matchCount + " HTTP 5xx log entries for " + svc + ".";
        };
    }

    private Evidence buildNoSignalEvidence(LokiQueryType queryType, String logql, String incidentId,
                                            String service, String namespace, Instant startTime, Instant endTime) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("queryType", queryType.getKey());
        attrs.put("logql", logql != null ? logql : "");
        attrs.put("service", service != null ? service : "unknown");
        attrs.put("namespace", namespace != null ? namespace : "default");
        if (startTime != null) attrs.put("startTime", startTime.toString());
        if (endTime != null) attrs.put("endTime", endTime.toString());

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                LokiEvidenceTypes.SOURCE,
                LokiEvidenceTypes.LOG_NO_SIGNAL,
                service,
                Instant.now(),
                "Loki returned no log entries for " + queryType.getKey() + " query on " + service + ".",
                attrs,
                0.0
        );
    }
}
