package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A topology path that explains how a fault can propagate between services.
 *
 * <p>The path is intentionally small and immutable. It is used as RCA context
 * and as a bounded scoring signal, not as a hard gate.</p>
 */
public record PropagationPath(
    /** Services in propagation order, e.g. payment-service → order-service. */
    List<String> services,

    /** Edges that make up the propagation path. */
    List<TopologyEdge> edges,

    /** Aggregate confidence across all edges in the path. */
    @JsonProperty("path_confidence") TopologyEdgeConfidence pathConfidence,

    /** Common propagation direction for this path. */
    PropagationDirection direction,

    /** How this path was discovered. */
    @JsonProperty("path_source") TopologyEdgeSource pathSource,

    /** Human-readable explanation of the path. */
    String explanation
) {
    public static final PropagationPath NONE = new PropagationPath(
            List.of(), List.of(), TopologyEdgeConfidence.LOW,
            PropagationDirection.UNKNOWN, TopologyEdgeSource.STATIC_FALLBACK,
            "No propagation path found."
    );

    public static PropagationPath fromEdge(TopologyEdge edge) {
        if (edge == null || !edge.isPresent()) {
            return NONE;
        }
        return new PropagationPath(
                List.of(edge.fromService(), edge.toService()),
                List.of(edge),
                edge.edgeConfidence(),
                edge.direction(),
                edge.edgeSource(),
                edge.explanation()
        );
    }

    public static PropagationPath fromEdges(
            List<String> services,
            List<TopologyEdge> edges,
            PropagationDirection direction,
            TopologyEdgeSource source,
            String explanation
    ) {
        if (services == null || services.size() < 2 || edges == null || edges.isEmpty()) {
            return NONE;
        }
        return new PropagationPath(
                List.copyOf(services),
                List.copyOf(edges),
                weakestConfidence(edges),
                direction,
                source,
                explanation
        );
    }

    public boolean isPresent() {
        return !services.isEmpty() && !edges.isEmpty();
    }

    public int pathLength() {
        return edges.size();
    }

    private static TopologyEdgeConfidence weakestConfidence(List<TopologyEdge> edges) {
        if (edges.stream().anyMatch(e -> e.edgeConfidence() == TopologyEdgeConfidence.LOW)) {
            return TopologyEdgeConfidence.LOW;
        }
        if (edges.stream().anyMatch(e -> e.edgeConfidence() == TopologyEdgeConfidence.MEDIUM)) {
            return TopologyEdgeConfidence.MEDIUM;
        }
        return TopologyEdgeConfidence.HIGH;
    }
}
