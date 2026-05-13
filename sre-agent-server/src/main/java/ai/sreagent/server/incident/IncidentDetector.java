package ai.sreagent.server.incident;

import ai.sreagent.core.domain.ServiceTopology;
import ai.sreagent.server.demo.DemoServiceClient;
import ai.sreagent.server.demo.DemoServiceStatus;
import ai.sreagent.server.demo.DemoServicesStatusResponse;
import ai.sreagent.server.topology.TopologyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Continuous anomaly detector that monitors demo services and triggers RCA
 * only after sustained anomalies (N anomalous observations within a sliding
 * detection window).
 *
 * <p><b>Design rationale:</b>
 * <ul>
 *   <li>Bursty anomalies (single-spike) are filtered out — only sustained
 *       failures trigger RCA, mimicking real-world incident detection.</li>
 *   <li>Separates fault injection (ChaosController) from anomaly detection
 *       and RCA triggering — ChaosController becomes a pure fault injector.</li>
 *   <li>Cooldown prevents RCA flooding when a service oscillates between
 *       healthy/unhealthy states.</li>
 * </ul>
 *
 * <p><b>Configuration (application.yml):</b>
 * <pre>
 *   sre-agent.detector:
 *     enabled: true              # enable/disable periodic scan
 *     interval-seconds: 15       # scan interval
 *     consecutive-threshold: 4   # anomalous observations required in window
 *     window-seconds: 60         # sliding detection window
 *     cooldown-minutes: 5        # min gap between RCAs for same service
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "sre-agent.detector.enabled", havingValue = "true", matchIfMissing = true)
public class IncidentDetector {

    private static final Logger log = LoggerFactory.getLogger(IncidentDetector.class);

    private static final int DEFAULT_INTERVAL_SECONDS = 15;
    private static final int DEFAULT_CONSECUTIVE_THRESHOLD = 4;
    private static final int DEFAULT_COOLDOWN_MINUTES = 5;
    private static final int DEFAULT_WINDOW_SECONDS =
            DEFAULT_INTERVAL_SECONDS * DEFAULT_CONSECUTIVE_THRESHOLD;

    private final DemoServiceClient demoClient;
    private final IncidentService incidentService;
    private final ServiceTopology topology;
    private final int consecutiveThreshold;
    private final Duration detectionWindow;
    private final Duration normalizationWindow;
    private final Duration cooldown;

    /** Per-service anomaly tracking. Key = service name. */
    private final ConcurrentHashMap<String, DetectionState> stateMap = new ConcurrentHashMap<>();

    /** Active incident IDs per service name. */
    private final ConcurrentHashMap<String, String> activeIncidents = new ConcurrentHashMap<>();

    /** Active fingerprint → incident ID, for chain-level deduplication. */
    private final ConcurrentHashMap<IncidentFingerprint, String> activeFingerprints = new ConcurrentHashMap<>();

    private record DetectionState(
            List<Instant> anomalyObservations,
            Instant lastRcaAt,
            String lastIncidentId
    ) {
        int anomalyCount() {
            return anomalyObservations.size();
        }
    }

    public IncidentDetector(DemoServiceClient demoClient,
                            IncidentService incidentService,
                            TopologyProvider topologyProvider,
                            org.springframework.core.env.Environment env) {
        this.demoClient = demoClient;
        this.incidentService = incidentService;
        this.topology = topologyProvider.getTopology();
        this.consecutiveThreshold = env.getProperty(
                "sre-agent.detector.consecutive-threshold",
                Integer.class, DEFAULT_CONSECUTIVE_THRESHOLD);
        this.detectionWindow = Duration.ofSeconds(env.getProperty(
                "sre-agent.detector.window-seconds",
                Integer.class, DEFAULT_WINDOW_SECONDS));
        this.normalizationWindow = Duration.ofSeconds(env.getProperty(
                "sre-agent.incident.normalization-window-seconds",
                Integer.class, Math.toIntExact(IncidentFingerprint.DEFAULT_WINDOW.toSeconds())));
        this.cooldown = Duration.ofMinutes(env.getProperty(
                "sre-agent.detector.cooldown-minutes",
                Integer.class, DEFAULT_COOLDOWN_MINUTES));
        log.info("IncidentDetector started: threshold={} observations, window={}s, normalizationWindow={}s, cooldown={}m",
                consecutiveThreshold, detectionWindow.toSeconds(),
                normalizationWindow.toSeconds(), cooldown.toMinutes());
    }

