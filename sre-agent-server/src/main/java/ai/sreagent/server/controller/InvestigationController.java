package ai.sreagent.server.controller;

import ai.sreagent.core.domain.EventTraceEntry;
import ai.sreagent.llm.model.LlmEnhancedReport;
import ai.sreagent.server.service.InvestigationResponse;
import ai.sreagent.server.service.InvestigationService;
import ai.sreagent.server.service.LlmSynthesisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for investigation API.
 * Delegates all logic to InvestigationService and LlmSynthesisService.
 */
@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {

    private final InvestigationService service;
    private final LlmSynthesisService llmService;

    public InvestigationController(InvestigationService service, LlmSynthesisService llmService) {
        this.service = service;
        this.llmService = llmService;
    }

    @PostMapping("/scenario-e")
    public ResponseEntity<InvestigationResponse> runScenarioE() throws Exception {
        InvestigationResponse response = service.runScenarioE();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<InvestigationResponse> getSummary(@PathVariable String incidentId) {
        return service.getSummary(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{incidentId}/report", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getReport(@PathVariable String incidentId) {
        return service.getReport(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{incidentId}/trace")
    public ResponseEntity<List<EventTraceEntry>> getTrace(@PathVariable String incidentId) {
        return service.getTrace(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Step G: LLM-assisted RCA synthesis endpoints ---

    @PostMapping("/scenario-e/llm-summary")
    public ResponseEntity<LlmEnhancedReport> getScenarioELlmSummary() throws Exception {
        LlmEnhancedReport report = llmService.synthesizeScenarioE(service);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/{incidentId}/llm-summary")
    public ResponseEntity<LlmEnhancedReport> getLlmSummary(@PathVariable String incidentId) {
        return llmService.synthesize(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
