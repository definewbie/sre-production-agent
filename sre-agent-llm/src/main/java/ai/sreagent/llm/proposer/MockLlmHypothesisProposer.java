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
                "Check order-service timeout error rate before and after deploy",
                "metric_error_rate_spike",
                "Determine if deployment increased timeout errors"
            ),
            new ProbeIntent(
                ProbeType.PROMETHEUS_QUERY,
                "payment-service", "payment-service",
                "Check payment-service p95 latency during incident window",
                "metric_latency_p95_spike",
                "Determine if payment-service latency spiked after deploy"
            ),
            new ProbeIntent(
                ProbeType.LOKI_QUERY,
                "order-service", "order-service",
                "Search order-service logs for retry exhausted and downstream timeout",
                "log_retry_exhausted",
                "Find evidence of retry amplification"
            ),
            new ProbeIntent(
                ProbeType.TRACE_QUERY,
                "order-service", "order-service->payment-service",
                "Inspect order-service to payment-service span latency and error spans",
                "trace_downstream_span_slow",
                "Confirm downstream latency amplification in trace data"
            ),
            new ProbeIntent(
                ProbeType.KUBERNETES_QUERY,
                "order-service", "order-service-pods",
                "Check order-service pod restart/readiness to rule out local runtime instability",
                "pod_restart_count_increased",
                "Rule out local pod issues as alternative cause"
            )
        );

        VerificationPlan plan = new VerificationPlan(
            List.of(
                "timeout config diff exact value",
                "pre/post deploy timeout error rate",
                "payment-service p95 latency by endpoint",
                "client-side cancellation / timeout metrics",
                "retry exhausted logs",
                "order-service -> payment-service slow/error spans"
            ),
            List.of(
                "timeout config diff",
                "client-side cancellation metrics",
                "retry exhausted logs"
            ),
            List.of(
                "payment-service independent error spike",
                "network partition evidence"
            ),
            probes
        );

        return new UnverifiedHypothesisProposal(
            "llm_prop_deployment_timeout_amplification",
            "Deployment timeout change may have amplified downstream latency",
            "deployment_downstream_amplification_loop",
            result.incident() != null ? result.incident().service() : "order-service",
            "Deployment timeout/retry configuration change amplified downstream latency symptoms, " +
                "creating a feedback loop between order-service and payment-service",
            "The two leading hypotheses (deployment regression and downstream dependency latency) " +
                "may not be independent. A deployment timeout or retry behavior change could have " +
                "amplified downstream latency, creating an apparent competing signal. " +
                "The deployment changed timeout/retry configuration, which caused more aggressive " +
                "retries to payment-service, which increased load and latency on payment-service, " +
                "which then appeared as both a deployment regression and a downstream dependency issue.",
            List.of(
                "competing_hypotheses decision with small score gap",
                "deployment event near incident window",
                "downstream latency spike on payment-service"
            ),
            plan,
            0.35,
            ProposalStatus.UNVERIFIED_PROPOSAL,
            false
        );
    }
}
