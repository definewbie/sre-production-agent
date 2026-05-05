package ai.sreagent.llm.proposer;

import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds LLM prompts for hypothesis proposal generation.
 * Includes strict guardrails in system prompt.
 */
public class LlmHypothesisProposalPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一个 SRE RCA 假设提议助手。
            
            你可以提出新的假设和验证计划。
            你不得决定最终根因。
            你不得更改 RCA 决策。
            你不得更改置信度分数。
            你不得编造证据。
            你必须将所有提议标注为未验证。
            你必须产出探测意图，而非结论。
            
            LLM 提议，验证裁决。
            
            所有输出必须使用中文。
            """;

    public LlmRequest build(InvestigationResult result, List<NormalizedEvidence> normalizedEvidence) {
        String userPrompt = buildUserPrompt(result, normalizedEvidence);
        return new LlmRequest(SYSTEM_PROMPT, userPrompt, Map.of());
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    private String buildUserPrompt(InvestigationResult result, List<NormalizedEvidence> evidence) {
        StringBuilder sb = new StringBuilder();

        // 1. Incident summary
        sb.append("## Incident Summary\n");
        if (result.incident() != null) {
            sb.append("- Service: ").append(result.incident().service()).append("\n");
            sb.append("- Alert: ").append(result.incident().alertName()).append("\n");
            sb.append("- Severity: ").append(result.incident().severity()).append("\n");
        }
        sb.append("- Incident ID: ").append(result.incidentId()).append("\n\n");

        // 2. Deterministic RCA decision
        sb.append("## Deterministic RCA Decision\n");
        sb.append("- Decision: ").append(result.decision().decisionType()).append("\n");
        sb.append("- Selected hypothesis: ").append(result.decision().selectedHypothesisId()).append("\n");
        sb.append("- Confidence: ").append(String.format("%.2f", result.decision().confidenceScore())).append("\n");
        sb.append("- Rationale: ").append(result.decision().rationale()).append("\n\n");

        // 3. Hypothesis scores
        sb.append("## Hypothesis Scores\n");
        if (result.confidenceResults() != null) {
            for (var cr : result.confidenceResults()) {
                sb.append("- ").append(cr.hypothesisId())
                  .append(": score=").append(String.format("%.2f", cr.score()))
                  .append(", level=").append(cr.level()).append("\n");
            }
        }
        sb.append("\n");

        // 4. Score gap
        sb.append("## Score Gap\n");
        if (result.comparison() != null) {
            sb.append("- Gap: ").append(String.format("%.2f", result.comparison().scoreGap())).append("\n");
            sb.append("- Near tie: ").append(result.comparison().nearTie()).append("\n");
        }
        sb.append("\n");

        // 5. Normalized evidence grouped by category
        sb.append("## Normalized Evidence\n");
        if (evidence != null && !evidence.isEmpty()) {
            Map<String, List<NormalizedEvidence>> byCategory = evidence.stream()
                .collect(Collectors.groupingBy(e -> e.category().name()));
            for (var entry : byCategory.entrySet()) {
                sb.append("### ").append(entry.getKey()).append("\n");
                for (var ne : entry.getValue()) {
                    sb.append("- signal=").append(ne.signal().name())
                      .append(", causalRole=").append(ne.causalRole().name())
                      .append(", entity=").append(ne.entity())
                      .append(", strength=").append(String.format("%.2f", ne.strength()))
                      .append("\n");
                }
            }
        } else {
            sb.append("(no normalized evidence)\n");
        }
        sb.append("\n");

        // 6. Verification summary
        sb.append("## Verification Summary\n");
        if (result.verificationResults() != null) {
            for (var vr : result.verificationResults()) {
                sb.append("- ").append(vr.hypothesisId())
                      .append(": supporting=").append(vr.supportingEvidenceIds().size())
                      .append(", counter=").append(vr.counterEvidenceIds().size())
                      .append(", missing=").append(vr.missingEvidence().size())
                  .append("\n");
            }
        }
        sb.append("\n");

        // 7. Instructions
        sb.append("## Required Output\n");
        sb.append("Based on the above, propose testable hypotheses with verification plans.\n");
        sb.append("Each proposal must include:\n");
        sb.append("- proposalId, title, rootCauseType, affectedService\n");
        sb.append("- candidateCause, reasoning\n");
        sb.append("- supportingSignals (list of signal names)\n");
        sb.append("- verificationPlan with requiredEvidence, missingEvidence, counterEvidenceToCheck, probeIntents\n");
        sb.append("- priorConfidence (0.0-0.5 only)\n");
        sb.append("- status: UNVERIFIED_PROPOSAL\n");
        sb.append("- canAffectDecision: false\n");

        return sb.toString();
    }
}
