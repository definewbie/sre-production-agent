package ai.sreagent.server.controller;

import ai.sreagent.probe.*;
import ai.sreagent.probe.policy.ProbeExecutionPolicy;
import ai.sreagent.server.service.ProbeExecutionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the probe execution REST API (Step S).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProbeExecutionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProbeExecutionService probeService;

    @Test
    void scenarioEProposeAndExecuteProbes_returnsOk() throws Exception {
        mockMvc.perform(post("/api/investigations/scenario-e/propose-and-execute-probes")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canAffectDecision").value(false))
            .andExpect(jsonPath("$.status").value("EXECUTED"))
            .andExpect(jsonPath("$.incidentId").isNotEmpty());
    }

    @Test
    void scenarioEProposeAndExecuteProbes_producesEvidence() throws Exception {
        ProbeExecutionResult result = probeService.proposeAndExecuteScenarioE();

        assertThat(result.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.normalizedEvidence()).isNotEmpty();
        assertThat(result.canAffectDecision()).isFalse();
    }

    @Test
    void scenarioEProposeAndExecuteProbes_doesNotAllowLiveMode() {
        // Service always uses FIXTURE mode — no LIVE mode available
        ProbeExecutionPolicy policy = new ProbeExecutionPolicy();
        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan livePlan = router.createPlan("test", "test", List.of(), ProbeExecutionMode.LIVE);
        assertThat(policy.allows(livePlan)).isFalse();
    }
}
