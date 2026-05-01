package ai.sreagent.llm.synthesis;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.LlmRequest;
import ai.sreagent.llm.client.LlmResponse;
import ai.sreagent.llm.client.MockLlmClient;
import ai.sreagent.llm.model.LlmEnhancedReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReportSynthesizerTest {

    @Test
    void synthesizesWithMockClient() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        InvestigationResult result = createScenarioEResult();

        LlmEnhancedReport report = synthesizer.synthesize(result);

        assertThat(report.incidentId()).isEqualTo("inc_test");
        assertThat(report.baseDecisionType()).isEqualTo("competing_hypotheses");
        assertThat(report.baseSelectedHypothesisId()).isEqualTo("hyp_deployment_regression");
        assertThat(report.baseConfidenceScore()).isEqualTo(0.64);
        assertThat(report.baseScoreGap()).isEqualTo(0.06);
    }

    @Test
    void executiveSummaryIsNotBlank() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.executiveSummary()).isNotBlank();
    }

    @Test
    void reasoningNarrativeIsNotBlank() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.reasoningNarrative()).isNotBlank();
    }

    @Test
    void uncertaintyExplanationIsNotBlank() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.uncertaintyExplanation()).isNotBlank();
    }

    @Test
    void evidenceScopeNoteIsNotBlank() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.evidenceScopeNote()).isNotBlank();
        assertThat(report.evidenceScopeNote()).contains("K8s");
        assertThat(report.evidenceScopeNote()).contains("CMDB");
    }

    @Test
    void modelProviderIsMock() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.modelProvider()).isEqualTo("mock");
    }

    @Test
    void advisoryOnlyIsTrue() {
        LlmReportSynthesizer synthesizer = new LlmReportSynthesizer(new MockLlmClient());
        LlmEnhancedReport report = synthesizer.synthesize(createScenarioEResult());

        assertThat(report.advisoryOnly()).isTrue();
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
                List.of("check_redis_latency"),
                List.of("hyp_downstream_dependency_latency")
        );

        return new InvestigationResult(
                incidentId, incident, hypotheses, List.of(), confResults,
                comparison, decision, List.of(), "# Report",
                List.of(new EventTraceEntry("evt_001", incidentId, "INCIDENT_CREATED",
                        Instant.now(), Map.of()))
        );
    }
}
