package ai.sreagent.loki.parser;

import java.time.Instant;
import java.util.Map;

/**
 * A single parsed log entry from a Loki query result.
 */
public record LokiLogEntry(
    Map<String, String> labels,
    Instant timestamp,
    String message
) {}
