package ai.sreagent.server.controller;

import ai.sreagent.core.domain.EventTraceEntry;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.server.service.InMemoryInvestigationStore;
import ai.sreagent.server.service.InvestigationService;
import ai.sreagent.server.service.MockResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryInvestigationStore store;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStore() {
        // Note: InMemoryInvestigationStore has no clear method, but each test is independent
    }

    @Test
    void postScenarioEReturns200() throws Exception {
        mockMvc.perform(post("/api/investigations/scenario-e"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("uncertain_requires_more_evidence"))
                .andExpect(jsonPath("$.selectedHypothesisId").value("hyp_downstream_dependency_latency"))
                .andExpect(jsonPath("$.scoreGap").value(closeTo(0.05, 0.01)))
                .andExpect(jsonPath("$.confidenceScore").value(closeTo(0.41, 0.01)))
                .andExpect(jsonPath("$.scores.hyp_deployment_regression").value(closeTo(0.36, 0.01)))
                .andExpect(jsonPath("$.scores.hyp_downstream_dependency_latency").value(closeTo(0.41, 0.01)))
                .andExpect(jsonPath("$.competingHypotheses").isEmpty())
                .andExpect(jsonPath("$.reportUrl").value(containsString("/report")))
                .andExpect(jsonPath("$.traceUrl").value(containsString("/trace")));
    }

    @Test
    void getSummaryReturns200() throws Exception {
        // First create an investigation
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-e"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String incidentId = objectMapper.readTree(body).get("incidentId").asText();

        // Then fetch summary
        mockMvc.perform(get("/api/investigations/" + incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.decisionType").value("uncertain_requires_more_evidence"));
    }

    @Test
    void getReportReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-e"))
                .andReturn();
        String incidentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("incidentId").asText();

        mockMvc.perform(get("/api/investigations/" + incidentId + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("竞争假设分析报告")));
    }

    @Test
    void getTraceReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-e"))
                .andReturn();
        String incidentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("incidentId").asText();

        mockMvc.perform(get("/api/investigations/" + incidentId + "/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].event_type").exists());
    }

    @Test
    void getMissingIncidentReturns404() throws Exception {
        mockMvc.perform(get("/api/investigations/not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMissingReportReturns404() throws Exception {
        mockMvc.perform(get("/api/investigations/not-exist/report"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMissingTraceReturns404() throws Exception {
        mockMvc.perform(get("/api/investigations/not-exist/trace"))
                .andExpect(status().isNotFound());
    }
}
