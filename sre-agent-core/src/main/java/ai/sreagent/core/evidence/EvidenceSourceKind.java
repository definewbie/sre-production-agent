package ai.sreagent.core.evidence;

/**
 * Which system/source-kind produced this evidence.
 * Inferred from Evidence.source field when no explicit mapping exists.
 */
public enum EvidenceSourceKind {
    STATIC,
    KUBERNETES,
    PROMETHEUS,
    LOKI,
    ALERTMANAGER,
    TRACE,
    CMDB,
    UNKNOWN
}
