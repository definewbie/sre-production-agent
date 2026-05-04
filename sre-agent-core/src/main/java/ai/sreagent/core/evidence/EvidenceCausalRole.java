package ai.sreagent.core.evidence;

/**
 * Causal role classification for evidence within an RCA context.
 * This is for reasoning support, not final RCA decision.
 */
public enum EvidenceCausalRole {
    /** Observable symptom (alert firing, error rate spike) */
    SYMPTOM,
    /** Potential root cause candidate (downstream timeout, crash loop) */
    CAUSE_CANDIDATE,
    /** Background context (deployment metadata, topology) */
    CONTEXT,
    /** Evidence that counters a hypothesis */
    COUNTER_SIGNAL,
    /** Topology / dependency information */
    TOPOLOGY_CONTEXT,
    /** No signal or empty result */
    NO_SIGNAL,
    UNKNOWN
}
