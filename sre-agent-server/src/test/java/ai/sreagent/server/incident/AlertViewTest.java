package ai.sreagent.server.incident;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AlertView")
class AlertViewTest {

    private AlertmanagerAlert makeAlert(String alertName, String service, String namespace,
                                         String severity, String state, String fingerprint,
                                         Map<String, String> annotations) {
        Map<String, String> labels = new java.util.HashMap<>();
        if (alertName != null) labels.put("alertname", alertName);
        if (service != null) labels.put("service", service);
        if (namespace != null) labels.put("namespace", namespace);
        if (severity != null) labels.put("severity", severity);
        return new AlertmanagerAlert(
                labels, annotations, Instant.parse("2025-01-01T00:00:00Z"),
                null, state, fingerprint, List.of(), List.of()
        );
    }

    @Test
    @DisplayName("from() maps all fields correctly")
    void mapsAllFields() {
        var alert = makeAlert("HighLatency", "order-service", "production",
                "critical", "active", "fp-abc123",
                Map.of("summary", "P99 latency above 5s"));

        AlertView view = AlertView.from(alert);

        assertThat(view.fingerprint()).isEqualTo("fp-abc123");
        assertThat(view.alertName()).isEqualTo("HighLatency");
        assertThat(view.service()).isEqualTo("order-service");
        assertThat(view.namespace()).isEqualTo("production");
        assertThat(view.severity()).isEqualTo("critical");
        assertThat(view.state()).isEqualTo("active");
        assertThat(view.startsAt()).isNotNull();
        assertThat(view.summary()).isEqualTo("P99 latency above 5s");
    }

    @Nested
    @DisplayName("null/missing field handling")
    class NullHandling {

        @Test
        @DisplayName("fingerprint defaults to empty string when null")
        void nullFingerprint() {
            var alert = makeAlert("Test", null, null, null, "active", null, null);
            AlertView view = AlertView.from(alert);
            assertThat(view.fingerprint()).isEmpty();
        }

        @Test
        @DisplayName("state defaults to 'active' when null")
        void nullState() {
            var alert = makeAlert("Test", null, null, null, null, "fp1", null);
            AlertView view = AlertView.from(alert);
            assertThat(view.state()).isEqualTo("active");
        }

        @Test
        @DisplayName("summary defaults to empty when annotations null")
        void nullAnnotations() {
            var alert = makeAlert("Test", null, null, null, "active", "fp1", null);
            AlertView view = AlertView.from(alert);
            assertThat(view.summary()).isEmpty();
        }

        @Test
        @DisplayName("summary defaults to empty when annotations missing 'summary' key")
        void missingSummaryKey() {
            var alert = makeAlert("Test", null, null, null, "active", "fp1",
                    Map.of("description", "some desc"));
            AlertView view = AlertView.from(alert);
            assertThat(view.summary()).isEmpty();
        }
    }

    @Test
    @DisplayName("service falls back through label chain: service > app > job > pod > deployment")
    void serviceLabelFallback() {
        // Test service label priority: "service" > "app" > "job" > "pod" > "deployment"
        var alertWithApp = new AlertmanagerAlert(
                Map.of("alertname", "Test", "app", "my-app"),
                null, Instant.parse("2025-01-01T00:00:00Z"), null,
                "active", "fp1", List.of(), List.of()
        );
        assertThat(AlertView.from(alertWithApp).service()).isEqualTo("my-app");

        var alertWithJob = new AlertmanagerAlert(
                Map.of("alertname", "Test", "job", "my-job"),
                null, Instant.parse("2025-01-01T00:00:00Z"), null,
                "active", "fp2", List.of(), List.of()
        );
        assertThat(AlertView.from(alertWithJob).service()).isEqualTo("my-job");
    }
}
