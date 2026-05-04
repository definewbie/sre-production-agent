package ai.sreagent.llm.proposer;

/**
 * An advisory probe intent — what evidence the LLM would like to collect
 * to verify or refute a proposed hypothesis.
 * NOT executed in Step R.
 */
public record ProbeIntent(
    ProbeType probeType,
    String targetService,
    String targetEntity,
    String queryIntent,
    String expectedEvidenceType,
    String rationale
) {}
