package ai.sreagent.demo.payment;

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
public class ChargeController {

    private static final Logger log = LoggerFactory.getLogger(ChargeController.class);

    private final FaultConfigController faultConfigController;

    public ChargeController(FaultConfigController faultConfigController) {
        this.faultConfigController = faultConfigController;
    }

    @GetMapping("/charge")
    public ResponseEntity<Map<String, Object>> charge() {
        FaultConfig config = faultConfigController.getCurrent();
        String mode = config.mode();
        String txnId = "TXN-" + System.currentTimeMillis();

        log.info("[payment] [charge] txnId={} mode={}", txnId, mode);

        try {
            switch (mode) {
                case "latency" -> {
                    log.info("[payment] [charge] txnId={} injected-latency latencyMs={}", txnId, config.latencyMs());
                    Thread.sleep(config.latencyMs());
                }
                case "error" -> {
                    if (ThreadLocalRandom.current().nextDouble() < config.errorRate()) {
                        log.warn("[payment] [charge] txnId={} injected-error errorRate={}", txnId, config.errorRate());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorBody(txnId, "injected error", mode));
                    }
                }
                case "timeout" -> {
                    log.info("[payment] [charge] txnId={} injected-timeout sleeping 30s", txnId);
                    Thread.sleep(30_000);
                }
                case "mixed" -> {
                    if (config.latencyMs() > 0) {
                        log.info("[payment] [charge] txnId={} injected-latency latencyMs={}", txnId, config.latencyMs());
                        Thread.sleep(config.latencyMs());
                    }
                    if (ThreadLocalRandom.current().nextDouble() < config.errorRate()) {
                        log.warn("[payment] [charge] txnId={} injected-error errorRate={}", txnId, config.errorRate());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorBody(txnId, "injected error (mixed)", mode));
                    }
                    if (ThreadLocalRandom.current().nextDouble() < config.timeoutRate()) {
                        log.info("[payment] [charge] txnId={} injected-timeout (mixed) sleeping 30s", txnId);
                        Thread.sleep(30_000);
                    }
                }
                default -> {
                    // normal mode — no injection
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[payment] [charge] txnId={} interrupted", txnId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(txnId, "interrupted", mode));
        }

        log.info("[payment] [charge] txnId={} success", txnId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txnId", txnId);
        result.put("status", "charged");
        result.put("mode", mode);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> errorBody(String txnId, String error, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("txnId", txnId);
        body.put("status", "failed");
        body.put("error", error);
        body.put("mode", mode);
        return body;
    }
}
