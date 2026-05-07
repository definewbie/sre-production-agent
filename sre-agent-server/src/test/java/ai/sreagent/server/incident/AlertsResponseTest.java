package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.relevance.AlertRelevance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertsResponse (V.2-UI-6.1)")
class AlertsResponseTest {

    private AlertView alert(AlertRelevance relevance) {
        return new AlertView("fp", "Test", "svc", "ns", "warning", "active",
                null, "", relevance, relevance.isRcaEligible(),
                relevance.isRcaEligible() ? null : "reason", null, null);
    }

    @Test
    @DisplayName("empty list → all zeros")
    void empty() {
        var resp = AlertsResponse.of(List.of());
        assertEquals(0, resp.summary().totalAlerts());
        assertEquals(0, resp.summary().serviceAlerts());
        assertEquals(0, resp.summary().rcaEligibleAlerts());
    }

    @Test
    @DisplayName("mixed alerts → correct summary counts")
    void mixed() {
        var alerts = List.of(
                alert(AlertRelevance.SERVICE_ALERT),
                alert(AlertRelevance.SERVICE_ALERT),
                alert(AlertRelevance.PLATFORM_ALERT),
                alert(AlertRelevance.PLATFORM_ALERT),
                alert(AlertRelevance.PLATFORM_ALERT),
                alert(AlertRelevance.WATCHDOG_ALERT),
                alert(AlertRelevance.UNSUPPORTED_ALERT)
        );
        var resp = AlertsResponse.of(alerts);

        assertEquals(7, resp.summary().totalAlerts());
        assertEquals(2, resp.summary().serviceAlerts());
        assertEquals(3, resp.summary().platformAlerts());
        assertEquals(1, resp.summary().watchdogAlerts());
        assertEquals(1, resp.summary().unsupportedAlerts());
        assertEquals(0, resp.summary().ignoredAlerts());
        assertEquals(2, resp.summary().rcaEligibleAlerts());
    }

    @Test
    @DisplayName("only service alerts → rcaEligible = total")
    void allService() {
        var alerts = List.of(alert(AlertRelevance.SERVICE_ALERT), alert(AlertRelevance.SERVICE_ALERT));
        var resp = AlertsResponse.of(alerts);

        assertEquals(2, resp.summary().totalAlerts());
        assertEquals(2, resp.summary().serviceAlerts());
        assertEquals(2, resp.summary().rcaEligibleAlerts());
        assertEquals(0, resp.summary().platformAlerts());
    }

    @Test
    @DisplayName("source and checkedAt are populated")
    void metadata() {
        var resp = AlertsResponse.of(List.of());
        assertEquals("alertmanager", resp.source());
        assertNotNull(resp.checkedAt());
    }
}
