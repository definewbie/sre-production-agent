package ai.sreagent.trace.mapper;

/**
 * Trace evidence type constants.
 * These map to generic Evidence.evidenceType strings.
 */
public final class TraceEvidenceTypes {

    private TraceEvidenceTypes() {}

    public static final String TRACE_DOWNSTREAM_SPAN_SLOW = "trace_downstream_span_slow";
    public static final String TRACE_ERROR_SPAN = "trace_error_span";
    public static final String TRACE_ROOT_SPAN_SLOW = "trace_root_span_slow";
    public static final String TRACE_DEPENDENCY_PATH = "trace_dependency_path";
    public static final String TRACE_TIMEOUT_SPAN = "trace_timeout_span";
    public static final String TRACE_CHILD_SPAN_DOMINATES_LATENCY = "trace_child_span_dominates_latency";
    public static final String TRACE_NO_SIGNAL = "trace_no_signal";

    /** Source identifier for all trace evidence */
    public static final String SOURCE = "tracing";
}
