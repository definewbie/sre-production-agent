package ai.sreagent.server.controller;

import ai.sreagent.probe.*;
import ai.sreagent.server.service.ProbeExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for probe execution API (Step S).
 * Provides minimal endpoints for propose-and-execute-probes in fixture mode.
 */
@RestController
@RequestMapping("/api/investigations")
public class ProbeController {

    private final ProbeExecutionService probeService;

    public ProbeController(ProbeExecutionService probeService) {
        this.probeService = probeService;
    }

    /**
     * Scenario E convenience endpoint: propose hypotheses and execute probes.
     * Uses fixture mode only — no live backend.
     */
    @PostMapping("/scenario-e/propose-and-execute-probes")
    public ResponseEntity<ProbeExecutionResult> proposeAndExecuteProbes() throws Exception {
        ProbeExecutionResult result = probeService.proposeAndExecuteScenarioE();
        return ResponseEntity.ok(result);
    }
}
