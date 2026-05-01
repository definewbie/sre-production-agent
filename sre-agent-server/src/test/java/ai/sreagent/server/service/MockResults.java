package ai.sreagent.server.service;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;

import java.time.Instant;
import java.util.*;

/**
 * Test helper to create mock InvestigationResult without running the real workflow.
 */
public class MockResults {

    public static InvestigationResult create(String incidentId) {
        return create(incidentId, "competing_hypotheses", "hyp_deployment_regression",
                0.64, 0.06);
    }

    public static InvestigationResult create(String incidentId, String decisionType,
                                              String selectedId, double confidence, double gap) {
        IncidentTask incident = new IncidentTask(incidentId, "HighErrorRate",
                "order-service", "prod", "critical", Instant.now(),
                Map.of(), Map.of());

        List<Hypothesis> hypotheses = List.of(
                new Hypothesis("hyp_deployment_regression", incidentId, "pattern_deploy",
                        "Deployment regression", "deployment", "order-service", "timeout config change"),
                new Hypothesis("hyp_downstream_dependency_latency", incidentId, "pattern_dep",
                        "Downstream latency", "dependency", "checkout-service", "payment timeout"),
                new Hypothesis("hyp_pod_oom_killed", incidentId, "pattern_oom",
                        "OOM killed", "resource", "recommend-service", "memory limit too low")
        );

        List<ConfidenceResult> confResults = List.of(
                new ConfidenceResult("hyp_deployment_regression", confidence, "moderate",
                        List.of("deploy_before_alert"), List.of("no_5xx_on_payment"), List.of(),
                        List.of(), "COMPETING", "near tie with downstream"),
                new ConfidenceResult("hyp_downstream_dependency_latency", confidence - gap, "moderate",
                        List.of("payment_latency_high"), List.of(), List.of("redis_check"),
                        List.of(), "COMPETING", "plausible alternative"),
                new ConfidenceResult("hyp_pod_oom_killed", 0.05, "low",
                        List.of(), List.of("no_oom_event"), List.of(),
                        List.of("no_restart_evidence"), "REJECTED", "no evidence")
        );

        HypothesisComparison comparison = new HypothesisComparison(
                incidentId, selectedId,
                List.of("hyp_downstream_dependency_latency"),
                gap, List.of(), "deployment leads by small margin", gap < 0.10
        );

        InvestigationDecision decision = new InvestigationDecision(
                incidentId, selectedId, decisionType, confidence,
                "two hypotheses within 0.10 gap",
                List.of("check_redis_latency", "check_payment_circuit_breaker"),
                List.of("hyp_downstream_dependency_latency")
        );

        return new InvestigationResult(
                incidentId, incident, hypotheses, List.of(), confResults,
                comparison, decision, List.of(),
                "# Competing Hypotheses Report\n\nDecision: " + decisionType,
                List.of(new EventTraceEntry("evt_001", incidentId, "INCIDENT_CREATED",
                        Instant.now(), Map.of("alert", "HighErrorRate")))
        );
    }
}
