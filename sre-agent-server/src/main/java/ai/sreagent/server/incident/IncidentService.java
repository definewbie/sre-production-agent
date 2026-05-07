package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.client.AlertmanagerClient;
import ai.sreagent.alertmanager.client.AlertmanagerClientConfig;
import ai.sreagent.alertmanager.client.HttpAlertmanagerClient;
import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceMapper;
import ai.sreagent.alertmanager.mapper.AlertmanagerIncidentMapper;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.parser.AlertmanagerResponseParser;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.server.live.LiveEvidenceCollector;
import ai.sreagent.server.live.LiveEvidenceReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for alert-driven incident intake and RCA.
 *
 * Two roles clearly separated:
 * - Trigger Role: Alertmanager alert → IncidentTask → RCA entry point
 * - Evidence Source Role: Alertmanager alerts → Evidence (one of many sources)
 *
 * Production-like path: GET /api/incidents/alerts → pick alert → POST /api/incidents/from-alert → RCA
 * Lab/Demo path: uses LiveScenarioService's POST /api/live-scenario/run (unchanged)
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final AlertmanagerClient alertClient;
    private final AlertmanagerResponseParser parser;
    private final AlertmanagerIncidentMapper incidentMapper;
    private final AlertmanagerEvidenceMapper evidenceMapper;
    private final InvestigationWorkflow workflow;
    private final String prometheusUrl;
    private final String lokiUrl;
    private final String jaegerUrl;

    /** In-memory store for incident records (alert-driven RCA results). */
    private final ConcurrentHashMap<String, IncidentRecord> incidentStore = new ConcurrentHashMap<>();

    public IncidentService(Environment env) {
        String alertmanagerUrl = env.getProperty(
                "sre-agent.observability.alertmanager-url", "http://localhost:9093");
        this.alertClient = new HttpAlertmanagerClient(AlertmanagerClientConfig.of(alertmanagerUrl));
        this.parser = new AlertmanagerResponseParser();
        this.incidentMapper = new AlertmanagerIncidentMapper();
        this.evidenceMapper = new AlertmanagerEvidenceMapper();
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
     * Fetch current firing alerts from Alertmanager.
     * Used by UI to display active alerts for incident intake.
     */
    public List<AlertView> fetchFiringAlerts() {
        List<AlertmanagerAlert> alerts = fetchParsedAlerts(true);
        return alerts.stream()
                .filter(AlertmanagerAlert::isFiring)
                .map(AlertView::from)
                .toList();
    }

    // ── Alert-Driven RCA ──────────────────────────────────────────

    /**
     * Trigger RCA from a specific alert identified by fingerprint or alertName+service.
     *
     * Flow:
     * 1. Find target alert from Alertmanager firing alerts
     * 2. Map to IncidentTask (Trigger Role)
     * 3. Collect evidence from all live sources (Prometheus, Loki, Jaeger, K8s)
     * 4. Collect Alertmanager evidence (Evidence Source Role)
     * 5. Merge all evidence
     * 6. Run InvestigationWorkflow.runFromMemory()
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
        log.info("Triggering alert-driven RCA: alertName={}, service={}, fingerprint={}",
                alert.alertName(), alert.service(), alert.fingerprint());

        // 2. Map to IncidentTask
        IncidentTask incidentTask = incidentMapper.map(alert);
        String incidentId = incidentTask.id();

        // Store running state
        IncidentRcaResultView runningView = IncidentRcaResultView.running(
                incidentId, incidentTask.alertName(), incidentTask.service(), incidentTask.severity());
        incidentStore.put(incidentId, new IncidentRecord(incidentTask, alert, runningView, null, null));

        try {
            // 3. Collect evidence from all live sources
            String service = incidentTask.service();
            String namespace = incidentTask.namespace();
            Duration lookback = Duration.ofMinutes(5);

            LiveEvidenceCollector liveCollector = new LiveEvidenceCollector(
                    prometheusUrl, lokiUrl, jaegerUrl, false);
            LiveEvidenceReport liveReport = liveCollector.collect(service, namespace, lookback);

            List<Evidence> allEvidence = new ArrayList<>(liveReport.allEvidence());

            // 4. Collect Alertmanager evidence (Evidence Source Role)
            List<Evidence> alertEvidence = evidenceMapper.map(
                    List.of(alert), incidentId, service, namespace);
            allEvidence.addAll(alertEvidence);

            log.info("Evidence collected: {} from live sources, {} from alertmanager, total={}",
                    liveReport.totalEvidenceCount(), alertEvidence.size(), allEvidence.size());

            // 5. Run RCA
            InvestigationResult rcaResult = workflow.runFromMemory(incidentTask, allEvidence);

            long durationMs = System.currentTimeMillis() - startTime;

            // 6. Build result view
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

    /**
     * Get the full RCA data for an incident in a LiveScenarioResult-compatible format.
     * This allows the frontend to reuse mapLiveScenarioToRcaView for both paths.
     */
    public Optional<Map<String, Object>> getIncidentRca(String incidentId) {
        IncidentRecord record = incidentStore.get(incidentId);
        if (record == null || record.rcaResult == null) return Optional.empty();

        InvestigationResult rca = record.rcaResult;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", rca.incidentId());
        result.put("scenarioName", "Alert-driven: " + record.sourceAlert.alertName());
        result.put("status", "COMPLETED");
        result.put("phase", "completed");
        result.put("incidentId", rca.incidentId());
        result.put("durationMs", record.resultView.durationMs());

        // Nest InvestigationResult as baseRca (same structure as LiveScenarioResult)
        result.put("baseRca", rca);

        // Evidence report (from stored live report)
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
