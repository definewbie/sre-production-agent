package ai.sreagent.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioFApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postScenarioFReturns200() throws Exception {
        mockMvc.perform(post("/api/investigations/scenario-f"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("likely_root_cause"))
                .andExpect(jsonPath("$.selectedHypothesisId").value("hyp_pod_crash_loop"))
                .andExpect(jsonPath("$.confidenceScore").value(greaterThanOrEqualTo(0.80)))
                .andExpect(jsonPath("$.reportUrl").value(containsString("/report")))
                .andExpect(jsonPath("$.traceUrl").value(containsString("/trace")));
    }

    @Test
    void scenarioFGetSummaryReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-f"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String incidentId = objectMapper.readTree(body).get("incidentId").asText();

        mockMvc.perform(get("/api/investigations/" + incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.decisionType").value("likely_root_cause"));
    }

    @Test
    void scenarioFGetReportReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-f"))
                .andReturn();
        String incidentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("incidentId").asText();

        mockMvc.perform(get("/api/investigations/" + incidentId + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("竞争假设分析报告")));
    }

    @Test
    void scenarioFGetTraceReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/investigations/scenario-f"))
                .andReturn();
        String incidentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("incidentId").asText();

        mockMvc.perform(get("/api/investigations/" + incidentId + "/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].event_type").exists());
    }
}
