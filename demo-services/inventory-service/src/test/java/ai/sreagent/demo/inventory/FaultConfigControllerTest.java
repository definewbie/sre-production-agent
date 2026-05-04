package ai.sreagent.demo.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaultConfigController.class)
@Import(FaultConfigController.class)
class FaultConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetConfig() throws Exception {
        mockMvc.perform(post("/fault-config")
                        .contentType("application/json")
                        .content("{\"mode\":\"normal\",\"latencyMs\":0,\"errorRate\":0.0,\"timeoutRate\":0.0}"))
                .andExpect(status().isOk());
    }

    @Test
    void getFaultConfigShouldReturnDefault() throws Exception {
        mockMvc.perform(get("/fault-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("normal"));
    }

    @Test
    void postFaultConfigShouldUpdate() throws Exception {
        mockMvc.perform(post("/fault-config")
                        .contentType("application/json")
                        .content("{\"mode\":\"latency\",\"latencyMs\":100,\"errorRate\":0.0,\"timeoutRate\":0.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("latency"))
                .andExpect(jsonPath("$.latencyMs").value(100));
    }
}
