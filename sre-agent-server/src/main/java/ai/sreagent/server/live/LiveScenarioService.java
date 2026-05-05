package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.k8s.KubernetesClientConfig;
import ai.sreagent.k8s.KubernetesResourceReader;
import ai.sreagent.llm.proposer.LlmHypothesisProposalResult;
import ai.sreagent.llm.proposer.LlmHypothesisProposer;
import ai.sreagent.llm.proposer.MockLlmHypothesisProposer;
import ai.sreagent.server.demo.DemoServiceClient;
import ai.sreagent.server.demo.DemoServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the complete live Scenario G RCA flow:
 * 1. Inject fault on payment-service (latency)
 * 2. Collect live evidence from Prometheus / Loki / Jaeger / Kubernetes
 * 3. Build IncidentTask + evidence
 * 4. Run deterministic RCA workflow
 * 5. Optionally run LLM hypothesis proposal (advisory only)
 * 6. Reset fault
 *
 * Key constraint: baseDecision is immutable.
 * LLM/probe layers are advisory only and never mutate the RCA conclusion.
 *
 * Mode behavior:
 * - "simulation" mode: uses fixture evidence, fallback allowed.
 * - "live" mode: HTTP endpoints + Kubernetes API only, no fixture fallback.
 *   If evidence collection fails, RCA runs with whatever was collected.
 *   Source failures are reported in the evidence report.
 */
@Service
// TODO：现在的scenarios都是hardcode在代码里面的，应该做进一步的抽象，类似规则引擎的fact和rules，这样才能cover更加通用的场景
public class LiveScenarioService {

    private static final Logger log = LoggerFactory.getLogger(LiveScenarioService.class);

    private final DemoServiceClient demoClient;
    private final DemoServiceConfig demoConfig;
    private final String prometheusUrl;
    private final String lokiUrl;
    private final String jaegerUrl;
    private final KubernetesResourceReader kubernetesReader;

    // Store results in memory
    private final ConcurrentHashMap<String, LiveScenarioResult> resultStore = new ConcurrentHashMap<>();

    public LiveScenarioService(DemoServiceClient demoClient, DemoServiceConfig demoConfig,
                                org.springframework.core.env.Environment env) {
        this.demoClient = demoClient;
        this.demoConfig = demoConfig;
        this.prometheusUrl = env.getProperty("sre-agent.observability.prometheus-url", "http://localhost:9090");
        this.lokiUrl = env.getProperty("sre-agent.observability.loki-url", "http://localhost:3100");
        this.jaegerUrl = env.getProperty("sre-agent.observability.trace-url", "http://localhost:16686");
        // Kubernetes reader is created lazily by LiveEvidenceCollector based on mode
        this.kubernetesReader = null;
    }

