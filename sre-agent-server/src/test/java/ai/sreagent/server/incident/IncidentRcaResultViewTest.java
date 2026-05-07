package ai.sreagent.server.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncidentRcaResultView")
class IncidentRcaResultViewTest {

    @Nested
    @DisplayName("running() factory")
    class Running {

        @Test
        @DisplayName("creates view with RUNNING status")
        void createsRunningView() {
            var view = IncidentRcaResultView.running("inc-001", "HighLatency", "order-svc", "critical");

            assertThat(view.incidentId()).isEqualTo("inc-001");
            assertThat(view.status()).isEqualTo("RUNNING");
            assertThat(view.triggerSource()).isEqualTo("alertmanager");
            assertThat(view.alertName()).isEqualTo("HighLatency");
            assertThat(view.service()).isEqualTo("order-svc");
            assertThat(view.severity()).isEqualTo("critical");
            assertThat(view.confidenceScore()).isEqualTo(0);
            assertThat(view.durationMs()).isEqualTo(0);
            assertThat(view.errorMessage()).isNull();
            assertThat(view.decisionType()).isNull();
        }
    }

    @Nested
    @DisplayName("failed() factory")
    class Failed {

        @Test
        @DisplayName("creates view with FAILED status and error message")
        void createsFailedView() {
            var view = IncidentRcaResultView.failed("inc-002", "OOM", "api-svc", "Connection refused");

            assertThat(view.incidentId()).isEqualTo("inc-002");
            assertThat(view.status()).isEqualTo("FAILED");
            assertThat(view.triggerSource()).isEqualTo("alertmanager");
            assertThat(view.alertName()).isEqualTo("OOM");
            assertThat(view.service()).isEqualTo("api-svc");
            assertThat(view.errorMessage()).isEqualTo("Connection refused");
            assertThat(view.confidenceScore()).isEqualTo(0);
            assertThat(view.durationMs()).isEqualTo(0);
        }

        @Test
        @DisplayName("creates view with null error message")
        void nullError() {
            var view = IncidentRcaResultView.failed("inc-003", "Test", null, null);
            assertThat(view.status()).isEqualTo("FAILED");
            assertThat(view.errorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("status values")
    class StatusValues {

        @Test
        @DisplayName("IncidentStatus enum has RUNNING, COMPLETED, FAILED")
        void enumValues() {
            var values = IncidentRcaResultView.IncidentStatus.values();
            assertThat(values).extracting(Enum::name)
                    .containsExactlyInAnyOrder("RUNNING", "COMPLETED", "FAILED");
        }
    }
}
