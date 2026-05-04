package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceMapper;
import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceTypes;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertmanagerEvidenceMapperTest {

    private AlertmanagerEvidenceMapper mapper;

    private static final String INCIDENT_ID = "inc-test-001";
    private static final String SERVICE = "order-service";
    private static final String NAMESPACE = "production";

    @BeforeEach
    void setUp() {
        mapper = new AlertmanagerEvidenceMapper();
    }

    private AlertmanagerAlert buildAlert(String alertName, String service,
                                          String severity, String state,
                                          List<String> silencedBy, List<String> inhibitedBy,
                                          String fingerprint) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("alertname", alertName);
        if (service != null) labels.put("service", service);
        labels.put("namespace", NAMESPACE);
        if (severity != null) labels.put("severity", severity);

        return new AlertmanagerAlert(
                labels,
                Map.of("summary", alertName + " alert"),
                Instant.parse("2026-04-28T10:08:00Z"),
                null,
                state,
                fingerprint,
                silencedBy != null ? silencedBy : List.of(),
                inhibitedBy != null ? inhibitedBy : List.of()
        );
    }

    // ------------------------------------------------------------------ //
    //  Firing / resolved state evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("firing alert evidence")
    class FiringAlertEvidence {

        @Test
        @DisplayName("firing alert produces alert_firing evidence")
        void shouldProduceAlertFiringEvidence() {
            AlertmanagerAlert alert = buildAlert(
                    "HighErrorRate", SERVICE, "warning", "active",
                    null, null, "fp1");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_FIRING);

            Evidence firing = evidence.stream()
                    .filter(e -> AlertmanagerEvidenceTypes.ALERT_FIRING.equals(e.evidenceType()))
                    .findFirst().orElseThrow();
            assertThat(firing.content()).contains("HighErrorRate", SERVICE);
            assertThat(firing.strength()).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("resolved alert evidence")
    class ResolvedAlertEvidence {

        @Test
        @DisplayName("resolved alert produces alert_resolved evidence")
        void shouldProduceAlertResolvedEvidence() {
            AlertmanagerAlert alert = buildAlert(
                    "HighErrorRate", SERVICE, "warning", "resolved",
                    null, null, "fp2");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_RESOLVED);
        }
    }

    // ------------------------------------------------------------------ //
    //  Severity evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("severity evidence")
    class SeverityEvidence {

        @Test
        @DisplayName("critical severity alert produces alert_severity_high evidence")
        void criticalSeverityProducesSeverityHigh() {
            AlertmanagerAlert alert = buildAlert(
                    "DiskFull", SERVICE, "critical", "active",
                    null, null, "fp3");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_SEVERITY_HIGH);
        }

        @Test
        @DisplayName("high severity alert produces alert_severity_high evidence")
        void highSeverityProducesSeverityHigh() {
            AlertmanagerAlert alert = buildAlert(
                    "HighLatency", SERVICE, "high", "active",
                    null, null, "fp4");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_SEVERITY_HIGH);
        }
    }

    // ------------------------------------------------------------------ //
    //  Silenced / inhibited evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("silenced alert evidence")
    class SilencedAlertEvidence {

        @Test
        @DisplayName("silenced alert produces alert_silenced evidence")
        void shouldProduceSilencedEvidence() {
            AlertmanagerAlert alert = buildAlert(
                    "SilencedAlert", SERVICE, "warning", "active",
                    List.of("silence-123"), null, "fp5");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_SILENCED);
        }
    }

    @Nested
    @DisplayName("inhibited alert evidence")
    class InhibitedAlertEvidence {

        @Test
        @DisplayName("inhibited alert produces alert_inhibited evidence")
        void shouldProduceInhibitedEvidence() {
            AlertmanagerAlert alert = buildAlert(
                    "InhibitedAlert", SERVICE, "warning", "active",
                    null, List.of("inhibit-456"), "fp6");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_INHIBITED);
        }
    }

    // ------------------------------------------------------------------ //
    //  Grouped evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("grouped alerts evidence")
    class GroupedAlertsEvidence {

        @Test
        @DisplayName("multiple alerts produce alert_grouped evidence")
        void shouldProduceGroupedEvidence() {
            AlertmanagerAlert alert1 = buildAlert(
                    "HighErrorRate", SERVICE, "critical", "active",
                    null, null, "fp7");
            AlertmanagerAlert alert2 = buildAlert(
                    "HighMemoryUsage", SERVICE, "warning", "active",
                    null, null, "fp8");

            List<Evidence> evidence = mapper.map(
                    List.of(alert1, alert2), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence.stream().map(Evidence::evidenceType).toList())
                    .contains(AlertmanagerEvidenceTypes.ALERT_GROUPED);

            Evidence grouped = evidence.stream()
                    .filter(e -> AlertmanagerEvidenceTypes.ALERT_GROUPED.equals(e.evidenceType()))
                    .findFirst().orElseThrow();
            assertThat(grouped.content()).contains("2 grouped alerts");
            assertThat(grouped.attributes()).containsEntry("groupedAlertCount", 2);
        }
    }

    // ------------------------------------------------------------------ //
    //  No-signal evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("empty alert list evidence")
    class EmptyAlertListEvidence {

        @Test
        @DisplayName("empty alert list produces alert_no_signal evidence")
        void shouldProduceNoSignalEvidence() {
            List<Evidence> evidence = mapper.map(
                    List.of(), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence).hasSize(1);
            Evidence e = evidence.getFirst();
            assertThat(e.evidenceType()).isEqualTo(AlertmanagerEvidenceTypes.ALERT_NO_SIGNAL);
            assertThat(e.strength()).isEqualTo(0.0);
        }
    }

    // ------------------------------------------------------------------ //
    //  Source
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("evidence source")
    class EvidenceSource {

        @Test
        @DisplayName("evidence source is alertmanager")
        void sourceShouldBeAlertmanager() {
            AlertmanagerAlert alert = buildAlert(
                    "TestAlert", SERVICE, "warning", "active",
                    null, null, "fp9");

            List<Evidence> evidence = mapper.map(List.of(alert), INCIDENT_ID, SERVICE, NAMESPACE);

            assertThat(evidence).isNotEmpty();
            assertThat(evidence).allSatisfy(e ->
                    assertThat(e.source()).isEqualTo("alertmanager"));
        }
    }
}
