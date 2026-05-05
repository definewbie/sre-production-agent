package ai.sreagent.server.service;

import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.MockLlmClient;
import ai.sreagent.llm.client.OpenAiCompatibleLlmClient;
import ai.sreagent.llm.model.LlmEnhancedReport;
import ai.sreagent.llm.synthesis.LlmReportSynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LlmSynthesisService.class);

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
            log.info("LLM provider: mock (deterministic fallback)");
            return new MockLlmClient();
        }
        String baseUrl = System.getenv("LLM_BASE_URL");
        String apiKey = System.getenv("LLM_API_KEY");
        String model = System.getenv().getOrDefault("LLM_MODEL", "gpt-4o");
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("LLM_PROVIDER={} but LLM_BASE_URL or LLM_API_KEY not set, falling back to mock", provider);
            return new MockLlmClient();
        }
        log.info("LLM provider: {} (model={}, baseUrl={})", provider, model, baseUrl);
        return new OpenAiCompatibleLlmClient(baseUrl, apiKey, model);
    }

    private InvestigationResult runScenarioEQuietly(InvestigationService investigationService) throws Exception {
        InvestigationResponse response = investigationService.runScenarioE();
        return store.findByIncidentId(response.incidentId())
                .orElseThrow(() -> new RuntimeException("Investigation result not found after running Scenario E"));
    }
}
