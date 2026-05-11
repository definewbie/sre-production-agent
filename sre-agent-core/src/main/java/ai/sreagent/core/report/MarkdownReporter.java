package ai.sreagent.core.report;

import ai.sreagent.core.domain.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a deterministic Markdown RCA report from investigation results.
 * Zero external dependencies beyond domain objects.
 */
public class MarkdownReporter {

    public String generate(
            IncidentTask incident,
            List<Hypothesis> hypotheses,
            List<VerificationResult> verificationResults,
            List<ConfidenceResult> confidenceResults,
            HypothesisComparison comparison,
            InvestigationDecision decision,
            List<Evidence> evidence,
            ProblemWindow problemWindow) {

        StringBuilder sb = new StringBuilder();

        Map<String, VerificationResult> verifMap = verificationResults.stream()
                .collect(Collectors.toMap(VerificationResult::hypothesisId, v -> v));
        Map<String, ConfidenceResult> confMap = confidenceResults.stream()
                .collect(Collectors.toMap(ConfidenceResult::hypothesisId, c -> c));
        Map<String, Evidence> evidenceMap = evidence.stream()
                .collect(Collectors.toMap(Evidence::id, e -> e));

        // Title
        sb.append("# 竞争假设分析报告: ")
                .append(incident.alertName())
                .append(" — ")
                .append(incident.service())
                .append("\n\n");

        // Decision
        sb.append("## 决策结论\n\n");
        sb.append("决策类型: ").append(decisionTypeZh(decision.decisionType())).append("\n");
        sb.append("选定假设: ").append(hypothesisTitleZh(decision.selectedHypothesisId())).append("\n");
        if (!decision.competingHypotheses().isEmpty()) {
            sb.append("竞争假设: ").append(String.join(", ",
                    decision.competingHypotheses().stream()
                            .map(this::hypothesisTitleZh).toList())).append("\n");
        }
        sb.append("置信度: ").append(formatScore(decision.confidenceScore())).append("\n");
        sb.append("分数差距: ").append(formatScore(comparison.scoreGap())).append("\n\n");

        // Summary
        sb.append("## 事件摘要\n\n");
        sb.append(incident.service()).append(" 触发了 ").append(incident.alertName());
        sb.append(" 告警，发生时间 ").append(incident.startedAt()).append("。\n\n");
        sb.append(decision.rationale()).append("\n\n");

        // Hypothesis Scores
        sb.append("## 假设评分\n\n");
        sb.append("| 假设 | 分数 | 等级 | 决策 |\n");
        sb.append("|---|---:|---|---|\n");
        confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .forEach(c -> sb.append(String.format("| %s | %.2f | %s | %s |\n",
                        hypothesisTitleZh(c.hypothesisId()), c.score(), levelZh(c.level()), decisionZh(c.decision()))));
        sb.append("\n");

        // Observability quality
        sb.append("## 可观测性质量\n\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            List<String> blindProviders = c.providerBlindness();
            sb.append("- ").append(hypothesisTitleZh(c.hypothesisId()))
                    .append(": ").append(c.diagnosticQuality() != null ? c.diagnosticQuality() : "FULL");
            if (blindProviders != null && !blindProviders.isEmpty()) {
                sb.append("（blind providers: ").append(String.join(", ", blindProviders)).append("）");
            }
            sb.append("\n");
        }
        sb.append("\n");

        // ── V.2-RCA-1A.3: Temporal Alignment Analysis ──
        sb.append("## 时间对齐分析\n\n");
        sb.append("Temporal alignment 评估证据时间戳是否支持各假设的因果顺序")
                .append("（candidate 异常先于 impacted 服务异常）。\n\n");

        // Problem Window status
        if (problemWindow != null && problemWindow.isValid()) {
            sb.append("**Problem Window**: ")
                    .append(problemWindow.problemStart().toString()).append(" → ")
                    .append(problemWindow.problemEnd().toString())
                    .append("（来源: ").append(problemWindow.source())
                    .append(", lookback: ").append(problemWindow.lookbackWindow().toMinutes()).append("min")
                    .append(", lookahead: ").append(problemWindow.lookaheadWindow().toMinutes()).append("min）\n\n");
        } else {
            sb.append("> ⚠️ **PARTIAL** — Problem Window 尚未接入报告层。")
                    .append("当前为无 temporal 路径（score=0, confidence=UNKNOWN），")
                    .append("不代表 TemporalAligner 不可用，而是调用链未贯通。\n\n");
        }

        sb.append("| 假设 | Temporal Score | Temporal 置信度 | Candidate First Seen | Impacted First Seen |\n");
        sb.append("|---|---|---|---:|---:|\n");
        confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .forEach(c -> sb.append(String.format("| %s | %+.2f | %s | %s | %s |\n",
                        hypothesisTitleZh(c.hypothesisId()),
                        c.temporalAlignmentScore(),
                        temporalConfidenceZh(c.temporalConfidence()),
                        c.candidateFirstSeen() != null ? c.candidateFirstSeen().toString() : "N/A",
                        c.impactedFirstSeen() != null ? c.impactedFirstSeen().toString() : "N/A")));
        sb.append("\n");

        if (problemWindow == null || !problemWindow.isValid()) {
            sb.append("> ⚠️ 以上 temporal 数据来自 ConfidenceResult（由 ConfidenceScorer 注入）。")
                    .append("若为全部默认值则说明调用链未传入 TemporalAlignmentResult。\n\n");
        }

        // Per-hypothesis temporal detail
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            sb.append("### ").append(hypothesisTitleZh(c.hypothesisId()))
                    .append(" 时间对齐详情\n\n");
            sb.append("- **Temporal Score**: ").append(String.format("%+.2f", c.temporalAlignmentScore())).append("\n");
            sb.append("- **Temporal 置信度**: ").append(temporalConfidenceZh(c.temporalConfidence())).append("\n");
            sb.append("- **Candidate First Seen**: ")
                    .append(c.candidateFirstSeen() != null ? c.candidateFirstSeen().toString() : "N/A").append("\n");
            sb.append("- **Impacted First Seen**: ")
                    .append(c.impactedFirstSeen() != null ? c.impactedFirstSeen().toString() : "N/A").append("\n");
            String explanation = c.temporalExplanation();
            sb.append("- **Temporal 说明**: ")
                    .append(explanation != null && !explanation.isEmpty() ? explanation : "N/A（无 temporal alignment 数据）")
                    .append("\n\n");
        }