    /**
     * Run Scenario G: payment latency → order timeout/error spike.
     *
     * @param mode          "live" to inject real faults, "simulation" to use fixtures only
     * @param faultMode     fault type: "latency", "error", "timeout", or "normal"
     * @param faultParams   fault parameters (latencyMs, errorRate, etc.)
     * @param waitSeconds   seconds to wait after fault injection before collecting evidence
     * @param runLlmProposal whether to run LLM hypothesis proposal
     * @return live scenario result
     */
    public LiveScenarioResult runScenarioG(String mode, String faultMode,
                                            Map<String, Object> faultParams,
                                            int waitSeconds, boolean runLlmProposal) {
        String scenarioId = "scenario-g-" + System.currentTimeMillis();
        long startTime = System.currentTimeMillis();

        boolean isSimulation = "simulation".equals(mode);

        log.info("Starting Scenario G: mode={}, faultMode={}, wait={}s, llm={}, isSimulation={}",
                mode, faultMode, waitSeconds, runLlmProposal, isSimulation);

        try {
            // Phase 1: Inject fault (live mode only)
            if (!isSimulation) {
                injectFault(faultMode, faultParams);

                // Generate traffic so fault produces observable metrics
                int trafficCount = faultParams != null && faultParams.containsKey("trafficCount")
                        ? ((Number) faultParams.get("trafficCount")).intValue() : 5;
                log.info("Generating {} checkout requests to produce observable metrics...", trafficCount);
                int successes = demoClient.generateTraffic(trafficCount);
                log.info("Traffic generation complete: {}/{} requests succeeded (failures expected under fault)", successes, trafficCount);

                // Wait for metrics propagation (Prometheus scrape interval ~15s)
                int effectiveWait = Math.max(waitSeconds, 15);
                if (effectiveWait > 0) {
                    log.info("Waiting {}s for metrics propagation...", effectiveWait);
                    Thread.sleep(effectiveWait * 1000L);
                }
            }

            // Phase 2: Collect evidence from all 4 sources
            // forceFixture=true for simulation mode; false for live mode
            LiveEvidenceCollector collector = new LiveEvidenceCollector(
                    prometheusUrl, lokiUrl, jaegerUrl, isSimulation, kubernetesReader);

            // Collect for the alerting service (order-service) and the suspected downstream (payment-service)
            LiveEvidenceReport orderReport = collector.collect("order-service", "demo", Duration.ofMinutes(15));
            LiveEvidenceReport paymentReport = collector.collect("payment-service", "demo", Duration.ofMinutes(15));

            // Merge evidence
            List<Evidence> allEvidence = new ArrayList<>();
            allEvidence.addAll(orderReport.allEvidence());
            allEvidence.addAll(paymentReport.allEvidence());

            // Build merged report — merge source reports by combining counts
            Map<String, LiveEvidenceReport.SourceReport> mergedSources = new LinkedHashMap<>();
            mergeSourceReports(mergedSources, orderReport.sources());
            mergeSourceReports(mergedSources, paymentReport.sources());
            List<String> allWarnings = new ArrayList<>();
            allWarnings.addAll(orderReport.warnings());
            allWarnings.addAll(paymentReport.warnings());

            // Fallback evidence: ONLY for simulation mode
            if (allEvidence.isEmpty()) {
                if (isSimulation) {
                    log.info("No evidence collected in simulation mode — using fallback fixture evidence");
                    allEvidence = buildFallbackEvidence();
                } else {
                    log.warn("No evidence collected in live mode — RCA will run with zero evidence (no fixture fallback)");
                    allWarnings.add("No evidence collected from any live source. RCA results may be inconclusive.");
                }
            }

            // Build final mergedReport AFTER potential fallback evidence addition
            LiveEvidenceReport mergedReport = new LiveEvidenceReport(
                    allEvidence.size(), List.copyOf(allEvidence), Map.copyOf(mergedSources), List.copyOf(allWarnings));

            // Phase 3: Build IncidentTask
            IncidentTask incident = buildScenarioGIncident(faultMode);

            // Phase 4: Run deterministic RCA
            InvestigationWorkflow workflow = new InvestigationWorkflow();
            InvestigationResult rcaResult = workflow.runFromMemory(incident, allEvidence);
            log.info("RCA completed: incidentId={}, decision={}, confidence={}",
                    rcaResult.incidentId(),
                    rcaResult.decision().decisionType(),
                    rcaResult.decision().confidenceScore());

            // Phase 5: LLM hypothesis proposal (advisory only)
            LlmHypothesisProposalResult llmProposal = null;
            if (runLlmProposal) {
                llmProposal = runLlmProposal(rcaResult, allEvidence);
            }

            // Phase 6: Reset fault (live mode only)
            if (!isSimulation) {
                try {
                    demoClient.setAllFaultConfig(Map.of("mode", "normal"));
                    log.info("Fault reset to normal");
                } catch (Exception e) {
                    log.warn("Failed to reset fault: {}", e.getMessage());
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            LiveScenarioResult result = LiveScenarioResult.completed(
                    scenarioId, "Scenario G: Payment Latency → Order Error Spike",
                    rcaResult, llmProposal, mergedReport, durationMs);

            resultStore.put(scenarioId, result);
            log.info("Scenario G completed in {}ms", durationMs);
            return result;

        } catch (Exception e) {
            log.error("Scenario G failed", e);
            // Try to reset fault
            if (!isSimulation) {
                try { demoClient.setAllFaultConfig(Map.of("mode", "normal")); } catch (Exception ignored) {}
            }
            return LiveScenarioResult.failed(scenarioId,
                    "Scenario G: Payment Latency → Order Error Spike", e.getMessage());
        }
    }

    /**
     * Get a previously run scenario result.
     */
    public Optional<LiveScenarioResult> getResult(String scenarioId) {
        return Optional.ofNullable(resultStore.get(scenarioId));
    }

    /**
     * Get the latest completed scenario result.
     */
    public Optional<LiveScenarioResult> getLatest() {
        return resultStore.values().stream()
                .filter(r -> r.status() == LiveScenarioResult.LiveScenarioStatus.COMPLETED)
                .reduce((first, second) -> second);
    }

    /**
     * List all scenario results.
     */
    public List<LiveScenarioResult> listAll() {
        return new ArrayList<>(resultStore.values());
    }

    /**
     * Reset all demo services to normal.
     */
    public void resetFaults() {
        demoClient.setAllFaultConfig(Map.of("mode", "normal"));
    }

    private void mergeSourceReports(Map<String, LiveEvidenceReport.SourceReport> target,
                                     Map<String, LiveEvidenceReport.SourceReport> source) {
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            LiveEvidenceReport.SourceReport incoming = entry.getValue();
            LiveEvidenceReport.SourceReport existing = target.get(key);
            if (existing == null) {
                target.put(key, incoming);
            } else {
                // Merge: combine evidence counts, preserve availability, merge evidence types
                List<String> mergedTypes = new ArrayList<>(existing.evidenceTypes());
                for (String t : incoming.evidenceTypes()) {
                    if (!mergedTypes.contains(t)) {
                        mergedTypes.add(t);
                    }
                }
                target.put(key, new LiveEvidenceReport.SourceReport(
                        key,
                        existing.available() || incoming.available(),
                        existing.evidenceCount() + incoming.evidenceCount(),
                        List.copyOf(mergedTypes),
                        existing.error() != null ? existing.error() : incoming.error()
                ));
            }
        }
    }

    private void injectFault(String faultMode, Map<String, Object> params) {
        Map<String, Object> faultConfig = new LinkedHashMap<>();
        faultConfig.put("mode", faultMode);

        switch (faultMode) {
            case "latency" -> {
                int latencyMs = params != null && params.containsKey("latencyMs")
                        ? ((Number) params.get("latencyMs")).intValue() : 2000;
                faultConfig.put("latencyMs", latencyMs);
                faultConfig.put("errorRate", 0.0);
                faultConfig.put("timeoutRate", 0.0);
            }
            case "error" -> {
                double errorRate = params != null && params.containsKey("errorRate")
                        ? ((Number) params.get("errorRate")).doubleValue() : 0.5;
                faultConfig.put("latencyMs", 0);
                faultConfig.put("errorRate", errorRate);
                faultConfig.put("timeoutRate", 0.0);
            }
            case "timeout" -> {
                int timeoutMs = params != null && params.containsKey("timeoutMs")
                        ? ((Number) params.get("timeoutMs")).intValue() : 5000;
                faultConfig.put("latencyMs", 0);
                faultConfig.put("errorRate", 0.0);
                faultConfig.put("timeoutRate", 1.0);
            }
            default -> {
                faultConfig.put("mode", "normal");
                faultConfig.put("latencyMs", 0);
                faultConfig.put("errorRate", 0.0);
                faultConfig.put("timeoutRate", 0.0);
            }
        }

        // Inject on payment-service
        demoClient.setNamedServiceFaultConfig("payment-service", faultConfig);
        log.info("Injected fault on payment-service: {}", faultConfig);
    }

    private IncidentTask buildScenarioGIncident(String faultMode) {
        String incidentId = "inc-scenario-g-" + System.currentTimeMillis();
        return new IncidentTask(
                incidentId,
                "PaymentLatencySpike",
                "order-service",
                "demo",
                "warning",
                Instant.now(),
                Map.of("team", "platform", "env", "demo", "scenario", "g",
                        "fault_mode", faultMode),
                Map.of("description",
                        "order-service error rate elevated, upstream payment-service exhibiting "
                                + faultMode + " behavior",
                        "runbook", "https://wiki/internal/order-service-high-error-rate")
        );
    }

    private LlmHypothesisProposalResult runLlmProposal(InvestigationResult rcaResult,
                                                         List<Evidence> evidence) {
        try {
            LlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
            List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(evidence);
            LlmHypothesisProposalResult result = proposer.propose(rcaResult, normalized);
            log.info("LLM proposal: {} hypotheses, advisoryOnly={}",
                    result.proposals().size(), result.advisoryOnly());
            return result;
        } catch (Exception e) {
            log.warn("LLM proposal failed: {}", e.getMessage());
            return null;
        }
    }

    private List<Evidence> buildFallbackEvidence() {
        // Minimal static evidence to ensure RCA can produce a result
        // ONLY used in simulation mode
        String incId = "inc-scenario-g-fallback";
        return List.of(
            new Evidence("ev-fb-1", incId, "prometheus", "metric_latency_p95_spike",
                    "payment-service", Instant.now(),
                    "Prometheus indicates p95 latency for payment-service exceeded threshold (2.5s).",
                    Map.of("queryType", "LATENCY_P95", "value", 2500.0, "threshold", 1000.0,
                            "unit", "ms"), 0.85),
            new Evidence("ev-fb-2", incId, "loki", "log_timeout_error",
                    "order-service", Instant.now(),
                    "Loki detected timeout errors in order-service logs when calling payment-service.",
                    Map.of("queryType", "TIMEOUT_ERROR", "downstream", "payment-service"), 0.80),
            new Evidence("ev-fb-3", incId, "jaeger", "trace_downstream_slow_span",
                    "payment-service", Instant.now(),
                    "Jaeger trace shows payment-service span took 2500ms (parent order-service checkout).",
                    Map.of("queryType", "DOWNSTREAM_SLOW_SPAN", "duration_ms", 2500), 0.75),
            new Evidence("ev-fb-4", incId, "prometheus", "metric_error_rate_spike",
                    "order-service", Instant.now(),
                    "Prometheus indicates error rate for order-service exceeded threshold (8.2%).",
                    Map.of("queryType", "ERROR_RATE", "value", 0.082, "threshold", 0.05), 0.70),
            new Evidence("ev-fb-5", incId, "kubernetes", "deployment_metadata",
                    "order-service", Instant.now(),
                    "Kubernetes: deployment order-service replicas=3/3 available (healthy).",
                    Map.of("deployment_name", "order-service", "replicas", 3, "ready_replicas", 3), 0.40),
            new Evidence("ev-fb-6", incId, "kubernetes", "pod_healthy",
                    "payment-service", Instant.now(),
                    "Kubernetes: all payment-service pods Running, restartCount=0 (healthy).",
                    Map.of("pod_name", "payment-service-abc", "phase", "Running", "restart_count", 0), 0.30)
        );
    }
}