    /**
     * Periodic scan: checks all demo services for sustained anomalies.
     * Runs on a fixed rate — interval configurable via
     * {@code sre-agent.detector.interval-seconds} (default 15s).
     */
    @Scheduled(fixedRateString = "${sre-agent.detector.interval-seconds:" + DEFAULT_INTERVAL_SECONDS + "}000")
    public void detectAnomalies() {
        try {
            DemoServicesStatusResponse status = demoClient.checkAllServices();
            for (DemoServiceStatus svc : status.services()) {
                processService(svc);
            }
        } catch (Exception e) {
            log.warn("IncidentDetector scan failed (will retry): {}", e.getMessage());
        }
    }

    private void processService(DemoServiceStatus svc) {
        String serviceName = svc.service();
        boolean anomalous = isAnomalous(svc);
        Instant now = Instant.now();
        DetectionState current = stateMap.getOrDefault(serviceName,
                new DetectionState(List.of(), Instant.EPOCH, null));

        if (anomalous) {
            List<Instant> observations = addObservation(current.anomalyObservations(), now);
            int observationCount = observations.size();
            log.debug("Service {} anomalous ({}/{} observations in {}s): health={}, faults={}",
                    serviceName, observationCount, consecutiveThreshold, detectionWindow.toSeconds(),
                    svc.health(), svc.faultConfig());

            if (observationCount >= consecutiveThreshold) {
                if (shouldTriggerRca(current, serviceName)) {
                    triggerRcaForService(serviceName, svc);
                }
            }
            stateMap.put(serviceName,
                    new DetectionState(observations, current.lastRcaAt(), current.lastIncidentId()));
        } else {
            if (current.anomalyCount() > 0) {
                log.info("Service {} recovered after {} anomalous observations in sliding window", serviceName,
                        current.anomalyCount());
            }
            stateMap.put(serviceName,
                    new DetectionState(List.of(), current.lastRcaAt(), current.lastIncidentId()));
        }
    }

    private List<Instant> addObservation(List<Instant> existing, Instant now) {
        Instant cutoff = now.minus(detectionWindow);
        List<Instant> observations = new ArrayList<>();
        for (Instant seenAt : existing) {
            if (!seenAt.isBefore(cutoff)) {
                observations.add(seenAt);
            }
        }
        observations.add(now);
        return List.copyOf(observations);
    }

    /**
     * Determines if a service is anomalous based on its health status and fault config.
     * Anomalous = unreachable OR health degraded/down/unknown OR active fault config.
     */
    private boolean isAnomalous(DemoServiceStatus svc) {
        if (!svc.reachable()) {
            return true;
        }
        // Check health string
        String health = svc.health();
        if (health == null || !"up".equalsIgnoreCase(health.trim())) {
            return true;
        }
        return hasActiveFault(svc.faultConfig());
    }

    private boolean shouldTriggerRca(DetectionState state, String serviceName) {
        if (state.lastRcaAt() == null) {
            return true; // First RCA for this service
        }
        Duration since = Duration.between(state.lastRcaAt(), Instant.now());
        if (since.compareTo(cooldown) < 0) {
            log.debug("RCA cooldown active for {}: {}s remaining",
                    serviceName, cooldown.minus(since).toSeconds());
            return false;
        }
        // Chain-level dedup: if another service on the same topology chain
        // already triggered an RCA within the time window, suppress this one.
        IncidentFingerprint fp = IncidentFingerprint.from(serviceName, topology, normalizationWindow);
        if (activeFingerprints.containsKey(fp)) {
            log.info("Chain-level dedup: RCA already active for fingerprint {} (service {}), suppressing {}",
                    fp, activeFingerprints.get(fp), serviceName);
            return false;
        }
        return true;
    }

