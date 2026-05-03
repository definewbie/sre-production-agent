package ai.sreagent.server.service;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer that orchestrates investigation workflow.
 * Delegates all core logic to InvestigationWorkflow in sre-agent-core.
 */
@Service
public class InvestigationService {

    private final InMemoryInvestigationStore store;

    public InvestigationService(InMemoryInvestigationStore store) {
        this.store = store;
    }

    /**
     * Run Scenario E investigation using static demo data.
     */
    public InvestigationResponse runScenarioE() throws Exception {
        // Resolve paths relative to project root
        File alertFile = resolveProjectFile("examples/alerts/competing_hypotheses.json");
        File evidenceFile = resolveProjectFile("examples/evidence/competing_hypotheses.json");

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.run(alertFile, evidenceFile);

        store.save(result);

        return toResponse(result);
    }

    /**
     * Run Scenario F investigation — K8s CrashLoopBackOff evidence.
     */
    public InvestigationResponse runScenarioF() throws Exception {
        File alertFile = resolveProjectFile("examples/alerts/k8s_crashloop.json");
        File evidenceFile = resolveProjectFile("examples/evidence/k8s_crashloop.json");

        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.run(alertFile, evidenceFile);

        store.save(result);

        return toResponse(result);
    }

    /**
     * Build response DTO from investigation result.
     */
    private InvestigationResponse toResponse(InvestigationResult r) {
        Map<String, Double> scores = r.confidenceResults().stream()
                .collect(Collectors.toMap(ConfidenceResult::hypothesisId, ConfidenceResult::score));

        return new InvestigationResponse(
                r.incidentId(),
                r.decision().decisionType(),
                r.decision().selectedHypothesisId(),
                r.decision().confidenceScore(),
                r.comparison().scoreGap(),
                scores,
                r.comparison().competingHypothesisIds(),
                "/api/investigations/" + r.incidentId() + "/report",
                "/api/investigations/" + r.incidentId() + "/trace"
        );
    }

    public Optional<String> getReport(String incidentId) {
        return store.findByIncidentId(incidentId)
                .map(InvestigationResult::markdownReport);
    }

    public Optional<List<ai.sreagent.core.domain.EventTraceEntry>> getTrace(String incidentId) {
        return store.findByIncidentId(incidentId)
                .map(InvestigationResult::eventTrace);
    }

    public Optional<InvestigationResponse> getSummary(String incidentId) {
        return store.findByIncidentId(incidentId)
                .map(this::toResponse);
    }

    private File resolveProjectFile(String relativePath) {
        // Try current dir first, then parent (in case running from module dir)
        File f = new File(relativePath);
        if (f.exists()) return f;
        f = new File("../" + relativePath);
        if (f.exists()) return f;
        f = new File("../../" + relativePath);
        if (f.exists()) return f;
        throw new RuntimeException("Cannot find file: " + relativePath);
    }
}
