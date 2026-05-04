package ai.sreagent.demo.order;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaultConfigController {

    private final AtomicReference<FaultConfig> config = new AtomicReference<>(FaultConfig.DEFAULT);

    @GetMapping("/fault-config")
    public FaultConfig getFaultConfig() {
        return config.get();
    }

    @PostMapping("/fault-config")
    public FaultConfig updateFaultConfig(@RequestBody FaultConfig newConfig) {
        config.set(newConfig);
        return config.get();
    }

    public FaultConfig getCurrent() {
        return config.get();
    }
}
