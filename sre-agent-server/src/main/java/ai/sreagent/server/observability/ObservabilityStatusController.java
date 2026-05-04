package ai.sreagent.server.observability;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for observability stack health status.
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityStatusController {

    private final ObservabilityStatusService statusService;

    public ObservabilityStatusController(ObservabilityStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ResponseEntity<ObservabilityStatusResponse> getStatus() {
        return ResponseEntity.ok(statusService.checkAll());
    }

    @PostMapping("/status/check")
    public ResponseEntity<ObservabilityStatusResponse> refreshStatus() {
        return ResponseEntity.ok(statusService.checkAll());
    }
}