    private void triggerRcaForService(String serviceName, DemoServiceStatus svc) {
        String incidentId = "inc-detect-" + serviceName + "-" + System.currentTimeMillis();
        String faultType = inferFaultType(svc.faultConfig());
        String investigationService = selectInvestigationService(serviceName);
        String namespace = "demo";
        String severity = "warning";

        // Avoid duplicate RCA for same service (should not trigger due to cooldown,
        // but guard anyway)
        if (activeIncidents.containsKey(serviceName)) {
            log.info("RCA already in progress for {}, skipping", serviceName);
            return;
        }

        activeIncidents.put(serviceName, incidentId);
        activeFingerprints.put(IncidentFingerprint.from(serviceName, topology, normalizationWindow), incidentId);
        log.info("Sustained anomaly detected on {} ({} observations in {}s): triggering RCA {} for impacted service {}",
                serviceName, consecutiveThreshold, detectionWindow.toSeconds(), incidentId, investigationService);

        // Trigger RCA in background thread
        new Thread(() -> {
            try {
                IncidentRcaResultView result = incidentService.triggerRcaDirect(
                        incidentId, investigationService, faultType,
                        "detected-" + faultType + "-" + serviceName,
                        namespace, severity, serviceName);
                log.info("Detector-triggered RCA completed: incidentId={}, decision={}, confidence={}",
                        incidentId, result.decisionType(),
                        String.format("%.2f", result.confidenceScore()));
            } catch (Exception e) {
                log.error("Detector-triggered RCA failed: incidentId={}, service={}",
                        incidentId, serviceName, e);
            } finally {
                activeIncidents.remove(serviceName);
                // Update state with RCA timestamp
                DetectionState prev = stateMap.get(serviceName);
                if (prev != null) {
                    stateMap.put(serviceName,
                            new DetectionState(prev.anomalyObservations(), Instant.now(), incidentId));
                }
            }
        }, "detector-rca-" + incidentId).start();
    }

    private String selectInvestigationService(String faultedService) {
        Set<String> affectedNodes = topology.findAffectedNodes(faultedService);
        return affectedNodes.stream()
                .filter(s -> !s.equals(faultedService))
                .filter(topology::isRoot)
                .findFirst()
                .orElseGet(() -> affectedNodes.stream()
                        .filter(s -> !s.equals(faultedService))
                        .sorted(Comparator.naturalOrder())
                        .findFirst()
                        .orElse(faultedService));
    }

    static boolean hasActiveFault(String faultConfigStr) {
        return switch (inferFaultType(faultConfigStr)) {
            case "latency", "error", "timeout" -> true;
            default -> false;
        };
    }

    static String inferFaultType(String faultConfigStr) {
        if (faultConfigStr == null || faultConfigStr.isBlank() || "{}".equals(faultConfigStr.trim())) {
            return "unknown";
        }
        String lower = faultConfigStr.trim().toLowerCase();
        if ("normal".equals(lower) || "unknown".equals(lower)) {
            return "unknown";
        }
        if ("latency".equals(lower) || lower.matches(".*\"mode\"\\s*:\\s*\"latency\".*")) {
            return "latency";
        }
        if ("error".equals(lower) || lower.matches(".*\"mode\"\\s*:\\s*\"error\".*")) {
            return "error";
        }
        if ("timeout".equals(lower) || lower.matches(".*\"mode\"\\s*:\\s*\"timeout\".*")) {
            return "timeout";
        }
        if (lower.contains("\"errorrate\"") && lower.matches(".*\"errorrate\"\\s*:\\s*[1-9].*")) {
            return "error";
        }
        if (lower.contains("\"timeoutrate\"") && lower.matches(".*\"timeoutrate\"\\s*:\\s*[1-9].*")) {
            return "timeout";
        }
        if (lower.contains("\"latencyms\"") && lower.matches(".*\"latencyms\"\\s*:\\s*[1-9].*")) {
            return "latency";
        }
        return "unknown";
    }
}
