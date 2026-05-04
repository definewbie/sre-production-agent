package ai.sreagent.core.evidence;

/**
 * Top-level category for observability evidence.
 * Maps to the "pillar" of observability that produced this evidence.
 */
public enum EvidenceCategory {
    ALERT,
    METRIC,
    LOG,
    TRACE,
    KUBERNETES,
    TOPOLOGY,
    DEPLOYMENT,
    RUNTIME,
    UNKNOWN
}
