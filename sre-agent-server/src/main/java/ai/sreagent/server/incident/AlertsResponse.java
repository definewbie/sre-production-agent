package ai.sreagent.server.incident;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response for GET /api/incidents/alerts.
 * Contains classified alerts with summary statistics.
 */
public record AlertsResponse(
    String source,
    String checkedAt,
    AlertSummary summary,
    List<AlertView> alerts
) {
    public static AlertsResponse of(List<AlertView> alerts) {
        int total = alerts.size();
        int serviceAlerts = 0;
        int platformAlerts = 0;
        int watchdogAlerts = 0;
        int unsupportedAlerts = 0;
        int ignoredAlerts = 0;
        int rcaEligibleAlerts = 0;

        for (AlertView a : alerts) {
            if (a.relevance() == null) continue;
            switch (a.relevance()) {
                case SERVICE_ALERT -> { serviceAlerts++; rcaEligibleAlerts++; }
                case PLATFORM_ALERT -> platformAlerts++;
                case WATCHDOG_ALERT -> watchdogAlerts++;
                case UNSUPPORTED_ALERT -> unsupportedAlerts++;
                case IGNORED_ALERT -> ignoredAlerts++;
            }
        }

        return new AlertsResponse(
                "alertmanager",
                Instant.now().toString(),
                new AlertSummary(total, serviceAlerts, platformAlerts,
                        watchdogAlerts, unsupportedAlerts, ignoredAlerts, rcaEligibleAlerts),
                alerts
        );
    }

    public record AlertSummary(
            int totalAlerts,
            int serviceAlerts,
            int platformAlerts,
            int watchdogAlerts,
            int unsupportedAlerts,
            int ignoredAlerts,
            int rcaEligibleAlerts
    ) {}
}
