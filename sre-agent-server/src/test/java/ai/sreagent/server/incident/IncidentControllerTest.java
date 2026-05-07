package ai.sreagent.server.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@DisplayName("IncidentController API")
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    // ── GET /api/incidents/alerts ────────────────────────────────

    @Nested
    @DisplayName("GET /api/incidents/alerts")
    class ListFiringAlerts {

        @Test
        @DisplayName("returns 200 with alert list")
        void returnsAlertList() throws Exception {
            var alert = new AlertView("fp1", "HighLatency", "order-svc",
                    "production", "critical", "active", "2025-01-01T00:00:00Z", "P99 > 5s");
            given(incidentService.fetchFiringAlerts()).willReturn(List.of(alert));

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].fingerprint").value("fp1"))
                    .andExpect(jsonPath("$[0].alertName").value("HighLatency"))
                    .andExpect(jsonPath("$[0].service").value("order-svc"))
                    .andExpect(jsonPath("$[0].severity").value("critical"))
                    .andExpect(jsonPath("$[0].summary").value("P99 > 5s"));
        }

        @Test
        @DisplayName("returns empty array when no alerts")
        void emptyAlerts() throws Exception {
            given(incidentService.fetchFiringAlerts()).willReturn(List.of());

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("returns multiple alerts")
        void multipleAlerts() throws Exception {
            var a1 = new AlertView("fp1", "HighLatency", "svc-a", "default", "warning", "active", null, "");
            var a2 = new AlertView("fp2", "OOM", "svc-b", "default", "critical", "active", null, "");
            given(incidentService.fetchFiringAlerts()).willReturn(List.of(a1, a2));

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // ── POST /api/incidents/from-alert ───────────────────────────

    @Nested
    @DisplayName("POST /api/incidents/from-alert")
    class TriggerRca {

        @Test
        @DisplayName("returns 200 with COMPLETED result")
        void triggerSuccess() throws Exception {
            var result = IncidentRcaResultView.running("inc-001", "HighLatency", "svc", "critical");
            // Simulate completed
            var completed = new IncidentRcaResultView(
                    "inc-001", "COMPLETED", "alertmanager",
                    "HighLatency", "order-svc", "production", "critical",
                    "2025-01-01T00:00:00Z", "insufficient_evidence", null,
                    0.42, 0.10, Map.of("h1", 0.42), "/api/incidents/inc-001/report",
                    250, null
            );
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(completed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fingerprint\":\"fp1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.incidentId").value("inc-001"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.triggerSource").value("alertmanager"))
                    .andExpect(jsonPath("$.alertName").value("HighLatency"))
                    .andExpect(jsonPath("$.decisionType").value("insufficient_evidence"))
                    .andExpect(jsonPath("$.durationMs").value(250));
        }

        @Test
        @DisplayName("returns 400 when alert not found (FAILED)")
        void triggerFailed() throws Exception {
            var failed = IncidentRcaResultView.failed("inc-alert-1", "NotFound", null, "未找到匹配的告警");
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(failed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fingerprint\":\"nonexistent\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").value("未找到匹配的告警"));
        }

        @Test
        @DisplayName("accepts alertName + service body")
        void triggerByNameMatch() throws Exception {
            var completed = new IncidentRcaResultView(
                    "inc-002", "COMPLETED", "alertmanager",
                    "HighErrorRate", "api-svc", null, "warning",
                    null, "insufficient_evidence", null,
                    0.30, 0.0, null, "/api/incidents/inc-002/report",
                    100, null
            );
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(completed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"alertName\":\"HighErrorRate\",\"service\":\"api-svc\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alertName").value("HighErrorRate"))
                    .andExpect(jsonPath("$.service").value("api-svc"));
        }
    }

    // ── GET /api/incidents/{incidentId} ──────────────────────────

    @Nested
    @DisplayName("GET /api/incidents/{incidentId}")
    class GetIncident {

        @Test
        @DisplayName("returns 200 with incident result")
        void found() throws Exception {
            var view = IncidentRcaResultView.running("inc-003", "Test", "svc", "warning");
            given(incidentService.getIncident("inc-003")).willReturn(Optional.of(view));

            mockMvc.perform(get("/api/incidents/inc-003"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.incidentId").value("inc-003"))
                    .andExpect(jsonPath("$.status").value("RUNNING"));
        }

        @Test
        @DisplayName("returns 404 when not found")
        void notFound() throws Exception {
            given(incidentService.getIncident("nonexistent")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/incidents/nonexistent"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/incidents/{incidentId}/report ───────────────────

    @Nested
    @DisplayName("GET /api/incidents/{incidentId}/report")
    class GetReport {

        @Test
        @DisplayName("returns 200 with report map")
        void found() throws Exception {
            given(incidentService.getReport("inc-004"))
                    .willReturn(Optional.of("# RCA Report\n\nAnalysis..."));

            mockMvc.perform(get("/api/incidents/inc-004/report"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.report").value("# RCA Report\n\nAnalysis..."));
        }

        @Test
        @DisplayName("returns 404 when no report")
        void notFound() throws Exception {
            given(incidentService.getReport("nonexistent")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/incidents/nonexistent/report"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/incidents/{incidentId}/rca ──────────────────────

    @Nested
    @DisplayName("GET /api/incidents/{incidentId}/rca")
    class GetIncidentRca {

        @Test
        @DisplayName("returns 200 with full RCA data")
        void found() throws Exception {
            Map<String, Object> rcaData = Map.of(
                    "scenarioId", "inc-005",
                    "scenarioName", "Alert-driven: HighLatency",
                    "status", "COMPLETED",
                    "phase", "completed",
                    "incidentId", "inc-005"
            );
            given(incidentService.getIncidentRca("inc-005")).willReturn(Optional.of(rcaData));

            mockMvc.perform(get("/api/incidents/inc-005/rca"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scenarioId").value("inc-005"))
                    .andExpect(jsonPath("$.scenarioName").value("Alert-driven: HighLatency"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("returns 404 when no RCA data")
        void notFound() throws Exception {
            given(incidentService.getIncidentRca("nonexistent")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/incidents/nonexistent/rca"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/incidents ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/incidents")
    class ListIncidents {

        @Test
        @DisplayName("returns 200 with incident list")
        void returnsList() throws Exception {
            var v1 = IncidentRcaResultView.running("inc-a", "Alert1", "svc", "warning");
            var v2 = IncidentRcaResultView.running("inc-b", "Alert2", "svc", "critical");
            given(incidentService.listIncidents()).willReturn(List.of(v1, v2));

            mockMvc.perform(get("/api/incidents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].incidentId").value("inc-a"))
                    .andExpect(jsonPath("$[1].incidentId").value("inc-b"));
        }

        @Test
        @DisplayName("returns empty array when no incidents")
        void empty() throws Exception {
            given(incidentService.listIncidents()).willReturn(List.of());

            mockMvc.perform(get("/api/incidents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }
}
