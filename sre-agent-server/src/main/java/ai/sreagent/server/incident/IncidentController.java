package ai.sreagent.server.incident;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for alert-driven incident intake.
 *
 * Production-like path:
 *   GET  /api/incidents/alerts         → list current firing alerts
 *   POST /api/incidents/from-alert     → trigger RCA from a specific alert
 *   GET  /api/incidents/{incidentId}   → get incident + RCA result
 *   GET  /api/incidents/{incidentId}/report → get RCA markdown report
 *   GET  /api/incidents                → list all incidents
 *
 * Lab/Demo path (unchanged):
 *   POST /api/live-scenario/run        → LiveScenarioService
 *   POST /api/live-scenario/simulate   → LiveScenarioService
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * List current firing alerts from Alertmanager.
     * Returns compact alert views for the UI incident intake panel.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertView>> listFiringAlerts() {
        List<AlertView> alerts = incidentService.fetchFiringAlerts();
        return ResponseEntity.ok(alerts);
    }

    /**
     * Trigger RCA from a specific alert.
     * Request body identifies the alert by fingerprint or alertName+service.
     *
     * Response: incidentId + RCA result (running initially, then completed).
     */
    @PostMapping("/from-alert")
    public ResponseEntity<IncidentRcaResultView> triggerRcaFromAlert(
            @RequestBody IncidentRcaTriggerRequest request) {
        IncidentRcaResultView result = incidentService.triggerRcaFromAlert(request);
        if ("FAILED".equals(result.status())) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Get incident + RCA result by incident ID.
     */
    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentRcaResultView> getIncident(@PathVariable String incidentId) {
        return incidentService.getIncident(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get RCA markdown report for an incident.
     */
    @GetMapping("/{incidentId}/report")
    public ResponseEntity<Map<String, String>> getReport(@PathVariable String incidentId) {
        return incidentService.getReport(incidentId)
                .map(report -> ResponseEntity.ok(Map.of("report", report)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the full RCA data for an incident (LiveScenarioResult-compatible).
     * Frontend uses mapLiveScenarioToRcaView to render this.
     */
    @GetMapping("/{incidentId}/rca")
    public ResponseEntity<Map<String, Object>> getIncidentRca(@PathVariable String incidentId) {
        return incidentService.getIncidentRca(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all incidents (alert-driven RCA results).
     */
    @GetMapping
    public ResponseEntity<List<IncidentRcaResultView>> listIncidents() {
        return ResponseEntity.ok(incidentService.listIncidents());
    }
}
