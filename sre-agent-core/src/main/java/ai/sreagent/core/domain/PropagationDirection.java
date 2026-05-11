package ai.sreagent.core.domain;

/**
 * Direction of fault propagation along a topology edge.
 */
public enum PropagationDirection {

    /** Fault flows from upstream to downstream (normal dependency direction). */
    UPSTREAM_TO_DOWNSTREAM,

    /**
     * Downstream failure impacts upstream via backpressure, timeout, or cascading
     * resource exhaustion.
     */
    DOWNSTREAM_TO_UPSTREAM_IMPACT,

    /** Direction cannot be determined from available evidence. */
    UNKNOWN
}
