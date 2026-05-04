package ai.sreagent.loki.query;

import java.util.*;

/**
 * Registry of LogQL query templates for known log patterns.
 * Templates are MVP examples — real environments may use different label names.
 */
public class LokiQueryTemplateRegistry {

    private final Map<LokiQueryType, LokiQueryTemplate> templates;

    public LokiQueryTemplateRegistry() {
        templates = new EnumMap<>(LokiQueryType.class);
        registerDefaults();
    }

    private void registerDefaults() {
        register(LokiQueryType.TIMEOUT_ERROR,
                "{service=\"$service\", namespace=\"$namespace\"} |= \"timeout\"");

        register(LokiQueryType.DOWNSTREAM_TIMEOUT,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"downstream|payment|dependency\" |= \"timeout\"");

        register(LokiQueryType.EXCEPTION_LOGS,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"Exception|ERROR|Error\"");

        register(LokiQueryType.CRASH_LOGS,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"panic|crash|segmentation fault|fatal\"");

        register(LokiQueryType.OOM_LOGS,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"OOMKilled|OutOfMemory|out of memory|memory limit\"");

        register(LokiQueryType.DB_CONNECTION_TIMEOUT,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"database|db|connection pool|jdbc\" |~ \"timeout|exhausted\"");

        register(LokiQueryType.RETRY_EXHAUSTED,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \"retry exhausted|max retries|retry limit\"");

        register(LokiQueryType.HTTP_5XX_LOGS,
                "{service=\"$service\", namespace=\"$namespace\"} |~ \" 5[0-9][0-9] |status=5|status_code=5\"");
    }

    public void register(LokiQueryType type, String template) {
        templates.put(type, new LokiQueryTemplate(type, template));
    }

    public Optional<LokiQueryTemplate> getTemplate(LokiQueryType type) {
        return Optional.ofNullable(templates.get(type));
    }

    public Set<LokiQueryType> availableTypes() {
        return Collections.unmodifiableSet(templates.keySet());
    }
}
