package ai.sreagent.server.controller;

import ai.sreagent.server.demo.DemoServiceClient;
import ai.sreagent.server.demo.DemoServiceConfig;
import ai.sreagent.server.demo.DemoServiceStatus;
import ai.sreagent.server.demo.DemoServicesStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Chaos Experiment (Lab Demo fault injection).
 *
 * Provides endpoints to:
 * - Query current chaos status of demo services
 * - Inject faults (latency / error / timeout) on a target service
 * - Stop a running experiment on a service
 * - Reset all demo services to normal
 *
 * This is a Lab Demo / Experiment page — NOT a production remediation tool.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final DemoServiceClient demoClient;

    // In-memory experiment tracking (simple, no persistence needed for demo)
    private final Map<String, ChaosExperimentState> experiments = new LinkedHashMap<>();

    public ChaosController(DemoServiceClient demoClient) {
        this.demoClient = demoClient;
    }

    /**
     * GET /api/chaos/status
     * Returns current chaos status for all demo services plus any running experiments.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            expireCompletedExperiments();
            DemoServicesStatusResponse status = demoClient.checkAllServices();

            List<Map<String, Object>> serviceStatuses = status.services().stream()
                    .map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("service", s.service());
                        m.put("health", s.health());
                        m.put("faultConfig", s.faultConfig());
                        m.put("reachable", s.reachable());
                        // Add experiment state if one is running on this service
                        ChaosExperimentState exp = experiments.get(s.service());
                        if (exp != null && exp.active) {
                            m.put("experiment", expToMap(exp));
                        }
                        return m;
                    }).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("services", serviceStatuses);
            result.put("topology", status.topology());
            result.put("activeExperiments", experiments.values().stream()
                    .filter(e -> e.active)
                    .map(this::expToMap)
                    .toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to query chaos status", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "查询 Chaos 状态失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/chaos/start
     * Inject a fault on a target demo service.
     *
     * Body example:
     * {
     *   "targetService": "payment-service",
     *   "faultType": "latency",
     *   "latencyMs": 1500,
     *   "errorRate": 0,
     *   "durationSeconds": 300,
     *   "rps": 2,
     *   "experimentName": "payment latency demo",
     *   "description": "Inject latency into payment-service for RCA demo"
     * }
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startExperiment(@RequestBody Map<String, Object> body) {
        try {
            expireCompletedExperiments();
            String targetService = (String) body.get("targetService");
            String faultType = (String) body.get("faultType");

            // Validate inputs
            if (targetService == null || targetService.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "目标服务不能为空"));
            }
            if (faultType == null || faultType.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "故障类型不能为空"));
            }

            // Validate service name
            List<String> validServices = List.of("order-service", "payment-service", "inventory-service");
            if (!validServices.contains(targetService)) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "无效的目标服务: " + targetService + "。可选: " + String.join(", ", validServices)));
            }

            // Validate fault type
            List<String> validFaultTypes = List.of("latency", "error", "timeout", "resource_pressure");
            if (!validFaultTypes.contains(faultType)) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "无效的故障类型: " + faultType + "。可选: " + String.join(", ", validFaultTypes)));
            }

            // Build fault config
            Map<String, Object> faultConfig = new LinkedHashMap<>();
            faultConfig.put("mode", faultType);

            switch (faultType) {
                case "latency" -> {
                    int latencyMs = body.containsKey("latencyMs") && body.get("latencyMs") != null
                            ? ((Number) body.get("latencyMs")).intValue() : 2000;
                    faultConfig.put("latencyMs", latencyMs);
                    faultConfig.put("errorRate", 0.0);
                    faultConfig.put("timeoutRate", 0.0);
                }
                case "error" -> {
                    double errorRate = body.containsKey("errorRate") && body.get("errorRate") != null
                            ? ((Number) body.get("errorRate")).doubleValue() : 0.5;
                    faultConfig.put("latencyMs", 0);
                    faultConfig.put("errorRate", errorRate);
                    faultConfig.put("timeoutRate", 0.0);
                }
                case "timeout" -> {
                    int timeoutMs = body.containsKey("latencyMs") && body.get("latencyMs") != null
                            ? ((Number) body.get("latencyMs")).intValue() : 5000;
                    faultConfig.put("latencyMs", timeoutMs);
                    faultConfig.put("errorRate", 0.0);
                    faultConfig.put("timeoutRate", 1.0);
                }
                case "resource_pressure" -> {
                    // Reserved for future implementation
                    return ResponseEntity.status(501).body(
                            Map.of("error", "资源压力注入尚未实现 (resource_pressure not yet implemented)"));
                }
            }

            // Inject fault on target service
            log.info("Injecting fault: service={}, type={}, config={}", targetService, faultType, faultConfig);
            demoClient.setNamedServiceFaultConfig(targetService, faultConfig);

            // Generate traffic to produce observable metrics
            int rps = body.containsKey("rps") && body.get("rps") != null
                    ? ((Number) body.get("rps")).intValue() : 2;
            int durationSeconds = body.containsKey("durationSeconds") && body.get("durationSeconds") != null
                    ? ((Number) body.get("durationSeconds")).intValue() : 300;
            demoClient.generateTrafficAsync(rps, durationSeconds);
            log.info("Started background checkout traffic: rps={}, durationSeconds={}", rps, durationSeconds);

            // Track experiment state
            String experimentName = body.containsKey("experimentName")
                    ? (String) body.get("experimentName")
                    : faultType + "-" + targetService + "-" + System.currentTimeMillis();
            String description = body.containsKey("description")
                    ? (String) body.get("description")
                    : "Inject " + faultType + " into " + targetService;

            ChaosExperimentState state = new ChaosExperimentState(
                    targetService, faultType, faultConfig,
                    experimentName, description,
                    Instant.now(),
                    Instant.now().plusSeconds(durationSeconds),
                    durationSeconds
            );
            experiments.put(targetService, state);

            // Re-check status
            DemoServicesStatusResponse status = demoClient.checkAllServices();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "RUNNING");
            result.put("targetService", targetService);
            result.put("faultType", faultType);
            result.put("startedAt", state.startedAt.toString());
            result.put("expectedEndAt", state.expectedEndAt.toString());
            result.put("remainingSeconds", durationSeconds);
            result.put("message", "故障已注入 " + targetService + "（" + faultType + "），IncidentDetector 正在持续监测中");
            result.put("experiment", expToMap(state));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to start experiment", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "故障注入失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/chaos/stop
     * Stop the fault injection on a specific service (set to normal).
     *
     * Body: { "targetService": "payment-service" }
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopExperiment(@RequestBody Map<String, Object> body) {
        try {
            String targetService = (String) body.get("targetService");
            if (targetService == null || targetService.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "目标服务不能为空"));
            }

            Map<String, Object> normalConfig = Map.of(
                    "mode", "normal",
                    "latencyMs", 0,
                    "errorRate", 0.0,
                    "timeoutRate", 0.0
            );
            demoClient.setNamedServiceFaultConfig(targetService, normalConfig);
            log.info("Stopped experiment on {}", targetService);

            // Update in-memory state
            ChaosExperimentState exp = experiments.get(targetService);
            if (exp != null) {
                exp.active = false;
                exp.stoppedAt = Instant.now();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "STOPPED");
            result.put("targetService", targetService);
            result.put("message", targetService + " 故障注入已停止，服务恢复中...");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to stop experiment", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "停止实验失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/chaos/reset
     * Reset ALL demo services to normal (clear all faults).
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAll() {
        try {
            demoClient.setAllFaultConfig(Map.of("mode", "normal"));
            experiments.forEach((svc, exp) -> {
                exp.active = false;
                exp.stoppedAt = Instant.now();
            });
            log.info("All demo services reset to normal");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "RESET");
            result.put("message", "所有 demo services 已恢复正常");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to reset all faults", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "恢复正常失败: " + e.getMessage()));
        }
    }

    // --- In-memory experiment state ---

    static class ChaosExperimentState {
        String targetService;
        String faultType;
        Map<String, Object> faultConfig;
        String experimentName;
        String description;
        Instant startedAt;
        Instant expectedEndAt;
        Instant stoppedAt;
        int durationSeconds;
        boolean active = true;

        ChaosExperimentState(String targetService, String faultType,
                             Map<String, Object> faultConfig,
                             String experimentName, String description,
                             Instant startedAt, Instant expectedEndAt,
                             int durationSeconds) {
            this.targetService = targetService;
            this.faultType = faultType;
            this.faultConfig = faultConfig;
            this.experimentName = experimentName;
            this.description = description;
            this.startedAt = startedAt;
            this.expectedEndAt = expectedEndAt;
            this.durationSeconds = durationSeconds;
        }
    }

    private Map<String, Object> expToMap(ChaosExperimentState exp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetService", exp.targetService);
        m.put("faultType", exp.faultType);
        m.put("experimentName", exp.experimentName);
        m.put("description", exp.description);
        m.put("active", exp.active);
        m.put("startedAt", exp.startedAt.toString());
        m.put("expectedEndAt", exp.expectedEndAt.toString());
        if (exp.stoppedAt != null) {
            m.put("stoppedAt", exp.stoppedAt.toString());
        }
        m.put("durationSeconds", exp.durationSeconds);
        long remaining = exp.expectedEndAt.getEpochSecond() - Instant.now().getEpochSecond();
        m.put("remainingSeconds", exp.active ? Math.max(0, remaining) : 0);
        return m;
    }

    private void expireCompletedExperiments() {
        Instant now = Instant.now();
        experiments.forEach((service, exp) -> {
            if (exp.active && !exp.expectedEndAt.isAfter(now)) {
                Map<String, Object> normalConfig = Map.of(
                        "mode", "normal",
                        "latencyMs", 0,
                        "errorRate", 0.0,
                        "timeoutRate", 0.0
                );
                try {
                    demoClient.setNamedServiceFaultConfig(service, normalConfig);
                    exp.active = false;
                    exp.stoppedAt = now;
                    log.info("Auto-expired chaos experiment on {}", service);
                } catch (Exception e) {
                    log.warn("Failed to auto-expire chaos experiment on {}: {}", service, e.getMessage());
                }
            }
        });
    }
}
