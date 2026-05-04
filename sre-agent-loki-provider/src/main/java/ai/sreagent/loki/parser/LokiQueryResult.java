package ai.sreagent.loki.parser;

import java.util.List;

/**
 * Parsed result from a Loki query response.
 */
public record LokiQueryResult(
    String resultType,
    List<LokiLogEntry> entries
) {
    public LokiQueryResult {
        if (entries == null) entries = List.of();
    }

    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    public int entryCount() {
        return entries != null ? entries.size() : 0;
    }
}
