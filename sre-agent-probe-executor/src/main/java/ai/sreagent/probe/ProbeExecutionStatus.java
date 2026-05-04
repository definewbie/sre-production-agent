package ai.sreagent.probe;

/**
 * Status of a probe execution attempt.
 */
public enum ProbeExecutionStatus {
    PLANNED,
    EXECUTED,
    SKIPPED_BY_POLICY,
    FAILED,
    UNSUPPORTED_PROBE_TYPE
}
