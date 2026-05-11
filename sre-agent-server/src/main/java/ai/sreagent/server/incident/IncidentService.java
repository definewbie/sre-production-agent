package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.client.AlertmanagerClient;
import ai.sreagent.alertmanager.client.AlertmanagerClientConfig;
import ai.sreagent.alertmanager.client.HttpAlertmanagerClient;
import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceMapper;
import ai.sreagent.alertmanager.mapper.AlertmanagerIncidentMapper;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.parser.AlertmanagerResponseParser;
import ai.sreagent.alertmanager.relevance.AlertRelevance;
import ai.sreagent.alertmanager.relevance.AlertRelevanceClassifier;
import ai.sreagent.alertmanager.relevance.AlertRelevanceClassifier.ClassifiedAlert;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.server.live.LiveEvidenceCollector;
import ai.sreagent.server.live.LiveEvidenceReport;
import ai.sreagent.server.topology.TopologyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for alert-driven incident intake and RCA.
 *
 * V.2-UI-6.1: Alert Relevance Filtering & RCA Eligibility Guard.
 * All alerts are classified before being exposed to UI or triggering RCA.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final AlertmanagerClient alertClient;
    private final AlertmanagerResponseParser parser;
    private final AlertmanagerIncidentMapper incidentMapper;
    private final AlertmanagerEvidenceMapper evidenceMapper;
    private final AlertRelevanceClassifier relevanceClassifier;
    private final InvestigationWorkflow workflow;
    private final String prometheusUrl;
    private final String lokiUrl;
    private final String jaegerUrl;
    private final TopologyProvider topologyProvider;

    /** In-memory store for incident records (alert-driven RCA results). */
    private final ConcurrentHashMap<String, IncidentRecord> incidentStore = new ConcurrentHashMap<>();

    public IncidentService(Environment env, TopologyProvider topologyProvider) {
        this.topologyProvider = topologyProvider;
        String alertmanagerUrl = env.getProperty(
                "sre-agent.observability.alertmanager-url", "http://localhost:9093");
        this.alertClient = new HttpAlertmanagerClient(AlertmanagerClientConfig.of(alertmanagerUrl));
        this.parser = new AlertmanagerResponseParser();
        this.incidentMapper = new AlertmanagerIncidentMapper();
        this.evidenceMapper = new AlertmanagerEvidenceMapper();
        this.relevanceClassifier = new AlertRelevanceClassifier();
        this.workflow = new InvestigationWorkflow();
        this.prometheusUrl = env.getProperty(
                "sre-agent.observability.prometheus-url", "http://localhost:9090");
        this.lokiUrl = env.getProperty(
                "sre-agent.observability.loki-url", "http://localhost:3100");
        this.jaegerUrl = env.getProperty(
                "sre-agent.observability.trace-url", "http://localhost:16686");
        log.info("IncidentService initialized: alertmanager={}, prometheus={}, loki={}, jaeger={}",
                alertmanagerUrl, prometheusUrl, lokiUrl, jaegerUrl);
    }

    // ── Alert Polling (Trigger Role) ──────────────────────────────

    /**
     * Fetch current firing alerts from Alertmanager with relevance classification.
     * Returns classified alerts with summary.
     */
    public AlertsResponse fetchClassifiedAlerts() {
        List<AlertmanagerAlert> rawAlerts = fetchParsedAlerts(true);
        List<AlertView> classified = rawAlerts.stream()
                .filter(AlertmanagerAlert::isFiring)
                .map(alert -> {
                    ClassifiedAlert ca = relevanceClassifier.classify(alert);
                    return AlertView.from(alert, ca.relevance(), ca.rcaEligible(), ca.ineligibleReason());
                })
                .toList();
        return AlertsResponse.of(classified);
    }

    /**
     * Legacy method — returns only firing alerts without classification.
     * Kept for backward compatibility.
     */
    public List<AlertView> fetchFiringAlerts() {
        return fetchClassifiedAlerts().alerts();
    }

    // ── Alert-Driven RCA ──────────────────────────────────────────

    /**
     * Trigger RCA from a specific alert identified by fingerprint or alertName+service.
     *
     * V.2-UI-6.1: Includes RCA eligibility guard.
     * Only SERVICE_ALERT (rcaEligible=true) can trigger RCA.
     *
     * Flow:
     * 1. Find target alert from Alertmanager firing alerts
     * 2. Check RCA eligibility (relevance classification)
     * 3. Map to IncidentTask (Trigger Role)
     * 4. Collect evidence from all live sources
     * 5. Collect Alertmanager evidence (Evidence Source Role)
     * 6. Merge all evidence
     * 7. Run InvestigationWorkflow.runFromMemory()
     */
    public IncidentRcaResultView triggerRcaFromAlert(IncidentRcaTriggerRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Find target alert
        List<AlertmanagerAlert> firingAlerts = fetchParsedAlerts(true);
        Optional<AlertmanagerAlert> target = findTargetAlert(firingAlerts, request);

        if (target.isEmpty()) {
            String id = "inc-alert-" + System.currentTimeMillis();
            log.warn("No matching alert found for trigger request: {}", request);
            return IncidentRcaResultView.failed(id, request.alertName(),
                    request.service(), "未找到匹配的告警");
        }

        AlertmanagerAlert alert = target.get();

        // 2. RCA Eligibility Guard
        ClassifiedAlert classification = relevanceClassifier.classify(alert);
        if (!classification.rcaEligible()) {
            String id = "inc-alert-" + System.currentTimeMillis();
            log.warn("RCA eligibility blocked: alertName={}, relevance={}, reason={}",
                    alert.alertName(), classification.relevance(), classification.ineligibleReason());
            return IncidentRcaResultView.failed(id, alert.alertName(),
                    alert.service(),
                    "该告警不可触发 RCA：" + classification.ineligibleReason());
        }

        log.info("Triggering alert-driven RCA: alertName={}, service={}, fingerprint={}, relevance={}",
                alert.alertName(), alert.service(), alert.fingerprint(), classification.relevance());

        // 3. Map to IncidentTask
        IncidentTask incidentTask = incidentMapper.map(alert);
        String incidentId = incidentTask.id();

        // Store running state
        IncidentRcaResultView runningView = IncidentRcaResultView.running(
                incidentId, incidentTask.alertName(), incidentTask.service(), incidentTask.severity());
        incidentStore.put(incidentId, new IncidentRecord(incidentTask, alert, runningView, null, null));

        try {
            // 4. Collect evidence from all live sources
            String service = incidentTask.service();
            String namespace = incidentTask.namespace();
            Duration lookback = Duration.ofMinutes(5);

            LiveEvidenceCollector liveCollector = new LiveEvidenceCollector(
                    prometheusUrl, lokiUrl, jaegerUrl, false);
            liveCollector.setTopology(topologyProvider.getTopology());
            LiveEvidenceReport liveReport = liveCollector.collect(service, namespace, lookback,
                    alert.startsAt());

            List<Evidence> allEvidence = new ArrayList<>(liveReport.allEvidence());

            // 5. Collect Alertmanager evidence (Evidence Source Role)
            List<Evidence> alertEvidence = evidenceMapper.map(
                    List.of(alert), incidentId, service, namespace);
            allEvidence.addAll(alertEvidence);

            log.info("Evidence collected: {} from live sources, {} from alertmanager, total={}",
                    liveReport.totalEvidenceCount(), alertEvidence.size(), allEvidence.size());

            // Bug #4: Liveness probe failure reclassification
            addLivenessProbeFailureEvidence(allEvidence, null, incidentId, service, namespace);

            // 6. Run RCA
            InvestigationResult rcaResult = workflow.runFromMemory(
                    incidentTask, allEvidence, topologyProvider.getTopology());

            long durationMs = System.currentTimeMillis() - startTime;

            // 7. Build result view
            IncidentRcaResultView resultView = IncidentRcaResultView.completed(rcaResult, durationMs);
            incidentStore.put(incidentId, new IncidentRecord(incidentTask, alert, resultView, rcaResult, liveReport));

            log.info("Alert-driven RCA completed: incidentId={}, decision={}, confidence={}, duration={}ms",
                    incidentId, rcaResult.decision().decisionType(),
                    String.format("%.2f", rcaResult.decision().confidenceScore()), durationMs);

            return resultView;

        } catch (Exception e) {
            log.error("Alert-driven RCA failed: incidentId={}", incidentId, e);
            IncidentRcaResultView failedView = IncidentRcaResultView.failed(
                    incidentId, incidentTask.alertName(), incidentTask.service(), e.getMessage());
            incidentStore.put(incidentId, new IncidentRecord(incidentTask, alert, failedView, null, null));
            return failedView;
        }
    }

    // ── Direct RCA (Chaos / Lab Demo) ─────────────────────────────

    /**
     * Trigger RCA directly from a chaos experiment (no Alertmanager dependency).
     * Creates a synthetic incident and runs the full evidence collection + workflow.
     */
    public IncidentRcaResultView triggerRcaDirect(
            String incidentId,
            String targetService,
            String faultType,
            String experimentName,
            String namespace,
            String severity
    ) {
        long startTime = System.currentTimeMillis();
        String alertName = "混沌实验: " + targetService + " " + faultType;

        IncidentTask incidentTask = new IncidentTask(
                incidentId,
                alertName,
                targetService,
                namespace != null ? namespace : "demo",
                severity != null ? severity : "warning",
                Instant.now(),
                Map.of("faultType", faultType, "source", "chaos", "experimentName",
                        experimentName != null ? experimentName : alertName),
                Map.of("description", "混沌实验注入故障: " + faultType + " on " + targetService)
        );

        // Store running state
        IncidentRcaResultView runningView = IncidentRcaResultView.running(
                incidentId, incidentTask.alertName(), incidentTask.service(), incidentTask.severity());
        incidentStore.put(incidentId, new IncidentRecord(incidentTask, null, runningView, null, null));

        log.info("Triggering direct RCA from chaos: incidentId={}, service={}, faultType={}",
                incidentId, targetService, faultType);

        try {
            // Collect evidence from all live sources
            String service = incidentTask.service();
            String ns = incidentTask.namespace();
            Duration lookback = Duration.ofMinutes(5);

            LiveEvidenceCollector liveCollector = new LiveEvidenceCollector(
                    prometheusUrl, lokiUrl, jaegerUrl, false);
            liveCollector.setTopology(topologyProvider.getTopology());

            // Collect evidence from the primary affected service
            LiveEvidenceReport liveReport = liveCollector.collect(service, ns, lookback,
                    incidentTask.startedAt());
            List<Evidence> allEvidence = new ArrayList<>(liveReport.allEvidence());

            // Also collect from services affected by this failure
            // (chain-aware evidence aggregation — captures blast radius)
            Set<String> affectedNodes = topologyProvider.getTopology().findAffectedNodes(service);
            for (String node : affectedNodes) {
                if (node.equals(service)) continue; // Already collected
                try {
                    LiveEvidenceReport nodeReport = liveCollector.collect(
                            node, ns, lookback, incidentTask.startedAt());
                    allEvidence.addAll(nodeReport.allEvidence());
                    log.info("Chain evidence from {}: {} items", node, nodeReport.totalEvidenceCount());
                } catch (Exception e) {
                    log.warn("Failed to collect chain evidence from {}: {}", node, e.getMessage());
                }
            }

            // Also collect Alertmanager evidence (might have alerts from chaos)
            try {
                List<Evidence> alertEvidence = evidenceMapper.map(
                        List.of(), incidentId, service, ns);
                // Don't fail if Alertmanager is empty — chaos doesn't rely on it
                log.info("Alertmanager evidence count for direct RCA: {}", alertEvidence.size());
            } catch (Exception e) {
                log.warn("Alertmanager evidence collection skipped (not required for chaos RCA): {}",
                        e.getMessage());
            }

            log.info("Chaos RCA evidence collected: {} items from live sources, total={}",
                    liveReport.totalEvidenceCount(), allEvidence.size());

            // Bug #4: Liveness probe failure reclassification
            // When latency fault + restart evidence coexist, the pod restart is
            // likely caused by K8s liveness probe timeout, not application crash.
            addLivenessProbeFailureEvidence(allEvidence, faultType, incidentId, service, ns);

            // Run RCA
            InvestigationResult rcaResult = workflow.runFromMemory(
                    incidentTask, allEvidence, topologyProvider.getTopology());

            long durationMs = System.currentTimeMillis() - startTime;

            IncidentRcaResultView resultView = IncidentRcaResultView.completed(rcaResult, durationMs);
            incidentStore.put(incidentId, new IncidentRecord(incidentTask, null, resultView, rcaResult, liveReport));

            log.info("Chaos RCA completed: incidentId={}, decision={}, confidence={}, duration={}ms",
                    incidentId, rcaResult.decision().decisionType(),
                    String.format("%.2f", rcaResult.decision().confidenceScore()), durationMs);

            return resultView;

        } catch (Exception e) {
            log.error("Chaos RCA failed: incidentId={}", incidentId, e);
            IncidentRcaResultView failedView = IncidentRcaResultView.failed(
                    incidentId, incidentTask.alertName(), incidentTask.service(), e.getMessage());
            incidentStore.put(incidentId, new IncidentRecord(incidentTask, null, failedView, null, null));
            return failedView;
        }
    }

    // ── Incident Queries ──────────────────────────────────────────

    public Optional<IncidentRcaResultView> getIncident(String incidentId) {
        IncidentRecord record = incidentStore.get(incidentId);
        return Optional.ofNullable(record).map(r -> r.resultView);
    }

    public Optional<String> getReport(String incidentId) {
        IncidentRecord record = incidentStore.get(incidentId);
        return Optional.ofNullable(record)
                .map(r -> r.rcaResult)
                .map(InvestigationResult::markdownReport);
    }

    public Optional<Map<String, Object>> getIncidentRca(String incidentId) {
        IncidentRecord record = incidentStore.get(incidentId);
        if (record == null || record.rcaResult == null) return Optional.empty();

        InvestigationResult rca = record.rcaResult;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", rca.incidentId());
        result.put("scenarioName", record.sourceAlert != null
                ? "Alert-driven: " + record.sourceAlert.alertName()
                : "Fault injection: " + rca.incidentId());
        result.put("status", "COMPLETED");
        result.put("phase", "completed");
        result.put("incidentId", rca.incidentId());
        result.put("durationMs", record.resultView.durationMs());

        result.put("baseRca", rca);
        result.put("evidenceReport", record.evidenceReport != null ? record.evidenceReport : 
                LiveEvidenceReport.empty());

        return Optional.of(result);
    }

    public List<IncidentRcaResultView> listIncidents() {
        return incidentStore.values().stream()
                .map(r -> r.resultView)
                .toList();
    }

    // ── Internal ──────────────────────────────────────────────────

    private List<AlertmanagerAlert> fetchParsedAlerts(boolean onlyFiring) {
        try {
            String json = alertClient.getAlerts(Map.of(), false);
            List<AlertmanagerAlert> alerts = parser.parse(json);
            if (onlyFiring) {
                return alerts.stream().filter(AlertmanagerAlert::isFiring).toList();
            }
            return alerts;
        } catch (Exception e) {
            log.warn("Failed to fetch alerts from Alertmanager: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<AlertmanagerAlert> findTargetAlert(List<AlertmanagerAlert> alerts,
                                                         IncidentRcaTriggerRequest request) {
        if (request.hasFingerprint()) {
            return alerts.stream()
                    .filter(a -> request.fingerprint().equals(a.fingerprint()))
                    .findFirst();
        }
        if (request.hasNameMatch()) {
            return alerts.stream()
                    .filter(a -> request.alertName().equals(a.alertName()))
                    .filter(a -> request.service() == null || request.service().equals(a.service()))
                    .findFirst();
        }
        return Optional.empty();
    }

    /**
     * Bug #4: If latency fault coexists with restart evidence, the pod restart is
     * likely caused by K8s liveness probe timeout, not a real application crash.
     * Adds a synthetic {@code liveness_probe_failure} evidence item so that the
     * {@code liveness_probe_timeout} pattern can rank above {@code pod_crash_loop}.
     */
    private void addLivenessProbeFailureEvidence(List<Evidence> allEvidence, String faultType,
                                                  String incidentId, String service, String ns) {
        // Check if latency context exists (from fault config or labels)
        boolean hasLatencyContext = "latency".equalsIgnoreCase(faultType);
        if (!hasLatencyContext) {
            // Also check evidence for latency spike (alert-driven path lacks faultType)
            hasLatencyContext = allEvidence.stream()
                    .anyMatch(e -> "metric_latency_p95_spike".equals(e.evidenceType()));
        }
        if (!hasLatencyContext) return;

        // Check if restart evidence exists
        boolean hasRestartEvidence = allEvidence.stream()
                .anyMatch(e -> "metric_restart_rate_increased".equals(e.evidenceType())
                        || "pod_restart_count_increased".equals(e.evidenceType()));
        if (!hasRestartEvidence) return;

        // Already has liveness_probe_failure? skip duplicate
        boolean alreadyHasLiveness = allEvidence.stream()
                .anyMatch(e -> "liveness_probe_failure".equals(e.evidenceType()));
        if (alreadyHasLiveness) return;

        Evidence livenessEvidence = new Evidence(
                "ev-liveness-" + incidentId.substring(0, 8),
                incidentId,
                "kubernetes",
                "liveness_probe_failure",
                service,
                Instant.now(),
                "Pod restarted due to liveness probe timeout — latency spike caused probe to fail, not an application crash",
                Map.of("reason", "LivenessProbeTimeout", "faultType", faultType != null ? faultType : "unknown"),
                0.75
        );
        allEvidence.add(livenessEvidence);
        log.info("Bug #4: Added liveness_probe_failure evidence — restarts likely probe-induced, not crash-loop");
    }

    /**
     * Internal record holding incident data.
     */
    private record IncidentRecord(
            IncidentTask incidentTask,
            AlertmanagerAlert sourceAlert,
            IncidentRcaResultView resultView,
            InvestigationResult rcaResult,
            LiveEvidenceReport evidenceReport
    ) {}
}
