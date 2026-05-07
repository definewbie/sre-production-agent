package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.relevance.AlertRelevance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertView (V.2-UI-6.1)")
class AlertViewTest {

    private AlertmanagerAlert makeAlert(String alertName, String service, String namespace) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("alertname", alertName);
        labels.put("service", service);
        labels.put("namespace", namespace);
        labels.put("severity", "warning");
        return new AlertmanagerAlert(labels, Map.of("summary", "test summary"),
                Instant.parse("2025-01-01T00:00:00Z"), null, "active",
                "fp-test", List.of(), List.of());
    }

    @Test
    @DisplayName("from(alert, relevance, rcaEligible, reason) — full classification")
    void fromClassified() {
        var alert = makeAlert("HighLatency", "payment-service", "demo");
        var view = AlertView.from(alert, AlertRelevance.SERVICE_ALERT, true, null);

        assertEquals("fp-test", view.fingerprint());
        assertEquals("HighLatency", view.alertName());
        assertEquals("payment-service", view.service());
        assertEquals("demo", view.namespace());
        assertEquals("warning", view.severity());
        assertEquals("active", view.state());
        assertEquals("test summary", view.summary());
        assertEquals(AlertRelevance.SERVICE_ALERT, view.relevance());
        assertTrue(view.rcaEligible());
        assertNull(view.ineligibleReason());
    }

    @Test
    @DisplayName("from(alert, PLATFORM, false, reason) — platform alert")
    void platformAlert() {
        var alert = makeAlert("NodeClock", "node-exporter", "monitoring");
        var view = AlertView.from(alert, AlertRelevance.PLATFORM_ALERT, false, "平台告警");

        assertEquals(AlertRelevance.PLATFORM_ALERT, view.relevance());
        assertFalse(view.rcaEligible());
        assertEquals("平台告警", view.ineligibleReason());
    }

    @Test
    @DisplayName("from(alert) — backward compatible, defaults to SERVICE_ALERT")
    void backwardCompatible() {
        var alert = makeAlert("HighLatency", "payment-service", "demo");
        var view = AlertView.from(alert);

        assertEquals(AlertRelevance.SERVICE_ALERT, view.relevance());
        assertTrue(view.rcaEligible());
        assertNull(view.ineligibleReason());
    }
}
