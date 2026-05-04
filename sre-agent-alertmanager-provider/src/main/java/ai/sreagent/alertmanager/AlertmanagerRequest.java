package ai.sreagent.alertmanager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Request to collect Alertmanager alert evidence.
 */
public record AlertmanagerRequest(
    String incidentId,
    Instant startTime,
    Instant endTime,
    Map<String, String> labelMatchers,
    boolean includeResolved,
    boolean onlyFiring
) {
    public AlertmanagerRequest {
        if (labelMatchers == null) labelMatchers = Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String incidentId;
        private Instant startTime;
        private Instant endTime;
        private Map<String, String> labelMatchers = Map.of();
        private boolean includeResolved = false;
        private boolean onlyFiring = false;

        public Builder incidentId(String incidentId) { this.incidentId = incidentId; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder labelMatchers(Map<String, String> labelMatchers) { this.labelMatchers = labelMatchers; return this; }
        public Builder includeResolved(boolean includeResolved) { this.includeResolved = includeResolved; return this; }
        public Builder onlyFiring(boolean onlyFiring) { this.onlyFiring = onlyFiring; return this; }

        public AlertmanagerRequest build() {
            return new AlertmanagerRequest(incidentId, startTime, endTime,
                    labelMatchers, includeResolved, onlyFiring);
        }
    }
}
