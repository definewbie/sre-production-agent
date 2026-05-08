package ai.sreagent.llm.proposer;

import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic mock proposer for tests.
 * No real LLM API required.
 *
 * Scenario E (competing_hypotheses, scoreGap < 0.10):
 *   → 1 proposal: deployment_timeout_amplification
 *
 * Scenario F (likely_root_cause, confidence >= 0.80):
 *   → 0 proposals, advisoryOnly=true
 */
public class MockLlmHypothesisProposer implements LlmHypothesisProposer {

    @Override
    public LlmHypothesisProposalResult propose(
            InvestigationResult result,
            List<NormalizedEvidence> normalizedEvidence
    ) {
        String incidentId = result.incidentId();
        String decisionType = result.decision().decisionType();
        double topScore = result.decision().confidenceScore();
        double scoreGap = result.comparison() != null ? result.comparison().scoreGap() : 1.0;

        List<UnverifiedHypothesisProposal> proposals = new ArrayList<>();

        boolean shouldPropose = shouldPropose(result);

        if (shouldPropose) {
            proposals.add(buildScenarioEProposal(result));
        }

        return new LlmHypothesisProposalResult(
            incidentId,
            decisionType,
            result.decision().selectedHypothesisId(),
            topScore,
            scoreGap,
            List.copyOf(proposals),
            !shouldPropose || proposals.isEmpty(),
            "mock"
        );
    }

    @Override
    public String proposerName() {
        return "mock-llm-hypothesis-proposer";
    }

    private boolean shouldPropose(InvestigationResult result) {
        String decisionType = result.decision().decisionType();
        double confidence = result.decision().confidenceScore();
        double scoreGap = result.comparison() != null ? result.comparison().scoreGap() : 1.0;

        // Trigger: competing hypotheses, uncertain, low confidence, or small gap
        if ("competing_hypotheses".equals(decisionType)) return true;
        if ("uncertain_requires_more_evidence".equals(decisionType)) return true;
        if ("insufficient_evidence".equals(decisionType)) return true;
        if (confidence < 0.60) return true;
        if (scoreGap < 0.10) return true;

        // Do not trigger: clear RCA with good confidence and gap
        if ("likely_root_cause".equals(decisionType) && confidence >= 0.80 && scoreGap >= 0.15) {
            return false;
        }

        return false;
    }

    private UnverifiedHypothesisProposal buildScenarioEProposal(InvestigationResult result) {
        List<ProbeIntent> probes = List.of(
            new ProbeIntent(
                ProbeType.PROMETHEUS_QUERY,
                "order-service", "order-service",
                "检查 order-service 部署前后的超时错误率变化",
                "metric_error_rate_spike",
                "判断部署是否导致超时错误增加"
            ),
            new ProbeIntent(
                ProbeType.PROMETHEUS_QUERY,
                "payment-service", "payment-service",
                "检查 payment-service 在故障窗口的 P95 延迟",
                "metric_latency_p95_spike",
                "判断 payment-service 延迟是否在部署后飙升"
            ),
            new ProbeIntent(
                ProbeType.LOKI_QUERY,
                "order-service", "order-service",
                "搜索 order-service 日志中的重试耗尽和下游超时记录",
                "log_retry_exhausted",
                "查找重试放大效应的证据"
            ),
            new ProbeIntent(
                ProbeType.TRACE_QUERY,
                "order-service", "order-service->payment-service",
                "检查 order-service 到 payment-service 的调用链路延迟和错误 span",
                "trace_downstream_span_slow",
                "从链路追踪确认下游延迟放大效应"
            ),
            new ProbeIntent(
                ProbeType.KUBERNETES_QUERY,
                "order-service", "order-service-pods",
                "检查 order-service Pod 重启/就绪状态，排除本地运行时异常",
                "pod_restart_count_increased",
                "排除 Pod 本地问题作为替代根因"
            )
        );

        VerificationPlan plan = new VerificationPlan(
            List.of(
                "超时配置差异的具体数值",
                "部署前后超时错误率对比",
                "payment-service 按接口的 P95 延迟",
                "客户端取消/超时相关指标",
                "重试耗尽日志",
                "order-service → payment-service 慢/错误 span"
            ),
            List.of(
                "超时配置差异",
                "客户端取消指标",
                "重试耗尽日志"
            ),
            List.of(
                "payment-service 独立错误飙升",
                "网络分区证据"
            ),
            probes
        );

        return new UnverifiedHypothesisProposal(
            "llm_prop_deployment_timeout_amplification",
            "部署超时变更可能放大了下游延迟",
            "deployment_downstream_amplification_loop",
            result.incident() != null ? result.incident().service() : "order-service",
            "部署中的超时/重试配置变更放大了下游延迟现象，" +
                "在 order-service 和 payment-service 之间形成了反馈循环",
            "排名前两位的假设（部署回归和下游依赖延迟）可能并非独立。" +
                "部署中引入了超时或重试行为的变更，导致对 payment-service 的重试更激进，" +
                "从而增加了 payment-service 的负载和延迟，" +
                "最终同时表现为部署回归和下游依赖问题两种信号。",
            List.of(
                "竞争假设决策模式下分数差距较小",
                "部署事件发生在故障窗口附近",
                "payment-service 下游延迟飙升"
            ),
            plan,
            0.35,
            ProposalStatus.UNVERIFIED_PROPOSAL,
            false
        );
    }
}
