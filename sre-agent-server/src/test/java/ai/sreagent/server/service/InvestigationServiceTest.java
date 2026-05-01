package ai.sreagent.server.service;

import ai.sreagent.core.workflow.InvestigationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationServiceTest {

    private InMemoryInvestigationStore store;
    private InvestigationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryInvestigationStore();
        service = new InvestigationService(store);
    }

    @Test
    void runScenarioEReturnsCompetingHypotheses() throws Exception {
        InvestigationResponse response = service.runScenarioE();

        assertEquals("competing_hypotheses", response.decisionType());
        assertEquals("hyp_deployment_regression", response.selectedHypothesisId());
    }

    @Test
    void runScenarioEReturnsCorrectScores() throws Exception {
        InvestigationResponse response = service.runScenarioE();

        assertEquals(0.64, response.scores().get("hyp_deployment_regression"), 0.01);
        assertEquals(0.58, response.scores().get("hyp_downstream_dependency_latency"), 0.01);
        assertTrue(response.scores().containsKey("hyp_pod_oom_killed"));
    }

    @Test
    void runScenarioEReturnsCorrectGap() throws Exception {
        InvestigationResponse response = service.runScenarioE();
        assertEquals(0.06, response.scoreGap(), 0.01);
    }

    @Test
    void runScenarioEStoresResult() throws Exception {
        InvestigationResponse response = service.runScenarioE();

        assertTrue(store.findByIncidentId(response.incidentId()).isPresent());
    }

    @Test
    void getReportReturnsMarkdown() throws Exception {
        InvestigationResponse response = service.runScenarioE();

        String report = service.getReport(response.incidentId()).orElseThrow();
        assertTrue(report.contains("Competing Hypotheses Report"));
    }

    @Test
    void getTraceReturnsEntries() throws Exception {
        InvestigationResponse response = service.runScenarioE();

        var trace = service.getTrace(response.incidentId()).orElseThrow();
        assertFalse(trace.isEmpty());
        assertTrue(trace.stream().anyMatch(e -> "INCIDENT_CREATED".equals(e.eventType())));
    }

    @Test
    void getReportForMissingReturnsEmpty() {
        assertTrue(service.getReport("not-exist").isEmpty());
    }

    @Test
    void getTraceForMissingReturnsEmpty() {
        assertTrue(service.getTrace("not-exist").isEmpty());
    }
}
