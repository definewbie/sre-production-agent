package ai.sreagent.loki.query;

/**
 * Types of LogQL queries the Loki provider can execute.
 */
public enum LokiQueryType {

    TIMEOUT_ERROR("timeout_error"),
    DOWNSTREAM_TIMEOUT("downstream_timeout"),
    EXCEPTION_LOGS("exception_logs"),
    CRASH_LOGS("crash_logs"),
    OOM_LOGS("oom_logs"),
    DB_CONNECTION_TIMEOUT("db_connection_timeout"),
    RETRY_EXHAUSTED("retry_exhausted"),
    HTTP_5XX_LOGS("http_5xx_logs");

    private final String key;

    LokiQueryType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static LokiQueryType fromString(String name) {
        if (name == null || name.isBlank()) return null;
        for (LokiQueryType t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.key.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
