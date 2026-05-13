package ai.sreagent.server.incident;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentDetectorTest {

    @Test
    void hasActiveFaultTreatsNormalAndUnknownAsInactive() {
        assertThat(IncidentDetector.hasActiveFault(null)).isFalse();
        assertThat(IncidentDetector.hasActiveFault("")).isFalse();
        assertThat(IncidentDetector.hasActiveFault("{}")).isFalse();
        assertThat(IncidentDetector.hasActiveFault("normal")).isFalse();
        assertThat(IncidentDetector.hasActiveFault("unknown")).isFalse();
        assertThat(IncidentDetector.hasActiveFault("{\"mode\":\"normal\"}")).isFalse();
    }

    @Test
    void inferFaultTypeSupportsModeStringsAndJsonPayloads() {
        assertThat(IncidentDetector.inferFaultType("latency")).isEqualTo("latency");
        assertThat(IncidentDetector.inferFaultType("error")).isEqualTo("error");
        assertThat(IncidentDetector.inferFaultType("timeout")).isEqualTo("timeout");
        assertThat(IncidentDetector.inferFaultType("{\"mode\":\"latency\",\"latencyMs\":3000}"))
                .isEqualTo("latency");
        assertThat(IncidentDetector.inferFaultType("{\"mode\" : \"error\", \"errorRate\":0.8}"))
                .isEqualTo("error");
        assertThat(IncidentDetector.inferFaultType("{\"mode\":\"timeout\",\"timeoutRate\":1.0}"))
                .isEqualTo("timeout");
    }

    @Test
    void hasActiveFaultRecognizesInjectedFaultModes() {
        assertThat(IncidentDetector.hasActiveFault("latency")).isTrue();
        assertThat(IncidentDetector.hasActiveFault("error")).isTrue();
        assertThat(IncidentDetector.hasActiveFault("timeout")).isTrue();
        assertThat(IncidentDetector.hasActiveFault("{\"mode\":\"latency\",\"latencyMs\":3000}"))
                .isTrue();
    }
}
