package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.parser.AlertmanagerResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertmanagerResponseParserTest {

    private AlertmanagerResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AlertmanagerResponseParser();
    }

    private String loadFixture(String name) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("fixtures/alertmanager/" + name)) {
            assertThat(is).as("fixture resource: " + name).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + name, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Fixture-based parsing tests
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("parse firing alert from fixture")
    class ParseFiringAlert {

        @Test
        @DisplayName("should parse firing_high_error_rate.json into a single active alert")
        void shouldParseFiringAlert() {
            String json = loadFixture("firing_high_error_rate.json");
            List<AlertmanagerAlert> alerts = parser.parse(json);

            assertThat(alerts).hasSize(1);

            AlertmanagerAlert alert = alerts.getFirst();
            assertThat(alert.alertName()).isEqualTo("HighErrorRate");
            assertThat(alert.service()).isEqualTo("order-service");
            assertThat(alert.namespace()).isEqualTo("demo");
            assertThat(alert.severity()).isEqualTo("warning");
            assertThat(alert.isFiring()).isTrue();
            assertThat(alert.state()).isEqualTo("active");
            assertThat(alert.startsAt()).isEqualTo(Instant.parse("2026-04-28T10:08:00Z"));
            assertThat(alert.fingerprint()).isEqualTo("abc123def456");

            // Annotations preserved
            assertThat(alert.annotations())
                    .containsEntry("summary", "order-service error rate is high")
                    .containsEntry("description", "5xx error rate exceeded threshold");

            // Labels preserved
            assertThat(alert.labels())
                    .containsEntry("alertname", "HighErrorRate")
                    .containsEntry("service", "order-service")
                    .containsEntry("namespace", "demo")
                    .containsEntry("severity", "warning");
        }
    }

    @Nested
    @DisplayName("parse resolved alert from fixture")
    class ParseResolvedAlert {

        @Test
        @DisplayName("should parse resolved_high_error_rate.json into a resolved alert")
        void shouldParseResolvedAlert() {
            String json = loadFixture("resolved_high_error_rate.json");
            List<AlertmanagerAlert> alerts = parser.parse(json);

            assertThat(alerts).hasSize(1);

            AlertmanagerAlert alert = alerts.getFirst();
            assertThat(alert.alertName()).isEqualTo("HighErrorRate");
            assertThat(alert.isResolved()).isTrue();
            assertThat(alert.state()).isEqualTo("resolved");
            assertThat(alert.startsAt()).isEqualTo(Instant.parse("2026-04-28T10:08:00Z"));
            assertThat(alert.endsAt()).isEqualTo(Instant.parse("2026-04-28T10:15:00Z"));
            assertThat(alert.hasEndTime()).isTrue();
        }
    }

    @Nested
    @DisplayName("parse multiple alerts from fixture")
    class ParseMultipleAlerts {

        @Test
        @DisplayName("should parse multiple_alerts.json into three alerts")
        void shouldParseMultipleAlerts() {
            String json = loadFixture("multiple_alerts.json");
            List<AlertmanagerAlert> alerts = parser.parse(json);

            assertThat(alerts).hasSize(3);

            assertThat(alerts.get(0).alertName()).isEqualTo("HighErrorRate");
            assertThat(alerts.get(0).severity()).isEqualTo("critical");

            assertThat(alerts.get(1).alertName()).isEqualTo("HighMemoryUsage");
            assertThat(alerts.get(1).severity()).isEqualTo("warning");

            assertThat(alerts.get(2).alertName()).isEqualTo("PodCrashLoop");
            assertThat(alerts.get(2).severity()).isEqualTo("critical");
        }
    }

    @Nested
    @DisplayName("parse empty alerts from fixture")
    class ParseEmptyAlerts {

        @Test
        @DisplayName("should parse empty_alerts.json into empty list")
        void shouldParseEmptyAlerts() {
            String json = loadFixture("empty_alerts.json");
            List<AlertmanagerAlert> alerts = parser.parse(json);

            assertThat(alerts).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  Edge-case parsing tests
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("parse null / blank input")
    class NullAndBlankInput {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyForNull() {
            List<AlertmanagerAlert> alerts = parser.parse(null);
            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank input")
        void shouldReturnEmptyForBlank() {
            List<AlertmanagerAlert> alerts = parser.parse("   ");
            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse invalid JSON")
    class InvalidJson {

        @Test
        @DisplayName("should return empty list for invalid JSON")
        void shouldReturnEmptyForInvalidJson() {
            List<AlertmanagerAlert> alerts = parser.parse("{ not valid json !!!");
            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("missing fields default values")
    class MissingFieldsDefaults {

        @Test
        @DisplayName("alert with missing status defaults to active")
        void missingStatusDefaultsToActive() {
            String json = """
                [
                  {
                    "labels": { "alertname": "NoStatusAlert" },
                    "startsAt": "2026-04-28T10:00:00Z",
                    "endsAt": "0001-01-01T00:00:00Z"
                  }
                ]
                """;

            List<AlertmanagerAlert> alerts = parser.parse(json);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.getFirst().state()).isEqualTo("active");
            assertThat(alerts.getFirst().isFiring()).isTrue();
        }

        @Test
        @DisplayName("alert with missing labels and annotations defaults to empty maps")
        void missingLabelsAndAnnotationsDefaultToEmptyMaps() {
            String json = """
                [
                  {
                    "startsAt": "2026-04-28T10:00:00Z",
                    "endsAt": "0001-01-01T00:00:00Z",
                    "status": { "state": "active" }
                  }
                ]
                """;

            List<AlertmanagerAlert> alerts = parser.parse(json);
            assertThat(alerts).hasSize(1);
            AlertmanagerAlert alert = alerts.getFirst();
            assertThat(alert.labels()).isNotNull().isEmpty();
            assertThat(alert.annotations()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("zero endsAt timestamp (0001-01-01T00:00:00Z) is treated as null")
        void zeroEndsAtTreatedAsNull() {
            String json = """
                [
                  {
                    "labels": { "alertname": "ZeroEndsAt" },
                    "startsAt": "2026-04-28T10:00:00Z",
                    "endsAt": "0001-01-01T00:00:00Z",
                    "status": { "state": "active" }
                  }
                ]
                """;

            List<AlertmanagerAlert> alerts = parser.parse(json);
            assertThat(alerts).hasSize(1);
            AlertmanagerAlert alert = alerts.getFirst();
            assertThat(alert.endsAt()).isNull();
            assertThat(alert.hasEndTime()).isFalse();
        }
    }
}
