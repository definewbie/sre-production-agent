package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.k8s.KubernetesClientConfig;
import ai.sreagent.k8s.KubernetesResourceReader;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.LlmRequest;
import ai.sreagent.llm.client.MockLlmClient;
import ai.sreagent.llm.client.OpenAiCompatibleLlmClient;
import ai.sreagent.llm.prompt.LlmPromptBuilder;
import ai.sreagent.llm.proposer.LlmHypothesisProposer;
import ai.sreagent.llm.proposer.LlmHypothesisProposalResult;
import ai.sreagent.llm.proposer.LlmHypothesisProposerImpl;
import ai.sreagent.llm.proposer.MockLlmHypothesisProposer;
import ai.sreagent.server.demo.DemoServiceClient;
import ai.sreagent.server.demo.DemoServiceConfig;
import ai.sreagent.server.demo.DemoServicesStatusResponse;
import ai.sreagent.server.demo.DemoServiceStatus;
import ai.sreagent.server.topology.TopologyProvider;
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
    private final TopologyProvider topologyProvider;
    private final KubernetesResourceReader kubernetesReader;

    // Store results in memory
    private final ConcurrentHashMap<String, LiveScenarioResult> resultStore = new ConcurrentHashMap<>();

    public LiveScenarioService(DemoServiceClient demoClient, DemoServiceConfig demoConfig,
                                org.springframework.core.env.Environment env,
                                TopologyProvider topologyProvider) {
        this.demoClient = demoClient;
        this.demoConfig = demoConfig;
        this.topologyProvider = topologyProvider;
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
            // Compute effective wait time (used in both live and simulation modes)
            int effectiveWait = Math.max(waitSeconds, 15);

            // Pre-flight check (live mode only): verify demo services are reachable
            if (!isSimulation) {
                DemoServicesStatusResponse preflight = demoClient.checkAllServices();
                long reachableCount = preflight.services().stream()
                        .filter(DemoServiceStatus::reachable).count();
                if (reachableCount == 0) {
                    log.warn("Pre-flight check failed: no demo services reachable. " +
                            "Aborting live investigation to avoid RCA with zero evidence.");
                    LiveScenarioResult failed = LiveScenarioResult.failed(scenarioId,
                            "Scenario G: Payment Latency → Order Error Spike",
                            "Demo services 不可达（order/payment/inventory 均无响应）。" +
                            "请先部署 demo services 到 K8s（scripts/demo-services/deploy-demo-services.sh），" +
                            "然后再发起实时排查。如无 K8s 环境，可使用 simulation 模式。");
                    resultStore.put(scenarioId, failed);
                    return failed;
                }
                if (reachableCount < preflight.services().size()) {
                    log.warn("Pre-flight check: {}/{} demo services reachable. " +
                            "RCA may be incomplete.",
                            reachableCount, preflight.services().size());
                }
            }

            // Phase 1: Inject fault (live mode only)
            Instant faultInjectedAt = Instant.now();  // anchor for evidence time window
            if (!isSimulation) {
                injectFault(faultMode, faultParams);

                // Generate traffic so fault produces observable metrics
                int trafficCount = faultParams != null && faultParams.containsKey("trafficCount")
                        ? ((Number) faultParams.get("trafficCount")).intValue() : 5;
                log.info("Generating {} checkout requests to produce observable metrics...", trafficCount);
                int successes = demoClient.generateTraffic(trafficCount);
                log.info("Traffic generation complete: {}/{} requests succeeded (failures expected under fault)", successes, trafficCount);

                // Wait for metrics propagation (Prometheus scrape interval ~15s)
                if (effectiveWait > 0) {
                    log.info("Waiting {}s for metrics propagation...", effectiveWait);
                    Thread.sleep(effectiveWait * 1000L);
                }
            }

            // Phase 2: Collect evidence from all 4 sources
            // forceFixture=true for simulation mode; false for live mode
            LiveEvidenceCollector collector = new LiveEvidenceCollector(
                    prometheusUrl, lokiUrl, jaegerUrl, isSimulation, kubernetesReader);
            collector.setTopology(topologyProvider.getTopology());

            // Collect for the alerting service (order-service) and the suspected downstream (payment-service)
            // Anchor queries at fault injection time so the window covers the active fault period
            Duration lookback = Duration.ofMinutes(15);
            LiveEvidenceReport orderReport = collector.collect(
                    "order-service", "demo", lookback, faultInjectedAt);
            LiveEvidenceReport paymentReport = collector.collect(
                    "payment-service", "demo", lookback, faultInjectedAt);

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
                    allEvidence = buildFallbackEvidence(faultMode);
                } else {
                    log.warn("No evidence collected in live mode — RCA will run with zero evidence (no fixture fallback)");
                    allWarnings.add("No evidence collected from any live source. RCA results may be inconclusive.");
                }
            }

            // Phase 3: Build IncidentTask
            IncidentTask incident = buildScenarioGIncident(faultMode);

            allEvidence.add(chaosFaultEvidence(incident.id(), faultMode, faultParams, faultInjectedAt));

            // Build final mergedReport AFTER potential fallback/control-plane evidence addition
            LiveEvidenceReport mergedReport = new LiveEvidenceReport(
                    allEvidence.size(), List.copyOf(allEvidence), Map.copyOf(mergedSources), List.copyOf(allWarnings));

            // Phase 4: Run deterministic RCA
            InvestigationWorkflow workflow = new InvestigationWorkflow();
            InvestigationResult rcaResult = workflow.runFromMemory(
                    incident, allEvidence, topologyProvider.getTopology());
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

            // Compute evidence time window
            int lookbackSec = 300;  // 5 minutes default
            int stepSec = 15;
            Instant windowEnd = Instant.now();
            Instant windowStart = windowEnd.minusSeconds(lookbackSec);

            // Generate Scenario G dynamic Chinese report
            String scenarioReport = buildScenarioGReport(
                    faultMode, faultParams, isSimulation,
                    rcaResult, mergedReport, llmProposal,
                    effectiveWait, lookbackSec, stepSec,
                    windowStart, windowEnd, durationMs,
                    runLlmProposal ? runLlmReportSynthesis(rcaResult) : null);

            LiveScenarioResult result = LiveScenarioResult.completed(
                    scenarioId, "Scenario G: Payment Latency → Order Error Spike",
                    rcaResult, llmProposal, mergedReport, durationMs,
                    effectiveWait, lookbackSec, stepSec,
                    windowStart.toString(), windowEnd.toString(),
                    scenarioReport);

            resultStore.put(scenarioId, result);
            log.info("Scenario G completed in {}ms", durationMs);
            return result;

        } catch (Exception e) {
            log.error("Scenario G failed", e);
            // Try to reset fault
            if (!isSimulation) {
                try { demoClient.setAllFaultConfig(Map.of("mode", "normal")); } catch (Exception ignored) {}
            }
            LiveScenarioResult failed = LiveScenarioResult.failed(scenarioId,
                    "Scenario G: Payment Latency → Order Error Spike", e.getMessage());
            resultStore.put(scenarioId, failed);
            return failed;
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

    private Evidence chaosFaultEvidence(String incidentId, String faultMode,
                                        Map<String, Object> faultParams, Instant timestamp) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("faultType", faultMode != null && !faultMode.isBlank() ? faultMode : "unknown");
        attrs.put("faultTargetService", "payment-service");
        attrs.put("impactedService", "order-service");
        attrs.put("scenario", "g");
        if (faultParams != null) {
            attrs.putAll(faultParams);
        }
        return new Evidence(
                "ev-chaos-fault-" + incidentId,
                incidentId,
                "chaos",
                "chaos_fault_injected",
                "payment-service",
                timestamp,
                "Scenario G injected " + attrs.get("faultType") + " fault into payment-service; "
                        + "order-service is the expected upstream impact target",
                Map.copyOf(attrs),
                1.0
        );
    }

    private LlmHypothesisProposalResult runLlmProposal(InvestigationResult rcaResult,
                                                         List<Evidence> evidence) {
        try {
            LlmClient client = resolveLlmClient();
            LlmHypothesisProposer proposer = new LlmHypothesisProposerImpl(client);
            List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(evidence);
            LlmHypothesisProposalResult result = proposer.propose(rcaResult, normalized);
            log.info("LLM proposal: {} hypotheses, advisoryOnly={}, proposer={}",
                    result.proposals().size(), result.advisoryOnly(), proposer.proposerName());
            return result;
        } catch (Exception e) {
            log.warn("LLM proposal failed, falling back to mock: {}", e.getMessage());
            try {
                return new MockLlmHypothesisProposer().propose(
                        rcaResult, EvidenceNormalizer.normalizeAll(evidence));
            } catch (Exception ex) {
                log.error("Mock fallback also failed: {}", ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Resolve LLM client from environment variables.
     * Returns MockLlmClient if LLM_PROVIDER is not configured.
     */
    private LlmClient resolveLlmClient() {
        String provider = System.getenv().getOrDefault("LLM_PROVIDER", "mock");
        if ("mock".equals(provider)) {
            return new MockLlmClient();
        }
        String baseUrl = System.getenv("LLM_BASE_URL");
        String apiKey = System.getenv("LLM_API_KEY");
        String model = System.getenv().getOrDefault("LLM_MODEL", "gpt-4o");
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("LLM_PROVIDER={} but LLM_BASE_URL or LLM_API_KEY not set, falling back to mock", provider);
            return new MockLlmClient();
        }
        log.info("LLM client: provider={}, model={}, baseUrl={}", provider, model, baseUrl);
        return new OpenAiCompatibleLlmClient(baseUrl, apiKey, model);
    }

    /**
     * Run LLM report synthesis — calls LLM to generate a Chinese narrative report
     * based on the deterministic investigation result.
     */
    private String runLlmReportSynthesis(InvestigationResult rcaResult) {
        try {
            LlmClient client = resolveLlmClient();
            LlmPromptBuilder promptBuilder = new LlmPromptBuilder();
            LlmRequest request = promptBuilder.build(rcaResult);
            var response = client.complete(request);
            log.info("LLM report synthesis complete: {} chars", response.content().length());
            return response.content();
        } catch (Exception e) {
            log.warn("LLM report synthesis failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build Scenario G dynamic Chinese report.
     * This is the Scenario-G-specific narrative report, replacing the generic
     * MarkdownReporter template with context-aware Chinese explanations.
     */
    private String buildScenarioGReport(
            String faultMode, Map<String, Object> faultParams, boolean isSimulation,
            InvestigationResult rca, LiveEvidenceReport evidenceReport,
            LlmHypothesisProposalResult llmProposal,
            int waitSeconds, int lookbackSeconds, int stepSeconds,
            Instant windowStart, Instant windowEnd, long durationMs,
            String llmSynthesizedReport) {

        StringBuilder sb = new StringBuilder();

        // Title
        sb.append("# Scenario G 动态调查报告\n\n");

        // Fault injection config
        sb.append("## 故障注入配置\n\n");
        sb.append("| 参数 | 值 |\n");
        sb.append("|---|---|\n");
        sb.append("| 模式 | ").append(isSimulation ? "模拟（仿真）" : "真实注入").append(" |\n");
        sb.append("| 故障类型 | ").append(faultMode).append(" |\n");
        if (faultParams != null) {
            if (faultParams.containsKey("latencyMs")) sb.append("| 注入延迟 | ").append(faultParams.get("latencyMs")).append("ms |\n");
            if (faultParams.containsKey("errorRate")) sb.append("| 错误率 | ").append(faultParams.get("errorRate")).append(" |\n");
            if (faultParams.containsKey("timeoutMs")) sb.append("| 超时时间 | ").append(faultParams.get("timeoutMs")).append("ms |\n");
        }
        sb.append("| 注入目标 | payment-service |\n");
        sb.append("\n");

        // Time window
        sb.append("## 证据时间窗口\n\n");
        sb.append("| 参数 | 值 |\n");
        sb.append("|---|---|\n");
        sb.append("| 等待时间 | ").append(waitSeconds).append("秒 |\n");
        sb.append("| 回溯窗口 | ").append(lookbackSeconds).append("秒 |\n");
        sb.append("| 查询步长 | ").append(stepSeconds).append("秒 |\n");
        sb.append("| 窗口起始 | ").append(windowStart).append(" |\n");
        sb.append("| 窗口结束 | ").append(windowEnd).append(" |\n");
        sb.append("\n");

        // Evidence summary
        sb.append("## 证据采集摘要\n\n");
        sb.append("共采集 **").append(evidenceReport.totalEvidenceCount()).append("** 条证据");
        sb.append("，总耗时 ").append(durationMs / 1000).append("秒。\n\n");

        if (!evidenceReport.sources().isEmpty()) {
            sb.append("| 数据源 | 可用 | 证据数 | 类型 |\n");
            sb.append("|---|---|---:|---|\n");
            for (var entry : evidenceReport.sources().entrySet()) {
                var src = entry.getValue();
                sb.append("| ").append(entry.getKey())
                  .append(" | ").append(src.available() ? "✓" : "✗")
                  .append(" | ").append(src.evidenceCount())
                  .append(" | ").append(String.join(", ", src.evidenceTypes()))
                  .append(" |\n");
            }
            sb.append("\n");
        }

        if (!evidenceReport.warnings().isEmpty()) {
            sb.append("**警告：**\n");
            for (String w : evidenceReport.warnings()) {
                sb.append("- ").append(w).append("\n");
            }
            sb.append("\n");
        }

        // Evidence assessment
        int totalEv = evidenceReport.totalEvidenceCount();
        if (totalEv < 10) {
            sb.append("> 当前时间窗口内采集到的指标信号偏少（").append(totalEv).append(" 条），");
            sb.append("建议增大 `lookbackSeconds`（当前 ").append(lookbackSeconds).append("）或 `waitSeconds`（当前 ").append(waitSeconds).append("）以获取更充分的证据。\n\n");
        }

        // Decision summary
        var decision = rca.decision();
        var comparison = rca.comparison();
        sb.append("## 决策结论\n\n");
        sb.append("- **决策类型：** ").append(decisionTypeZh(decision.decisionType())).append("\n");
        sb.append("- **选定假设：** ").append(decision.selectedHypothesisId()).append("\n");
        sb.append("- **置信度：** ").append(String.format("%.2f", decision.confidenceScore())).append("\n");
        if (comparison != null) {
            sb.append("- **分数差距：** ").append(String.format("%.2f", comparison.scoreGap())).append("\n");
            if (comparison.scoreGap() < 0.05) {
                sb.append("\n> **注意：** 排名前两位的假设分数极为接近，当前判断为近似并列。");
                sb.append("系统未强制选择唯一根因，建议通过后续探测（post-probe RCA re-run）进一步区分。\n");
            }
        }
        sb.append("\n");

        // Hypothesis breakdown
        sb.append("## 假设对比\n\n");
        if (rca.confidenceResults() != null && !rca.confidenceResults().isEmpty()) {
            sb.append("| 排名 | 假设 | 分数 | 支持证据 | 反驳证据 |\n");
            sb.append("|---:|---|---:|---:|---:|\n");
            var sorted = rca.confidenceResults().stream()
                    .sorted(java.util.Comparator.comparingDouble(
                            ai.sreagent.core.domain.ConfidenceResult::score).reversed())
                    .toList();
            int rank = 1;
            for (var cr : sorted) {
                var vr = rca.verificationResults().stream()
                        .filter(v -> v.hypothesisId().equals(cr.hypothesisId()))
                        .findFirst().orElse(null);
                int sup = vr != null ? vr.supportingEvidenceIds().size() : 0;
                int cnt = vr != null ? vr.counterEvidenceIds().size() : 0;
                sb.append("| ").append(rank++).append(" | ").append(cr.hypothesisId())
                  .append(" | ").append(String.format("%.2f", cr.score()))
                  .append(" | ").append(sup).append(" | ").append(cnt).append(" |\n");
            }
            sb.append("\n");
        }

        appendTopologySummary(sb, rca);

        // LLM Proposal (if available)
        if (llmProposal != null) {
            sb.append("## AI 补充假设（仅供参考，不影响 RCA 结论）\n\n");
            sb.append("LLM 提出了 ").append(llmProposal.proposals().size()).append(" 条补充假设：\n\n");
            for (var prop : llmProposal.proposals()) {
                sb.append("- **").append(prop.title()).append("**（置信度 ")
                  .append(String.format("%.2f", prop.priorConfidence())).append("）：")
                  .append(prop.reasoning()).append("\n");
            }
            sb.append("\n> 以上假设由 LLM 生成，仅供人工参考。确定性 RCA 决策不受 LLM 影响。\n\n");
        }

        // Next steps
        sb.append("## 建议后续操作\n\n");
        if (decision.nextProbes() != null && !decision.nextProbes().isEmpty()) {
            for (int i = 0; i < decision.nextProbes().size(); i++) {
                sb.append(i + 1).append(". ").append(decision.nextProbes().get(i)).append("\n");
            }
        } else {
            sb.append("无需额外探测。\n");
        }
        sb.append("\n");

        // LLM synthesized report (if available)
        if (llmSynthesizedReport != null && !llmSynthesizedReport.isBlank()) {
            sb.append("## LLM 增强分析报告\n\n");
            sb.append(llmSynthesizedReport);
            sb.append("\n\n");
        }

        // Footer
        sb.append("---\n");
        sb.append("*报告生成时间：").append(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(" (CST)*\n");
        sb.append("*决策方式：确定性规则引擎（LLM 仅做建议，不参与评分）*\n");

        return sb.toString();
    }

    private void appendTopologySummary(StringBuilder sb, InvestigationResult rca) {
        if (rca.confidenceResults() == null || rca.confidenceResults().isEmpty()) {
            return;
        }
        sb.append("## 拓扑传播分析\n\n");
        sb.append("Propagation Score 表示拓扑传播路径对根因解释的有界加分，")
                .append("用于区分“下游根因影响上游”和“本服务自身异常”。\n\n");
        sb.append("| 假设 | Topology Score | Propagation Score | 传播路径 | Path Source | Path Confidence |\n");
        sb.append("|---|---:|---:|---|---|---|\n");
        for (var cr : rca.confidenceResults().stream()
                .sorted(java.util.Comparator.comparingDouble(
                        ai.sreagent.core.domain.ConfidenceResult::score).reversed())
                .toList()) {
            var path = cr.propagationPath();
            if (path != null && path.isPresent()) {
                sb.append("| ").append(cr.hypothesisId())
                        .append(" | ").append(String.format("%.2f", cr.topologyCausalityScore()))
                        .append(" | ").append(String.format("%.2f", cr.propagationScore()))
                        .append(" | ").append(String.join(" → ", path.services()))
                        .append(" | ").append(path.pathSource())
                        .append(" | ").append(path.pathConfidence())
                        .append(" |\n");
            } else {
                sb.append("| ").append(cr.hypothesisId())
                        .append(" | ").append(String.format("%.2f", cr.topologyCausalityScore()))
                        .append(" | ").append(String.format("%.2f", cr.propagationScore()))
                        .append(" | - | - | - |\n");
            }
        }
        sb.append("\n");
    }

    private String decisionTypeZh(String decisionType) {
        return switch (decisionType) {
            case "likely_root_cause" -> "高置信根因";
            case "probable_root_cause" -> "可能根因";
            case "competing_hypotheses" -> "竞争假设";
            case "uncertain_requires_more_evidence" -> "不确定（需更多证据）";
            case "insufficient_evidence" -> "证据不足";
            default -> decisionType;
        };
    }

    /**
     * Build fallback evidence for simulation mode, tailored to the fault mode.
     * Generates evidence that matches the injected fault type so RCA can produce
     * accurate conclusions even in simulation mode.
     */
    private List<Evidence> buildFallbackEvidence(String faultMode) {
        String incId = "inc-scenario-g-fallback";
        Instant now = Instant.now();
        List<Evidence> evidence = new ArrayList<>();

        // Common: Kubernetes evidence (pods healthy regardless of fault type)
        evidence.add(new Evidence("ev-fb-k8s-1", incId, "kubernetes", "deployment_metadata",
                "order-service", now,
                "Kubernetes: deployment order-service replicas=3/3 available (healthy).",
                Map.of("deployment_name", "order-service", "replicas", 3, "ready_replicas", 3), 0.40));
        evidence.add(new Evidence("ev-fb-k8s-2", incId, "kubernetes", "pod_healthy",
                "payment-service", now,
                "Kubernetes: all payment-service pods Running, restartCount=0 (healthy).",
                Map.of("pod_name", "payment-service-abc", "phase", "Running", "restart_count", 0), 0.30));

        // Fault-specific evidence
        switch (faultMode) {
            case "latency" -> {
                evidence.add(new Evidence("ev-fb-prom-1", incId, "prometheus", "metric_latency_p95_spike",
                        "payment-service", now,
                        "Prometheus: payment-service p95 latency 2500ms (threshold 1000ms).",
                        Map.of("queryType", "LATENCY_P95", "value", 2500.0, "threshold", 1000.0, "unit", "ms"), 0.85));
                evidence.add(new Evidence("ev-fb-prom-2", incId, "prometheus", "metric_downstream_latency_p95_spike",
                        "order-service", now,
                        "Prometheus: order-service downstream p95 latency to payment-service 2400ms.",
                        Map.of("queryType", "DOWNSTREAM_LATENCY_P95", "value", 2400.0, "downstream", "payment-service"), 0.80));
                evidence.add(new Evidence("ev-fb-loki-1", incId, "loki", "log_timeout_error",
                        "order-service", now,
                        "Loki: order-service timeout errors calling payment-service /charge.",
                        Map.of("queryType", "TIMEOUT_ERROR", "downstream", "payment-service"), 0.75));
                evidence.add(new Evidence("ev-fb-trace-1", incId, "jaeger", "trace_downstream_slow_span",
                        "payment-service", now,
                        "Jaeger: payment-service span took 2500ms (parent order-service checkout).",
                        Map.of("queryType", "DOWNSTREAM_SLOW_SPAN", "duration_ms", 2500), 0.75));
                evidence.add(new Evidence("ev-fb-prom-3", incId, "prometheus", "metric_error_rate_spike",
                        "order-service", now,
                        "Prometheus: order-service error rate 8.2% (threshold 5%).",
                        Map.of("queryType", "ERROR_RATE", "value", 0.082, "threshold", 0.05), 0.70));
            }
            case "error" -> {
                evidence.add(new Evidence("ev-fb-prom-1", incId, "prometheus", "metric_error_rate_spike",
                        "payment-service", now,
                        "Prometheus: payment-service error rate 80% (threshold 5%).",
                        Map.of("queryType", "ERROR_RATE", "value", 0.80, "threshold", 0.05), 0.90));
                evidence.add(new Evidence("ev-fb-prom-2", incId, "prometheus", "metric_error_rate_spike",
                        "order-service", now,
                        "Prometheus: order-service error rate 45% due to upstream payment failures.",
                        Map.of("queryType", "ERROR_RATE", "value", 0.45, "threshold", 0.05), 0.85));
                evidence.add(new Evidence("ev-fb-loki-1", incId, "loki", "log_http_5xx",
                        "payment-service", now,
                        "Loki: payment-service returning HTTP 500 errors.",
                        Map.of("queryType", "HTTP_5XX_LOGS", "status_code", 500), 0.85));
                evidence.add(new Evidence("ev-fb-loki-2", incId, "loki", "log_http_5xx",
                        "order-service", now,
                        "Loki: order-service returning HTTP 502 Bad Gateway to clients.",
                        Map.of("queryType", "HTTP_5XX_LOGS", "status_code", 502, "upstream", "payment-service"), 0.80));
                evidence.add(new Evidence("ev-fb-trace-1", incId, "jaeger", "trace_error_span",
                        "payment-service", now,
                        "Jaeger: payment-service /charge spans showing HTTP 500 errors.",
                        Map.of("queryType", "ERROR_SPAN", "status_code", 500), 0.80));
            }
            case "timeout" -> {
                evidence.add(new Evidence("ev-fb-prom-1", incId, "prometheus", "metric_latency_p95_spike",
                        "payment-service", now,
                        "Prometheus: payment-service p95 latency exceeds 5000ms (timeout).",
                        Map.of("queryType", "LATENCY_P95", "value", 5000.0, "threshold", 1000.0, "unit", "ms"), 0.90));
                evidence.add(new Evidence("ev-fb-prom-2", incId, "prometheus", "metric_downstream_latency_p95_spike",
                        "order-service", now,
                        "Prometheus: order-service downstream timeout to payment-service.",
                        Map.of("queryType", "DOWNSTREAM_LATENCY_P95", "value", 5200.0, "downstream", "payment-service"), 0.85));
                evidence.add(new Evidence("ev-fb-loki-1", incId, "loki", "log_timeout_error",
                        "order-service", now,
                        "Loki: order-service upstream timeout errors — payment-service /charge not responding.",
                        Map.of("queryType", "TIMEOUT_ERROR", "downstream", "payment-service"), 0.85));
                evidence.add(new Evidence("ev-fb-loki-2", incId, "loki", "log_downstream_timeout",
                        "order-service", now,
                        "Loki: order-service downstream timeout — HttpTimeoutException calling payment-service.",
                        Map.of("queryType", "DOWNSTREAM_TIMEOUT", "exception", "HttpTimeoutException"), 0.80));
                evidence.add(new Evidence("ev-fb-trace-1", incId, "jaeger", "trace_timeout_span",
                        "payment-service", now,
                        "Jaeger: payment-service /charge spans timing out (>5000ms).",
                        Map.of("queryType", "TIMEOUT_SPAN", "duration_ms", 5000, "timed_out", true), 0.85));
                evidence.add(new Evidence("ev-fb-prom-3", incId, "prometheus", "metric_error_rate_spike",
                        "order-service", now,
                        "Prometheus: order-service error rate 60% due to payment-service timeouts.",
                        Map.of("queryType", "ERROR_RATE", "value", 0.60, "threshold", 0.05), 0.80));
            }
            default -> {
                // "normal" or unknown: minimal baseline evidence
                evidence.add(new Evidence("ev-fb-prom-1", incId, "prometheus", "metric_latency_p95_spike",
                        "payment-service", now,
                        "Prometheus: payment-service p95 latency 2500ms (threshold 1000ms).",
                        Map.of("queryType", "LATENCY_P95", "value", 2500.0, "threshold", 1000.0, "unit", "ms"), 0.85));
                evidence.add(new Evidence("ev-fb-loki-1", incId, "loki", "log_timeout_error",
                        "order-service", now,
                        "Loki: order-service timeout errors calling payment-service.",
                        Map.of("queryType", "TIMEOUT_ERROR", "downstream", "payment-service"), 0.75));
                evidence.add(new Evidence("ev-fb-trace-1", incId, "jaeger", "trace_downstream_slow_span",
                        "payment-service", now,
                        "Jaeger: payment-service span took 2500ms.",
                        Map.of("queryType", "DOWNSTREAM_SLOW_SPAN", "duration_ms", 2500), 0.75));
            }
        }

        return evidence;
    }
}
