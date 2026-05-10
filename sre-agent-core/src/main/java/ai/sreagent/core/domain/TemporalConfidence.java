package ai.sreagent.core.domain;

/**
 * Confidence level for temporal alignment evaluation.
 */
public enum TemporalConfidence {
    /** Sufficient timestamps on both candidate and impacted evidence. */
    HIGH,
    /** Some timestamps available but not all. */
    MEDIUM,
    /** Timestamps present but unable to distinguish candidate/impacted. */
    LOW,
    /** No timestamp information available at all. */
    UNKNOWN
}
