package ai.sreagent.llm.proposer;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.*;
import ai.sreagent.core.workflow.InvestigationResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test helper for building test fixtures for proposer tests.
 */
final class ProposerTestHelper {

    private ProposerTestHelper() {}

    /**
     * Scenario E: competing_hypotheses with small score gap (0.06).
     */
    static InvestigationResult scenarioEResult() {
        return buildResult("competing_hypotheses", 0.64, 0.06);
    }

    /**
     * Scenario F: likely_root_cause with high confidence (0.92) and clear gap.
     */
    static InvestigationResult scenarioFResult() {
        return buildResult("likely_root_cause", 0.92, 0.30);
    }

    static InvestigationResult buildResult(String decisionType, double confidence, double scoreGap) {
        IncidentTask incident = new IncidentTask(
            "inc-test-001", "PaymentServiceLatencySpike", "order-service",
            "demo", "critical", Instant.parse("2025-01-15T10:00:00Z"),
            Map.of(), Map.of()
        );

        Hypothesis h1 = new Hypothesis("h-deploy", "inc-test-001", "p-deploy",
            "Deployment Regression", "deployment_regression", "order-service",
            "Recent deployment caused regression");
        Hypothesis h2 = new Hypothesis("h-downstream", "inc-test-001", "p-downstream",
            "Downstream Dependency Latency", "downstream_dependency_latency", "order-service",
            "Payment service experiencing latency");

        ConfidenceResult cr1 = new ConfidenceResult("h-deploy", confidence, "high",
            List.of("deployment event"), List.of(), List.of(), List.of(), "selected", "");
        ConfidenceResult cr2 = new ConfidenceResult("h-downstream", confidence - scoreGap, "medium",
            List.of("latency spike"), List.of(), List.of(), List.of(), "competing", "");

        InvestigationDecision decision = new InvestigationDecision(
            "inc-test-001", "h-deploy", decisionType, confidence,
            "Competing hypotheses", List.of(), List.of("h-downstream")
        );

        HypothesisComparison comparison = new HypothesisComparison(
            "inc-test-001", "h-deploy", List.of("h-downstream"),
            scoreGap, List.of(), "Score gap analysis", scoreGap < 0.10
        );

        VerificationResult vr1 = new VerificationResult("h-deploy",
            List.of("e1"), List.of("e2"), List.of("e3"), List.of(), "");
        VerificationResult vr2 = new VerificationResult("h-downstream",
            List.of("e4"), List.of(), List.of("e5"), List.of(), "");

        return new InvestigationResult(
            "inc-test-001", incident, List.of(h1, h2),
            List.of(vr1, vr2), List.of(cr1, cr2), comparison, decision,
            List.of(), "", List.of()
        );
    }

    static InvestigationResult buildResultWithNullDecision() {
        return new InvestigationResult(
            "inc-null", null, List.of(), List.of(), List.of(), null, null,
            List.of(), "", List.of()
        );
    }

    static List<NormalizedEvidence> normalizedEvidence() {
        return List.of(
            new NormalizedEvidence(
                "deployment_event_detected", "deployment_event_detected",
                EvidenceCategory.DEPLOYMENT, EvidenceSignal.DEPLOYMENT_METADATA,
                EvidenceSourceKind.STATIC, EvidenceSeverity.WARNING,
                EvidenceCausalRole.CONTEXT,
                "order-service", "order-service", "demo",
                0.70, Instant.now(), "Deployment detected", Map.of()
            ),
            new NormalizedEvidence(
                "metric_downstream_latency_spike", "metric_downstream_latency_spike",
                EvidenceCategory.METRIC, EvidenceSignal.LATENCY_SPIKE,
                EvidenceSourceKind.PROMETHEUS, EvidenceSeverity.CRITICAL,
                EvidenceCausalRole.SYMPTOM,
                "payment-service", "payment-service", "demo",
                0.85, Instant.now(), "P95 latency spike", Map.of()
            )
        );
    }
}
