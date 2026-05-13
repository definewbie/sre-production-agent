package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single directed edge in the service dependency topology relevant to an RCA investigation.
 *
 * <p>A TopologyEdge represents a dependency between two services that may be
 * relevant to fault propagation. It carries metadata about how the edge was
 * discovered, the confidence in its existence, the direction of potential fault
 * propagation, and how many hops from the candidate cause this edge represents.</p>
 *
 * <h3>Edge source → confidence mapping</h3>
 * <pre>
 * TRACE                → HIGH    (parent-child spans in distributed tracing)
 * OBSERVED_DEPENDENCY  → MEDIUM  (latency correlation, timeout patterns)
 * CONFIGURED_TOPOLOGY  → MEDIUM  (service mesh config, K8s resources)
 * STATIC_FALLBACK      → LOW     (heuristic or static evidence inference)
 * </pre>
 *
 * <h3>Scoring semantics</h3>
 * <p>TopologyEdge does NOT directly contribute a numeric score to the RCA decision.
 * It provides qualitative context that supports or weakens the plausibility
 * of a fault propagation hypothesis. A hypothesis WITHOUT any topology context
 * that relies solely on temporal alignment should NOT reach probable/likely
 * root cause.</p>
 *
 * <p>pathLength of 1 means direct dependency (candidate → impacted).
 * Longer paths introduce more uncertainty.</p>
 */
public record TopologyEdge(
    /** Source service (candidate in fault propagation). */
    @JsonProperty("from_service") String fromService,

    /** Target service (impacted in fault propagation). */
    @JsonProperty("to_service") String toService,

    /** How this dependency edge was discovered. */
    @JsonProperty("edge_source") TopologyEdgeSource edgeSource,

    /** Confidence level of this edge (derived from source). */
    @JsonProperty("edge_confidence") TopologyEdgeConfidence edgeConfidence,

    /** Direction of fault propagation along this edge. */
    PropagationDirection direction,

    /**
     * Number of hops from the candidate cause service to this edge.
     * 1 = candidate directly depends on or calls this service.
     * Longer paths mean more uncertainty in propagation path.
     */
    @JsonProperty("path_length") int pathLength,

    /**
     * Human-readable explanation of how this edge was resolved.
     * e.g., "从 trace evidence 推断：order-service → payment-service（span parent-child 关系）"
     */
    String explanation
) {
    /** The topology edge representing "no topology information available". */
    public static final TopologyEdge NONE = new TopologyEdge(
            "", "", TopologyEdgeSource.STATIC_FALLBACK, TopologyEdgeConfidence.LOW,
            PropagationDirection.UNKNOWN, 0,
            "No topology evidence found — dependency relationship could not be confirmed."
    );

    /**
     * Derive edge confidence from edge source using the confidence hierarchy.
     */
    public static TopologyEdgeConfidence deriveConfidence(TopologyEdgeSource source) {
        return switch (source) {
            case TRACE -> TopologyEdgeConfidence.HIGH;
            case OBSERVED_DEPENDENCY, CONFIGURED_TOPOLOGY -> TopologyEdgeConfidence.MEDIUM;
            case STATIC_FALLBACK -> TopologyEdgeConfidence.LOW;
        };
    }

    /**
     * Returns true if this edge represents real topology information (not NONE).
     */
    public boolean isPresent() {
        return this != NONE && !fromService.isEmpty();
    }
}