        // ── V.2-RCA-1A.4: Topology Edge Analysis ──
        sb.append("## 拓扑分析\n\n");
        sb.append("Topology edge 描述候选假设中涉及的**服务间调用/依赖关系**及其发现方式；Propagation Score 描述该路径对根因传播解释的有界加分。\n\n");
        sb.append("| 假设 | Topology Score | Propagation Score | Edge Source | Edge Confidence | Direction | Path Length | 说明 |\n");
        sb.append("|---|---:|---:|---|---|---|---:|---|\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            TopologyEdge edge = c.topologyEdge();
            if (edge != null && edge.isPresent()) {
                sb.append(String.format("| %s | %+.2f | %+.2f | %s | %s | %s | %d | %s |\n",
                        hypothesisTitleZh(c.hypothesisId()),
                        c.topologyCausalityScore(),
                        c.propagationScore(),
                        topologyEdgeSourceZh(edge.edgeSource()),
                        topologyEdgeConfidenceZh(edge.edgeConfidence()),
                        propagationDirectionZh(edge.direction()),
                        edge.pathLength(),
                        edge.explanation() != null ? edge.explanation() : "—"));
            } else {
                sb.append(String.format("| %s | %+.2f | %+.2f | — | — | — | — | ⚠️ 无拓扑证据：无法确认服务间依赖关系 |\n",
                        hypothesisTitleZh(c.hypothesisId()),
                        c.topologyCausalityScore(),
                        c.propagationScore()));
            }
        }
        sb.append("\n");

        sb.append("### 传播路径\n\n");
        sb.append("| 假设 | Path Confidence | Path Length | Services | 说明 |\n");
        sb.append("|---|---|---:|---|---|\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            PropagationPath path = c.propagationPath();
            if (path != null && path.isPresent()) {
                sb.append(String.format("| %s | %s | %d | %s | %s |\n",
                        hypothesisTitleZh(c.hypothesisId()),
                        topologyEdgeConfidenceZh(path.pathConfidence()),
                        path.pathLength(),
                        String.join(" → ", path.services()),
                        path.explanation() != null ? path.explanation() : "—"));
            } else {
                sb.append(String.format("| %s | — | — | — | ⚠️ 无传播路径 |\n",
                        hypothesisTitleZh(c.hypothesisId())));
            }
        }
        sb.append("\n");

        boolean hasTopology = confidenceResults.stream()
                .anyMatch(c -> {
                    TopologyEdge e = c.topologyEdge();
                    return e != null && e.isPresent();
                });

        if (!hasTopology) {
            sb.append("> ⚠️ **NO-TOPOLOGY GUARDRAIL**：所有假设均缺少拓扑证据。\n");
            sb.append("> 在没有拓扑信息支持的情况下，仅凭 temporal alignment 不能将假设判定为\n");
            sb.append("> `likely_root_cause` 或 `probable_root_cause`。需要补充服务依赖关系证据后才可信赖。\n\n");
        }

        // Leading Hypothesis
        sb.append("## 领先假设\n\n");
        sb.append(hypothesisTitleZh(comparison.leadingHypothesisId())).append("\n\n");

        // Competing Hypotheses
        if (!comparison.competingHypothesisIds().isEmpty()) {
            sb.append("## 竞争假设\n\n");
            comparison.competingHypothesisIds().forEach(h ->
                    sb.append("- ").append(hypothesisTitleZh(h)).append("\n"));
            sb.append("\n");
        }

