package ai.sreagent.demo.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReserveController {

    private static final Logger log = LoggerFactory.getLogger(ReserveController.class);

    private final FaultConfigController faultConfigController;

    public ReserveController(FaultConfigController faultConfigController) {
        this.faultConfigController = faultConfigController;
    }

    @GetMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserve() {
        FaultConfig config = faultConfigController.getCurrent();
        String mode = config.mode();
        String reserveId = "RSV-" + System.currentTimeMillis();

        log.info("[inventory] [reserve] reserveId={} mode={}", reserveId, mode);

        try {
            // Apply latency injection
            if (config.latencyMs() > 0) {
                log.info("[inventory] [reserve] reserveId={} injected-latency latencyMs={}", reserveId, config.latencyMs());
                Thread.sleep(config.latencyMs());
            }

            // Apply error injection
            if (!"normal".equals(mode) && ThreadLocalRandom.current().nextDouble() < config.errorRate()) {
                log.warn("[inventory] [reserve] reserveId={} injected-error errorRate={}", reserveId, config.errorRate());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorBody(reserveId, "injected error", mode));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[inventory] [reserve] reserveId={} interrupted", reserveId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(reserveId, "interrupted", mode));
        }

        log.info("[inventory] [reserve] reserveId={} success", reserveId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reserveId", reserveId);
        result.put("status", "reserved");
        result.put("mode", mode);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> errorBody(String reserveId, String error, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reserveId", reserveId);
        body.put("status", "failed");
        body.put("error", error);
        body.put("mode", mode);
        return body;
    }
}
