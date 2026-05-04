package ai.sreagent.llm.proposer;

/**
 * Type of observability probe the LLM suggests executing.
 * Probes are NOT executed in Step R — they are advisory intent only.
 */
public enum ProbeType {
    PROMETHEUS_QUERY,
    LOKI_QUERY,
    TRACE_QUERY,
    KUBERNETES_QUERY,
    ALERTMANAGER_QUERY,
    CMDB_QUERY,
    HUMAN_REVIEW
}
