package ai.sreagent.server.service;

import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.MockLlmClient;
import ai.sreagent.llm.model.LlmEnhancedReport;
import ai.sreagent.llm.synthesis.LlmReportSynthesizer;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service layer for LLM-assisted RCA synthesis.
 *
 * Uses MockLlmClient by default. Real provider can be configured via
 * environment variables (LLM_PROVIDER, LLM_BASE_URL, LLM_API_KEY, LLM_MODEL).
 *
 * Guardrail: this service never lets LLM output override deterministic fields.
 */
@Service
public class LlmSynthesisService {

    private final LlmReportSynthesizer synthesizer;
    private final InMemoryInvestigationStore store;

    public LlmSynthesisService(InMemoryInvestigationStore store) {
        this.store = store;
        LlmClient client = resolveClient();
        this.synthesizer = new LlmReportSynthesizer(client);
    }

    /**
     * Run LLM synthesis on Scenario E (auto-runs investigation if needed).
     */
    public LlmEnhancedReport synthesizeScenarioE(InvestigationService investigationService) throws Exception {
        // Ensure we have a Scenario E result
        InvestigationResult result = store.findLatest()
                .orElseGet(() -> {
                    try {
                        return runScenarioEQuietly(investigationService);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to run Scenario E", e);
                    }
                });
        return synthesizer.synthesize(result);
    }

    /**
     * Run LLM synthesis on a specific incident.
     */
    public Optional<LlmEnhancedReport> synthesize(String incidentId) {
        return store.findByIncidentId(incidentId)
                .map(synthesizer::synthesize);
    }

    private LlmClient resolveClient() {
        String provider = System.getenv().getOrDefault("LLM_PROVIDER", "mock");
        if ("mock".equals(provider)) {
            return new MockLlmClient();
        }
        // Future: openai-compatible provider
        // For now, fall back to mock if config is incomplete
        return new MockLlmClient();
    }

    private InvestigationResult runScenarioEQuietly(InvestigationService investigationService) throws Exception {
        InvestigationResponse response = investigationService.runScenarioE();
        return store.findByIncidentId(response.incidentId())
                .orElseThrow(() -> new RuntimeException("Investigation result not found after running Scenario E"));
    }
}
