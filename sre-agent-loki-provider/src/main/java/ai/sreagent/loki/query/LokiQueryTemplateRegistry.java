package ai.sreagent.loki.query;

import java.util.*;

/**
 * Registry of LogQL query templates for known log patterns.
 * Templates are MVP examples — real environments may use different label names.
 *
 * All |~ regex patterns use (?i) prefix for case-insensitive matching
 * per Grafana Loki LogQL specification:
 *   "The matching is case-sensitive by default. Switch to case-insensitive
 *    matching by prefixing the regular expression with (?i)."
 */
public class LokiQueryTemplateRegistry {

    private final Map<LokiQueryType, LokiQueryTemplate> templates;

    public LokiQueryTemplateRegistry() {
        templates = new EnumMap<>(LokiQueryType.class);
        registerDefaults();
    }

    private void registerDefaults() {
        // Use 'app' label — Promtail auto-discovers from pod label 'app' (not 'service')

        register(LokiQueryType.TIMEOUT_ERROR,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)timeout\"");

        register(LokiQueryType.DOWNSTREAM_TIMEOUT,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)downstream|payment|dependency\" |~ \"(?i)timeout\"");

        register(LokiQueryType.EXCEPTION_LOGS,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)exception|error\"");

        register(LokiQueryType.CRASH_LOGS,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)panic|crash|segmentation fault|fatal\"");

        register(LokiQueryType.OOM_LOGS,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)oomkilled|outofmemory|out of memory|memory limit\"");

        register(LokiQueryType.DB_CONNECTION_TIMEOUT,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)database|db|connection pool|jdbc\" |~ \"(?i)timeout|exhausted\"");

        register(LokiQueryType.RETRY_EXHAUSTED,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i)retry exhausted|max retries|retry limit\"");

        // Match HTTP 5xx status codes OR chaos injection errors (e.g. "injected-error")
        register(LokiQueryType.HTTP_5XX_LOGS,
                "{app=\"$service\", namespace=\"$namespace\"} |~ \"(?i) 5[0-9][0-9] |status=5|status_code=5|injected-error\"");
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
