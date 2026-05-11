
package ai.sreagent.server.controller;

import ai.sreagent.server.live.LiveScenarioResult;
import ai.sreagent.server.live.LiveScenarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for live Scenario G RCA (Step V).
 * Provides endpoints to run, inspect, and reset live scenarios.
 */
@RestController
@RequestMapping("/api/live-scenario")
public class LiveScenarioController {

    private final LiveScenarioService scenarioService;

    public LiveScenarioController(LiveScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    /**
     * Run Scenario G: Payment Latency → Order Error Spike.
     *
     * POST /api/live-scenario/run
     * Body: { "mode": "live"|"simulation", "faultMode": "latency"|"error"|"timeout",
     *         "faultParams": {...}, "waitSeconds": 30, "runLlmProposal": true }
     */
    @PostMapping("/run")
    public ResponseEntity<LiveScenarioResult> runScenario(@RequestBody(required = false) Map<String, Object> body) {
        String mode = body != null ? (String) body.getOrDefault("mode", "simulation") : "simulation";
        String faultMode = body != null ? (String) body.getOrDefault("faultMode", "latency") : "latency";
        Map<String, Object> faultParams = body != null ? (Map<String, Object>) body.get("faultParams") : null;
        int waitSeconds = body != null && body.containsKey("waitSeconds")
                ? ((Number) body.get("waitSeconds")).intValue() : 0;
        boolean runLlm = body != null && Boolean.TRUE.equals(body.get("runLlmProposal"));

        LiveScenarioResult result = scenarioService.runScenarioG(
                mode, faultMode, faultParams, waitSeconds, runLlm);
        return ResponseEntity.ok(result);
    }

    /**
     * Quick simulation run — fixture-only, no fault injection, no wait.
     * GET /api/live-scenario/simulate
     */
    @GetMapping("/simulate")
    public ResponseEntity<LiveScenarioResult> simulate(
            @RequestParam(defaultValue = "false") boolean runLlm) {
        LiveScenarioResult result = scenarioService.runScenarioG(
                "simulation", "latency", null, 0, runLlm);
        return ResponseEntity.ok(result);
    }

    /**
     * Get the latest scenario result.
     * GET /api/live-scenario/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<LiveScenarioResult> getLatest() {
        return scenarioService.getLatest()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a specific scenario result by ID.
     * GET /api/live-scenario/{scenarioId}
     */
    @GetMapping("/{scenarioId}")
    public ResponseEntity<LiveScenarioResult> getResult(@PathVariable String scenarioId) {
        return scenarioService.getResult(scenarioId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a specific scenario result by ID.
     * GET /api/live-scenario/detail/{scenarioId}
     */
    @GetMapping("/detail/{scenarioId}")
    public ResponseEntity<LiveScenarioResult> getResultDetail(@PathVariable String scenarioId) {
        return getResult(scenarioId);
    }

    /**
     * List all scenario results.
     * GET /api/live-scenario
     */
    @GetMapping
    public ResponseEntity<List<LiveScenarioResult>> listAll() {
        return ResponseEntity.ok(scenarioService.listAll());
    }

    /**
     * Reset all demo service faults to normal.
     * POST /api/live-scenario/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetFaults() {
        scenarioService.resetFaults();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "All faults reset to normal"));
    }
}
