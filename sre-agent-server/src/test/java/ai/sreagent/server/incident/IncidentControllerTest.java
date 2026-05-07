package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.relevance.AlertRelevance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@DisplayName("IncidentController API (V.2-UI-6.1)")
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    /** Helper: build classified AlertView */
    private AlertView serviceAlert(String fp, String name, String svc, String ns) {
        return new AlertView(fp, name, svc, ns, "warning", "active",
                "2025-01-01T00:00:00Z", "summary",
                AlertRelevance.SERVICE_ALERT, true, null,
                Map.of("alertname", name, "service", svc, "namespace", ns), Map.of());
    }

    private AlertView platformAlert(String fp, String name, String ns) {
        return new AlertView(fp, name, "node-exporter", ns, "warning", "active",
                "2025-01-01T00:00:00Z", "",
                AlertRelevance.PLATFORM_ALERT, false, "平台告警",
                Map.of("alertname", name, "namespace", ns), Map.of());
    }

    private AlertView watchdogAlert(String fp) {
        return new AlertView(fp, "Watchdog", "prometheus", "monitoring", "none", "active",
                "2025-01-01T00:00:00Z", "",
                AlertRelevance.WATCHDOG_ALERT, false, "Watchdog 自检告警",
                Map.of("alertname", "Watchdog"), Map.of());
    }

    // ── GET /api/incidents/alerts ────────────────────────────────

    @Nested
    @DisplayName("GET /api/incidents/alerts — classified with summary")
    class ListFiringAlerts {

        @Test
        @DisplayName("returns 200 with AlertsResponse containing summary and classified alerts")
        void returnsClassifiedAlerts() throws Exception {
            var alerts = AlertsResponse.of(List.of(
                    serviceAlert("fp1", "HighLatencyP95", "payment-service", "demo"),
                    platformAlert("fp2", "NodeClockNotSynchronising", "observability"),
                    watchdogAlert("fp3")
            ));
            given(incidentService.fetchClassifiedAlerts()).willReturn(alerts);

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    // summary
                    .andExpect(jsonPath("$.summary.totalAlerts").value(3))
                    .andExpect(jsonPath("$.summary.serviceAlerts").value(1))
                    .andExpect(jsonPath("$.summary.platformAlerts").value(1))
                    .andExpect(jsonPath("$.summary.watchdogAlerts").value(1))
                    .andExpect(jsonPath("$.summary.rcaEligibleAlerts").value(1))
                    // source
                    .andExpect(jsonPath("$.source").value("alertmanager"))
                    // alerts array
                    .andExpect(jsonPath("$.alerts.length()").value(3))
                    // first alert: service alert
                    .andExpect(jsonPath("$.alerts[0].relevance").value("SERVICE_ALERT"))
                    .andExpect(jsonPath("$.alerts[0].rcaEligible").value(true))
                    // second alert: platform
                    .andExpect(jsonPath("$.alerts[1].relevance").value("PLATFORM_ALERT"))
                    .andExpect(jsonPath("$.alerts[1].rcaEligible").value(false))
                    // third: watchdog
                    .andExpect(jsonPath("$.alerts[2].relevance").value("WATCHDOG_ALERT"))
                    .andExpect(jsonPath("$.alerts[2].rcaEligible").value(false))
                    .andExpect(jsonPath("$.alerts[2].ineligibleReason").isNotEmpty());
        }

        @Test
        @DisplayName("returns empty alerts with zero summary")
        void emptyAlerts() throws Exception {
            var empty = AlertsResponse.of(List.of());
            given(incidentService.fetchClassifiedAlerts()).willReturn(empty);

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalAlerts").value(0))
                    .andExpect(jsonPath("$.alerts").isEmpty());
        }

        @Test
        @DisplayName("all alerts are service alerts → all rcaEligible")
        void allServiceAlerts() throws Exception {
            var alerts = AlertsResponse.of(List.of(
                    serviceAlert("fp1", "HighLatency", "order-service", "demo"),
                    serviceAlert("fp2", "HighErrorRate", "payment-service", "demo")
            ));
            given(incidentService.fetchClassifiedAlerts()).willReturn(alerts);

            mockMvc.perform(get("/api/incidents/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.serviceAlerts").value(2))
                    .andExpect(jsonPath("$.summary.rcaEligibleAlerts").value(2))
                    .andExpect(jsonPath("$.summary.platformAlerts").value(0));
        }
    }

    // ── POST /api/incidents/from-alert ───────────────────────────

    @Nested
    @DisplayName("POST /api/incidents/from-alert")
    class TriggerRca {

        @Test
        @DisplayName("returns 200 with COMPLETED result for eligible alert")
        void triggerSuccess() throws Exception {
            var completed = new IncidentRcaResultView(
                    "inc-001", "COMPLETED", "alertmanager",
                    "HighLatency", "order-service", "demo", "critical",
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
                    .andExpect(jsonPath("$.alertName").value("HighLatency"));
        }

        @Test
        @DisplayName("returns 400 when alert not eligible for RCA (FAILED)")
        void triggerIneligible() throws Exception {
            var failed = IncidentRcaResultView.failed("inc-alert-1", "Watchdog",
                    "prometheus", "该告警不可触发 RCA：Watchdog 是告警链路自检告警");
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(failed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fingerprint\":\"fp-watchdog\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").isNotEmpty());
        }

        @Test
        @DisplayName("returns 400 when alert not found (FAILED)")
        void triggerNotFound() throws Exception {
            var failed = IncidentRcaResultView.failed("inc-alert-2", "NotFound", null, "未找到匹配的告警");
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(failed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fingerprint\":\"nonexistent\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorMessage").value("未找到匹配的告警"));
        }

        @Test
        @DisplayName("accepts alertName + service body")
        void triggerByNameMatch() throws Exception {
            var completed = new IncidentRcaResultView(
                    "inc-002", "COMPLETED", "alertmanager",
                    "HighErrorRate", "payment-service", "demo", "warning",
                    null, "insufficient_evidence", null,
                    0.30, 0.0, null, "/api/incidents/inc-002/report",
                    100, null
            );
            given(incidentService.triggerRcaFromAlert(any(IncidentRcaTriggerRequest.class)))
                    .willReturn(completed);

            mockMvc.perform(post("/api/incidents/from-alert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"alertName\":\"HighErrorRate\",\"service\":\"payment-service\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alertName").value("HighErrorRate"));
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
        @DisplayName("returns 200 with report")
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
                    "incidentId", "inc-005"
            );
            given(incidentService.getIncidentRca("inc-005")).willReturn(Optional.of(rcaData));

            mockMvc.perform(get("/api/incidents/inc-005/rca"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scenarioId").value("inc-005"));
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
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("returns empty array when no incidents")
        void empty() throws Exception {
            given(incidentService.listIncidents()).willReturn(List.of());
            mockMvc.perform(get("/api/incidents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }
}
