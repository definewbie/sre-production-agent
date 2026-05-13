package ai.sreagent.core.domain;

/**
 * Source of a topology edge — describes <em>how</em> the dependency was discovered.
 *
 * <p>Confidence hierarchy: TRACE &gt; OBSERVED_DEPENDENCY &gt;
 * CONFIGURED_TOPOLOGY &gt; STATIC_FALLBACK.</p>
 */
public enum TopologyEdgeSource {

    /** Dependency discovered from distributed tracing (e.g., parent-child span). Highest confidence. */
    TRACE,

    /** Dependency observed from metrics/logs (e.g., downstream latency spike, timeout logs). */
    OBSERVED_DEPENDENCY,

    /** Dependency declared in configuration (e.g., service mesh, K8s NetworkPolicy, config map). */
    CONFIGURED_TOPOLOGY,

    /** Dependency inferred from static evidence or heuristics. Lowest confidence. */
    STATIC_FALLBACK
}
