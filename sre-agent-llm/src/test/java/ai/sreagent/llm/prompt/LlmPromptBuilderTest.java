package ai.sreagent.llm.prompt;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptBuilderTest {

    private final LlmPromptBuilder builder = new LlmPromptBuilder();

    @Test
    void promptContainsDecisionType() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("competing_hypotheses");
    }

    @Test
    void promptContainsSelectedHypothesis() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("hyp_deployment_regression");
    }

    @Test
    void promptContainsScores() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("0.64");
    }

    @Test
    void promptContainsCompetingHypothesis() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("hyp_downstream_dependency_latency");
    }

    @Test
    void promptContainsScoreGap() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("0.06");
    }

    @Test
    void promptContainsTopologyEvidence() {
        LlmRequest request = builder.build(createScenarioEResult());
        String prompt = request.userPrompt();
        assertThat(prompt.contains("order-service") || prompt.contains("payment-service")).isTrue();
    }

    @Test
    void systemPromptContainsConstraints() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.systemPrompt()).contains("你不得编造证据");
        assertThat(request.systemPrompt()).contains("你不得更改决策结论");
    }

    @Test
    void systemPromptContainsMultiPlatformPolicy() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.systemPrompt()).contains("你不得编造 K8s");
    }

    @Test
    void systemPromptContainsAdjudicatePhrase() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.systemPrompt()).contains("LLM 可以协助，但不能裁决");
    }

    @Test
    void userPromptContainsConstraints() {
        LlmRequest request = builder.build(createScenarioEResult());
        assertThat(request.userPrompt()).contains("Do not infer missing K8s");
    }

    private InvestigationResult createScenarioEResult() {
        String incidentId = "inc_test";
        IncidentTask incident = new IncidentTask(incidentId, "HighErrorRate",
                "order-service", "prod", "critical", Instant.now(), Map.of(), Map.of());

        List<Hypothesis> hypotheses = List.of(
                new Hypothesis("hyp_deployment_regression", incidentId, "pattern_deploy",
                        "Deployment regression", "deployment", "order-service", "timeout config change"),
                new Hypothesis("hyp_downstream_dependency_latency", incidentId, "pattern_dep",
                        "Downstream latency", "dependency", "checkout-service", "payment timeout")
        );

        List<VerificationResult> verResults = List.of(
                new VerificationResult("hyp_deployment_regression",
                        List.of("ev_001", "ev_004"), List.of("ev_006"), List.of(), List.of(), "supported"),
                new VerificationResult("hyp_downstream_dependency_latency",
                        List.of("ev_005", "ev_008"), List.of(), List.of(), List.of(), "supported")
        );

        List<ConfidenceResult> confResults = List.of(
                new ConfidenceResult("hyp_deployment_regression", 0.64, "moderate",
                        List.of("deploy_before_alert"), List.of("no_5xx_on_payment"), List.of(),
                        List.of(), "COMPETING", "near tie with downstream"),
                new ConfidenceResult("hyp_downstream_dependency_latency", 0.58, "moderate",
                        List.of("payment_latency_high"), List.of(), List.of("redis_check"),
                        List.of(), "COMPETING", "plausible alternative")
        );

        HypothesisComparison comparison = new HypothesisComparison(
                incidentId, "hyp_deployment_regression",
                List.of("hyp_downstream_dependency_latency"),
                0.06, List.of(), "deployment leads by small margin", true
        );

        InvestigationDecision decision = new InvestigationDecision(
                incidentId, "hyp_deployment_regression", "competing_hypotheses", 0.64,
                "two hypotheses within 0.10 gap",
                List.of("check_redis_latency", "check_payment_circuit_breaker"),
                List.of("hyp_downstream_dependency_latency")
        );

        List<Evidence> evidence = List.of(
                new Evidence("ev_008", incidentId, "topology", "service_topology_match",
                        "order-service", Instant.now(),
                        "order-service depends on payment-service", Map.of(), 0.8)
        );

        return new InvestigationResult(
                incidentId, incident, hypotheses, verResults, confResults,
                comparison, decision, evidence, "# Report",
                List.of(new EventTraceEntry("evt_001", incidentId, "INCIDENT_CREATED",
                        Instant.now(), Map.of("alert", "HighErrorRate")))
        );
    }
}
