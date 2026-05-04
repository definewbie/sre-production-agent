package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.mapper.AlertmanagerIncidentMapper;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.core.domain.IncidentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertmanagerIncidentMapperTest {

    private AlertmanagerIncidentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlertmanagerIncidentMapper();
    }

    // ------------------------------------------------------------------ //
    //  Firing alert
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("firing alert mapping")
    class FiringAlertMapping {

        @Test
        @DisplayName("should map firing alert to IncidentTask with correct alertName, service, namespace, severity")
        void shouldMapFiringAlert() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of(
                            "alertname", "HighErrorRate",
                            "service", "order-service",
                            "namespace", "production",
                            "severity", "critical"
                    ),
                    Map.of("summary", "error rate exceeded"),
                    Instant.parse("2026-04-28T10:08:00Z"),
                    null,
                    "active",
                    "abc123def456",
                    List.of(),
                    List.of()
            );

            IncidentTask incident = mapper.map(alert);

            assertThat(incident.alertName()).isEqualTo("HighErrorRate");
            assertThat(incident.service()).isEqualTo("order-service");
            assertThat(incident.namespace()).isEqualTo("production");
            assertThat(incident.severity()).isEqualTo("critical");
            assertThat(incident.startedAt()).isEqualTo(Instant.parse("2026-04-28T10:08:00Z"));
            assertThat(incident.id()).isEqualTo("inc_alertmanager_abc123def456");
        }
    }

    // ------------------------------------------------------------------ //
    //  Resolved alert
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("resolved alert mapping")
    class ResolvedAlertMapping {

        @Test
        @DisplayName("should map resolved alert to IncidentTask correctly")
        void shouldMapResolvedAlert() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of(
                            "alertname", "HighErrorRate",
                            "service", "payment-service",
                            "namespace", "staging",
                            "severity", "warning"
                    ),
                    Map.of("summary", "resolved alert"),
                    Instant.parse("2026-04-28T10:08:00Z"),
                    Instant.parse("2026-04-28T10:15:00Z"),
                    "resolved",
                    "xyz789",
                    List.of(),
                    List.of()
            );

            IncidentTask incident = mapper.map(alert);

            assertThat(incident.alertName()).isEqualTo("HighErrorRate");
            assertThat(incident.service()).isEqualTo("payment-service");
            assertThat(incident.namespace()).isEqualTo("staging");
            assertThat(incident.severity()).isEqualTo("warning");
            assertThat(incident.id()).isEqualTo("inc_alertmanager_xyz789");
        }
    }

    // ------------------------------------------------------------------ //
    //  Service label fallback chain
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("missing service label fallback")
    class ServiceLabelFallback {

        @Test
        @DisplayName("should fall back to 'app' label when 'service' is missing")
        void shouldFallBackToApp() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert", "app", "my-app"),
                    Map.of(), Instant.now(), null, "active", null, List.of(), List.of()
            );
            IncidentTask incident = mapper.map(alert);
            assertThat(incident.service()).isEqualTo("my-app");
        }

        @Test
        @DisplayName("should fall back to 'job' label when 'service' and 'app' are missing")
        void shouldFallBackToJob() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert", "job", "my-job"),
                    Map.of(), Instant.now(), null, "active", null, List.of(), List.of()
            );
            IncidentTask incident = mapper.map(alert);
            assertThat(incident.service()).isEqualTo("my-job");
        }

        @Test
        @DisplayName("should fall back to 'pod' label when 'service', 'app', 'job' are missing")
        void shouldFallBackToPod() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert", "pod", "my-pod-xyz"),
                    Map.of(), Instant.now(), null, "active", null, List.of(), List.of()
            );
            IncidentTask incident = mapper.map(alert);
            assertThat(incident.service()).isEqualTo("my-pod-xyz");
        }

        @Test
        @DisplayName("should fall back to 'deployment' label when others are missing")
        void shouldFallBackToDeployment() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert", "deployment", "my-deploy"),
                    Map.of(), Instant.now(), null, "active", null, List.of(), List.of()
            );
            IncidentTask incident = mapper.map(alert);
            assertThat(incident.service()).isEqualTo("my-deploy");
        }

        @Test
        @DisplayName("should fall back to 'unknown-service' when no service labels exist")
        void shouldFallBackToUnknownService() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert"),
                    Map.of(), Instant.now(), null, "active", null, List.of(), List.of()
            );
            IncidentTask incident = mapper.map(alert);
            assertThat(incident.service()).isEqualTo("unknown-service");
        }
    }

    // ------------------------------------------------------------------ //
    //  Deterministic incident IDs
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("deterministic incident IDs")
    class DeterministicIncidentId {

        @Test
        @DisplayName("alert with fingerprint creates deterministic incident id")
        void shouldUseFingerprintForIncidentId() {
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "TestAlert", "service", "svc"),
                    Map.of(), Instant.parse("2026-01-01T00:00:00Z"), null,
                    "active", "fp98765", List.of(), List.of()
            );

            IncidentTask incident = mapper.map(alert);
            assertThat(incident.id()).isEqualTo("inc_alertmanager_fp98765");
        }

        @Test
        @DisplayName("alert without fingerprint creates deterministic id from alertname_service_epoch")
        void shouldUseAlertNameServiceEpochForIncidentId() {
            Instant startsAt = Instant.parse("2026-04-28T10:08:00Z");
            AlertmanagerAlert alert = new AlertmanagerAlert(
                    Map.of("alertname", "HighErrorRate", "service", "order-service"),
                    Map.of(), startsAt, null,
                    "active", null, List.of(), List.of()
            );

            IncidentTask incident = mapper.map(alert);
            long expectedEpoch = startsAt.getEpochSecond();
            assertThat(incident.id()).isEqualTo(
                    "inc_alertmanager_HighErrorRate_order-service_" + expectedEpoch);
        }
    }

    // ------------------------------------------------------------------ //
    //  Null alert
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("null alert mapping")
    class NullAlertMapping {

        @Test
        @DisplayName("null alert maps to unknown incident")
        void shouldMapNullToUnknownIncident() {
            IncidentTask incident = mapper.map(null);

            assertThat(incident.id()).isEqualTo("inc_alertmanager_unknown");
            assertThat(incident.alertName()).isEqualTo("UnknownAlert");
            assertThat(incident.service()).isEqualTo("unknown-service");
            assertThat(incident.namespace()).isEqualTo("default");
            assertThat(incident.severity()).isEqualTo("warning");
            assertThat(incident.labels()).isEmpty();
            assertThat(incident.annotations()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  Labels and annotations preserved
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("labels and annotations preservation")
    class LabelsAnnotationsPreservation {

        @Test
        @DisplayName("labels and annotations are preserved in IncidentTask")
        void shouldPreserveLabelsAndAnnotations() {
            Map<String, String> labels = Map.of(
                    "alertname", "DiskFull",
                    "service", "storage-service",
                    "namespace", "infra",
                    "severity", "critical",
                    "extra_label", "extra_value"
            );
            Map<String, String> annotations = Map.of(
                    "summary", "Disk is almost full",
                    "description", "Disk usage at 95%",
                    "runbook_url", "https://docs.example.com/runbook"
            );

            AlertmanagerAlert alert = new AlertmanagerAlert(
                    labels, annotations,
                    Instant.parse("2026-04-28T12:00:00Z"), null,
                    "active", "diskfp123", List.of(), List.of()
            );

            IncidentTask incident = mapper.map(alert);

            assertThat(incident.labels()).containsExactlyInAnyOrderEntriesOf(labels);
            assertThat(incident.annotations()).containsExactlyInAnyOrderEntriesOf(annotations);
        }
    }
}
