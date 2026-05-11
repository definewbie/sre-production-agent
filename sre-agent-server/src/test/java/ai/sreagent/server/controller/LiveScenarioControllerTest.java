
package ai.sreagent.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LiveScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void simulateReturnsCompletedResult() throws Exception {
        mockMvc.perform(get("/api/live-scenario/simulate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.scenarioId").value(startsWith("scenario-g-")))
                .andExpect(jsonPath("$.baseRca").isNotEmpty())
                .andExpect(jsonPath("$.evidenceReport").isNotEmpty());
    }

    @Test
    void simulateHasEvidenceReport() throws Exception {
        mockMvc.perform(get("/api/live-scenario/simulate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceReport.totalEvidenceCount").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void runWithSimulationModeReturnsResult() throws Exception {
        mockMvc.perform(post("/api/live-scenario/run")
                        .contentType("application/json")
                        .content("{\"mode\":\"simulation\",\"faultMode\":\"latency\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(content().string(containsString("chaos_fault_injected")))
                .andExpect(content().string(containsString("Propagation Score")))
                .andExpect(content().string(containsString("CONFIGURED_TOPOLOGY")));
    }

    @Test
    void getLatestReturnsResultAfterSimulate() throws Exception {
        // First run a simulation
        mockMvc.perform(get("/api/live-scenario/simulate"))
                .andExpect(status().isOk());

        // Then fetch latest
        mockMvc.perform(get("/api/live-scenario/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getScenarioByIdReturnsResult() throws Exception {
        String body = mockMvc.perform(get("/api/live-scenario/simulate"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String scenarioId = objectMapper
                .readTree(body).get("scenarioId").asText();

        mockMvc.perform(get("/api/live-scenario/" + scenarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId));
    }

    @Test
    void getScenarioDetailByIdReturnsResult() throws Exception {
        String body = mockMvc.perform(get("/api/live-scenario/simulate"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String scenarioId = objectMapper
                .readTree(body).get("scenarioId").asText();

        mockMvc.perform(get("/api/live-scenario/detail/" + scenarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId));
    }

    @Test
    void getUnknownScenarioIdReturns404() throws Exception {
        mockMvc.perform(get("/api/live-scenario/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAllReturnsArray() throws Exception {
        mockMvc.perform(get("/api/live-scenario/simulate")).andExpect(status().isOk());

        mockMvc.perform(get("/api/live-scenario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test
    void resetReturnsOk() throws Exception {
        mockMvc.perform(post("/api/live-scenario/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