        // Why Leading Leads
        ConfidenceResult leadingConf = confMap.get(comparison.leadingHypothesisId());
        if (leadingConf != null) {
            sb.append("## 为什么 ").append(hypothesisTitleZh(comparison.leadingHypothesisId()))
                    .append(" 领先\n\n");
            for (String factor : leadingConf.supportingFactors()) {
                sb.append("- ").append(factor).append("\n");
            }
            sb.append("\n");
        }

        // Why Competing Remains Plausible
        for (String compId : comparison.competingHypothesisIds()) {
            ConfidenceResult compConf = confMap.get(compId);
            if (compConf != null) {
                sb.append("## 为什么 ").append(hypothesisTitleZh(compId))
                        .append(" 仍然成立\n\n");
                for (String factor : compConf.supportingFactors()) {
                    sb.append("- ").append(factor).append("\n");
                }
                sb.append("\n");
            }
        }

        // Counter Evidence
        sb.append("## 反驳证据\n\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            if (!c.counterFactors().isEmpty()) {
                sb.append("### 针对 ").append(hypothesisTitleZh(c.hypothesisId())).append("\n\n");
                for (String factor : c.counterFactors()) {
                    sb.append("- ").append(factor).append("\n");
                }
                sb.append("\n");
            }
        }

        // Contradictions
        sb.append("## 矛盾点\n\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            VerificationResult vr = verifMap.get(c.hypothesisId());
            if (vr != null && !vr.contradictions().isEmpty()) {
                for (String contra : vr.contradictions()) {
                    sb.append("- ").append(contra).append("\n");
                }
            }
        }
        sb.append("\n");

        // Suggested Next Probes
        sb.append("## 建议下一步探测\n\n");
        if (!decision.nextProbes().isEmpty()) {
            for (int i = 0; i < decision.nextProbes().size(); i++) {
                sb.append(i + 1).append(". ").append(decision.nextProbes().get(i)).append("\n");
            }
        }
        sb.append("\n");

        // Calibration Notes
        sb.append("## 校准说明\n\n");
        ConfidenceResult firstConf = confidenceResults.stream()
                .findFirst().orElse(null);
        if (firstConf != null && firstConf.calibrationNotes() != null) {
            sb.append(firstConf.calibrationNotes()).append("\n\n");
        }

        // Event Trace Note
        sb.append("## 事件追踪\n\n");
        sb.append("使用 CLI --show-trace 查看完整调查路径。\n");

        return sb.toString();
    }

    private String formatScore(double score) {
        return String.format("%.2f", score);
    }

    private String hypothesisTitle(String hypothesisId) {
        return hypothesisId.replace("hyp_", "").replace("_", " ");
    }

    private static final Map<String, String> HYP_TITLE_ZH = Map.of(
            "hyp_deployment_regression", "近期部署引入回归缺陷",
            "hyp_downstream_dependency_latency", "下游依赖延迟导致超时",
            "hyp_pod_oom_killed", "Pod 内存溢出（OOMKilled）",
            "hyp_pod_crash_loop", "容器崩溃循环"
    );

    private String hypothesisTitleZh(String hypothesisId) {
        return HYP_TITLE_ZH.getOrDefault(hypothesisId, hypothesisTitle(hypothesisId));
    }

    private String decisionTypeZh(String decisionType) {
        return switch (decisionType) {
            case "likely_root_cause" -> "高置信根因";
            case "probable_root_cause" -> "可能根因";
            case "competing_hypotheses" -> "竞争假设";
            case "uncertain_requires_more_evidence" -> "不确定（需更多证据）";
            case "insufficient_evidence" -> "证据不足";
            default -> decisionType;
        };
    }

    private String levelZh(String level) {
        return switch (level) {
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            case "critical" -> "关键";
            default -> level != null ? level : "-";
        };
    }

    private String decisionZh(String decision) {
        return switch (decision) {
            case "likely_root_cause" -> "高置信根因";
            case "probable_root_cause" -> "可能根因";
            case "competing" -> "竞争假设";
            case "uncertain" -> "不确定";
            case "insufficient_evidence" -> "证据不足";
            default -> decision != null ? decision : "-";
        };
    }

    private String temporalConfidenceZh(String confidence) {
        return switch (confidence) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            case "UNKNOWN" -> "未知";
            default -> confidence != null ? confidence : "未知";
        };
    }

    private String topologyEdgeSourceZh(TopologyEdgeSource source) {
        return switch (source) {
            case TRACE -> "追踪";
            case OBSERVED_DEPENDENCY -> "观测依赖";
            case CONFIGURED_TOPOLOGY -> "配置拓扑";
            case STATIC_FALLBACK -> "静态回退";
        };
    }

    private String topologyEdgeConfidenceZh(TopologyEdgeConfidence confidence) {
        return switch (confidence) {
            case HIGH -> "🔴 高";
            case MEDIUM -> "🟡 中";
            case LOW -> "⚪ 低";
        };
    }

    private String propagationDirectionZh(PropagationDirection direction) {
        return switch (direction) {
            case UPSTREAM_TO_DOWNSTREAM -> "上游→下游";
            case DOWNSTREAM_TO_UPSTREAM_IMPACT -> "下游影响上游";
            case UNKNOWN -> "未知";
        };
    }
}
