package ai.sreagent.loki.mapper;

/**
 * Loki evidence type constants.
 * These map to generic Evidence.evidenceType strings.
 */
public final class LokiEvidenceTypes {

    private LokiEvidenceTypes() {}

    public static final String LOG_TIMEOUT_ERROR = "log_timeout_error";
    public static final String LOG_DOWNSTREAM_TIMEOUT = "log_downstream_timeout";
    public static final String LOG_EXCEPTION_SPIKE = "log_exception_spike";
    public static final String LOG_CRASH_LOOP = "log_crash_loop";
    public static final String LOG_OOM_MESSAGE = "log_oom_message";
    public static final String LOG_DB_CONNECTION_TIMEOUT = "log_db_connection_timeout";
    public static final String LOG_RETRY_EXHAUSTED = "log_retry_exhausted";
    public static final String LOG_HTTP_5XX = "log_http_5xx";
    public static final String LOG_NO_SIGNAL = "log_no_signal";

    /** Source identifier for all Loki evidence */
    public static final String SOURCE = "loki";
}
