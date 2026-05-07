package ai.sreagent.server.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncidentRcaTriggerRequest")
class IncidentRcaTriggerRequestTest {

    @Nested
    @DisplayName("hasFingerprint()")
    class HasFingerprint {

        @Test
        @DisplayName("returns true when fingerprint is non-blank")
        void nonBlankFingerprint() {
            var req = new IncidentRcaTriggerRequest("abc123", null, null);
            assertThat(req.hasFingerprint()).isTrue();
        }

        @Test
        @DisplayName("returns false when fingerprint is null")
        void nullFingerprint() {
            var req = new IncidentRcaTriggerRequest(null, "HighLatency", "svc");
            assertThat(req.hasFingerprint()).isFalse();
        }

        @Test
        @DisplayName("returns false when fingerprint is blank")
        void blankFingerprint() {
            var req = new IncidentRcaTriggerRequest("   ", "HighLatency", "svc");
            assertThat(req.hasFingerprint()).isFalse();
        }

        @Test
        @DisplayName("returns false when fingerprint is empty string")
        void emptyFingerprint() {
            var req = new IncidentRcaTriggerRequest("", "HighLatency", "svc");
            assertThat(req.hasFingerprint()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasNameMatch()")
    class HasNameMatch {

        @Test
        @DisplayName("returns true when alertName is non-blank")
        void nonBlankAlertName() {
            var req = new IncidentRcaTriggerRequest(null, "HighLatency", null);
            assertThat(req.hasNameMatch()).isTrue();
        }

        @Test
        @DisplayName("returns true with alertName + service")
        void alertNameAndService() {
            var req = new IncidentRcaTriggerRequest(null, "HighLatency", "order-service");
            assertThat(req.hasNameMatch()).isTrue();
        }

        @Test
        @DisplayName("returns false when alertName is null (fingerprint doesn't count)")
        void nullAlertName() {
            var req = new IncidentRcaTriggerRequest("fp1", null, "svc");
            assertThat(req.hasNameMatch()).isFalse(); // hasNameMatch checks alertName only
        }

        @Test
        @DisplayName("returns false when alertName is blank")
        void blankAlertName() {
            var req = new IncidentRcaTriggerRequest(null, "  ", "svc");
            assertThat(req.hasNameMatch()).isFalse();
        }
    }

    @Test
    @DisplayName("both fingerprint and name match can coexist")
    void bothMatchMethods() {
        var req = new IncidentRcaTriggerRequest("fp1", "HighLatency", "svc");
        assertThat(req.hasFingerprint()).isTrue();
        assertThat(req.hasNameMatch()).isTrue();
    }

    @Test
    @DisplayName("neither match method when all null/blank")
    void noMatchMethod() {
        var req = new IncidentRcaTriggerRequest(null, null, null);
        assertThat(req.hasFingerprint()).isFalse();
        assertThat(req.hasNameMatch()).isFalse();
    }
}
