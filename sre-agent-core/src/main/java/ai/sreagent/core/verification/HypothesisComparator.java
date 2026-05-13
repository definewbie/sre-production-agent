package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares confidence results to determine the leading hypothesis,
 * competing hypotheses, and generates the final investigation decision.
 *
 * Decision policy (incident-level):
 *   - likely_root_cause:           top1 >= 0.80 and gap >= 0.15
 *   - probable_root_cause:         top1 >= 0.60 and gap >= 0.10
 *   - competing_hypotheses:        top1 >= 0.40 and top2 >= 0.40 and gap < 0.10
 *   - uncertain_requires_more:     top1 >= 0.40
 *   - insufficient_evidence:       top1 < 0.40
 *
 * Deterministic and explainable. No randomness.
 */
public class HypothesisComparator {

    /**
     * Compare confidence results and produce a HypothesisComparison.
     */
    public HypothesisComparison compare(
            IncidentTask incident,
            List<ConfidenceResult> confidenceResults,
            List<VerificationResult> verificationResults,
            List<Evidence> evidence
    ) {
        if (confidenceResults.isEmpty()) {
            throw new IllegalArgumentException("No confidence results to compare");
        }

        // Sort by score descending
        List<ConfidenceResult> sorted = confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList();

        ConfidenceResult top1 = sorted.get(0);
        ConfidenceResult top2 = sorted.size() > 1 ? sorted.get(1) : null;

        double scoreGap = top2 != null
                ? Math.round((top1.score() - top2.score()) * 100.0) / 100.0
                : 1.0;

        // Identify competing hypotheses (score >= 0.40 and gap < 0.10 with leader)
        List<String> competingIds = new ArrayList<>();
        if (top2 != null && top1.score() >= 0.40 && top2.score() >= 0.40 && scoreGap < 0.10) {
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).score() >= 0.40) {
                    competingIds.add(sorted.get(i).hypothesisId());
                }
            }
        }

        boolean nearTie = !competingIds.isEmpty();

        // Identify decisive evidence: supporting evidence unique to top1
        List<String> decisiveEvidenceIds = findDecisiveEvidence(top1, sorted, verificationResults);

        String summary = buildComparisonSummary(top1, top2, competingIds, scoreGap, nearTie);

        return new HypothesisComparison(
                incident.id(),
                top1.hypothesisId(),
                List.copyOf(competingIds),
                scoreGap,
                decisiveEvidenceIds,
                summary,
                nearTie
        );
    }

    /**
     * Generate the final investigation decision from a comparison.
     */
    public InvestigationDecision decide(
            IncidentTask incident,
            HypothesisComparison comparison,
            List<ConfidenceResult> confidenceResults
    ) {
        ConfidenceResult top1 = confidenceResults.stream()
                .filter(cr -> cr.hypothesisId().equals(comparison.leadingHypothesisId()))
                .findFirst()
                .orElseThrow();

        String decisionType = classifyDecision(top1.score(), comparison);

        List<String> nextProbes = generateNextProbes(comparison, confidenceResults);

        String rationale = buildRationale(top1, comparison, confidenceResults);

        return new InvestigationDecision(
                incident.id(),
                comparison.leadingHypothesisId(),
                decisionType,
                top1.score(),
                rationale,
                nextProbes,
                comparison.competingHypothesisIds()
        );
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private String classifyDecision(double topScore, HypothesisComparison comparison) {
        if (comparison.nearTie()) {
            return "competing_hypotheses";
        }
        double gap = comparison.scoreGap();
        if (topScore >= 0.80 && gap >= 0.15) return "likely_root_cause";
        if (topScore >= 0.60 && gap >= 0.10) return "probable_root_cause";
        if (topScore >= 0.40) return "uncertain_requires_more_evidence";
        return "insufficient_evidence";
    }

    private List<String> findDecisiveEvidence(
            ConfidenceResult leader,
            List<ConfidenceResult> sorted,
            List<VerificationResult> verifications
    ) {
        Set<String> othersSupporting = new HashSet<>();
        for (ConfidenceResult cr : sorted) {
            if (!cr.hypothesisId().equals(leader.hypothesisId())) {
                verifications.stream()
                        .filter(v -> v.hypothesisId().equals(cr.hypothesisId()))
                        .findFirst()
                        .ifPresent(v -> othersSupporting.addAll(v.supportingEvidenceIds()));
            }
        }

        return verifications.stream()
                .filter(v -> v.hypothesisId().equals(leader.hypothesisId()))
                .findFirst()
                .map(v -> v.supportingEvidenceIds().stream()
                        .filter(id -> !othersSupporting.contains(id))
                        .toList())
                .orElse(List.of());
    }

    private String buildComparisonSummary(
            ConfidenceResult top1,
            ConfidenceResult top2,
            List<String> competingIds,
            double scoreGap,
            boolean nearTie
    ) {
        String top1Name = hypothesisShortName(top1.hypothesisId());
        if (!nearTie || top2 == null) {
            return String.format(
                    "%s 为领先假设，分数 %.2f。",
                    top1Name, top1.score()
            );
        }
        String top2Name = hypothesisShortName(top2.hypothesisId());
        return String.format(
                "%s 因部署和配置变更证据略微领先，"
                + "但 %s 因支付延迟和超时证据仍是重要竞争假设。"
                + "分数差距：%.2f。",
                top1Name, top2Name, scoreGap
        );
    }

    private String buildRationale(
            ConfidenceResult top1,
            HypothesisComparison comparison,
            List<ConfidenceResult> allResults
    ) {
        if (comparison.nearTie()) {
            return "排名前两位的假设分数接近，"
                    + "系统保留两种解释，不强制选择单一根因。";
        }
        return String.format(
                "%s 为领先假设，分数 %.2f，与次选假设差距 %.2f。",
                hypothesisShortName(top1.hypothesisId()),
                top1.score(),
                comparison.scoreGap()
        );
    }

    private List<String> generateNextProbes(
            HypothesisComparison comparison,
            List<ConfidenceResult> confidenceResults
    ) {
        List<String> probes = new ArrayList<>();
        if (comparison.nearTie()) {
            probes.add("对比部署前后的超时错误率，确认部署是否引入了变化。");
            probes.add("按接口拆分检查 payment-service 延迟，定位是否为特定接口问题。");
            probes.add("在 staging 或 canary 环境回滚 order-service，对比错误率变化。");
            probes.add("检查重试/超时配置变更对 payment 调用的实际影响。");
        } else {
            probes.add("继续收集领先假设的补充证据。");
            probes.add("验证反驳证据是否可以被排除。");
        }
        return probes;
    }

    private String hypothesisShortName(String hypothesisId) {
        // hyp_deployment_regression → deployment regression
        return hypothesisId.replace("hyp_", "").replace("_", " ");
    }
}
