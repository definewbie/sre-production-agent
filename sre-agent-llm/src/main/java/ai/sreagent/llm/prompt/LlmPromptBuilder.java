package ai.sreagent.llm.prompt;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the LLM prompt from a deterministic InvestigationResult.
 *
 * Key invariant: the prompt includes strict constraints that prevent the LLM
 * from overriding the deterministic decision, scores, or evidence.
 */
public class LlmPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一个 RCA（根因分析）推理和报告合成助手。
            
            你可以协助解释确定性调查的结果。
            
            你必须只使用提供的结构化调查结果。
            
            你不得推断新的最终根因。
            你不得更改决策结论。
            你不得更改置信度分数。
            你不得编造证据。
            你不得隐藏反驳证据。
            当确定性决策为 competing_hypotheses 时，你不得声称已确定根因。
            你必须保留不确定性。
            你可以建议额外的调查探测，但必须标注为未验证建议。
            你不得编造 K8s、EC2、RDS、ElastiCache、ALB、CMDB 或拓扑事实。
            LLM 可以协助，但不能裁决。
            
            所有输出必须使用中文。
            """;

    private static final String EVIDENCE_SCOPE_NOTE =
            "此解释仅基于当前工作流可获取的已验证结构化证据。" +
            "未来证据来源可能包括 K8s、EC2、RDS、ElastiCache、ALB、CMDB 和服务拓扑。";

    public LlmRequest build(InvestigationResult result) {
        String userPrompt = buildUserPrompt(result);
        return new LlmRequest(SYSTEM_PROMPT, userPrompt, Map.of("incidentId", result.incidentId()));
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String getEvidenceScopeNote() {
        return EVIDENCE_SCOPE_NOTE;
    }

    private String buildUserPrompt(InvestigationResult result) {
        StringBuilder sb = new StringBuilder();

        // 1. Incident summary
        IncidentTask incident = result.incident();
        sb.append("# Investigation Result for Synthesis\n\n");
        sb.append("Incident ID: ").append(result.incidentId()).append("\n");
        sb.append("Alert: ").append(incident.alertName()).append("\n");
        sb.append("Service: ").append(incident.service()).append("\n");
        sb.append("Severity: ").append(incident.severity()).append("\n\n");

        // 2. Deterministic decision
        InvestigationDecision decision = result.decision();
        sb.append("## Deterministic Decision\n");
        sb.append("Decision Type: ").append(decision.decisionType()).append("\n");
        sb.append("Selected Hypothesis: ").append(decision.selectedHypothesisId()).append("\n");
        sb.append("Confidence Score: ").append(decision.confidenceScore()).append("\n");
        sb.append("Rationale: ").append(decision.rationale()).append("\n");
        if (!decision.competingHypotheses().isEmpty()) {
            sb.append("Competing Hypotheses: ").append(String.join(", ", decision.competingHypotheses())).append("\n");
        }
        sb.append("\n");

        // 3. Hypothesis scores
        sb.append("## Hypothesis Scores\n");
        for (ConfidenceResult cr : result.confidenceResults()) {
            sb.append("- ").append(cr.hypothesisId())
              .append(": score=").append(String.format("%.2f", cr.score()))
              .append(", level=").append(cr.level())
              .append(", decision=").append(cr.decision()).append("\n");
        }
        sb.append("\n");

        // 4. Score gap
        HypothesisComparison comparison = result.comparison();
        sb.append("## Hypothesis Comparison\n");
        sb.append("Leading Hypothesis: ").append(comparison.leadingHypothesisId()).append("\n");
        sb.append("Score Gap: ").append(String.format("%.2f", comparison.scoreGap())).append("\n");
        sb.append("Near Tie: ").append(comparison.nearTie()).append("\n");
        sb.append("Comparison Summary: ").append(comparison.comparisonSummary()).append("\n\n");

        // 5. Verification results
        sb.append("## Verification Results\n");
        for (VerificationResult vr : result.verificationResults()) {
            sb.append("### ").append(vr.hypothesisId()).append("\n");
            sb.append("Supporting Evidence: ").append(String.join(", ", vr.supportingEvidenceIds())).append("\n");
            sb.append("Counter Evidence: ").append(String.join(", ", vr.counterEvidenceIds())).append("\n");
            if (!vr.missingEvidence().isEmpty()) {
                sb.append("Missing Evidence: ").append(String.join(", ", vr.missingEvidence())).append("\n");
            }
            if (!vr.contradictions().isEmpty()) {
                sb.append("Contradictions: ").append(String.join(", ", vr.contradictions())).append("\n");
            }
            sb.append("Explanation: ").append(vr.explanation()).append("\n\n");
        }

        // 6. Evidence details
        sb.append("## Evidence Items\n");
        for (Evidence e : result.evidence()) {
            sb.append("- [").append(e.id()).append("] type=").append(e.evidenceType())
              .append(", service=").append(e.service())
              .append(", strength=").append(String.format("%.2f", e.strength()))
              .append(", content=").append(e.content()).append("\n");
        }
        sb.append("\n");

        // 7. Next probes
        sb.append("## Suggested Next Probes\n");
        for (String probe : decision.nextProbes()) {
            sb.append("- ").append(probe).append("\n");
        }
        sb.append("\n");

        // 8. Event trace summary
        sb.append("## Event Trace Summary\n");
        sb.append("Total events: ").append(result.eventTrace().size()).append("\n");
        for (EventTraceEntry entry : result.eventTrace()) {
            sb.append("- [").append(entry.eventId()).append("] ")
              .append(entry.eventType()).append("\n");
        }
        sb.append("\n");

        // 9. Constraints reminder
        sb.append("## Constraints\n");
        sb.append("- 仅将提供的结构化证据视为可用事实。\n");
        sb.append("- 不得推测缺失的 K8s、EC2、RDS、ElastiCache、ALB 或 CMDB 事实。\n");
        sb.append("- 如讨论未来调查，请标注为未验证建议。\n");
        sb.append("- LLM can assist, but cannot adjudicate.\n\n");

        // 10. Output format
        sb.append("## 输出格式\n");
        sb.append("请生成包含以下章节的结构化解释：\n");
        sb.append("- 执行摘要 (Executive Summary)\n");
        sb.append("- 推理叙事 (Reasoning Narrative)\n");
        sb.append("- 不确定性说明 (Uncertainty Explanation)\n");
        sb.append("- 后续步骤 (Next Steps)\n");
        sb.append("- 局限性 (Limitations)\n");
        sb.append("- 未验证建议 (Unverified Proposals)\n");

        return sb.toString();
    }
}
