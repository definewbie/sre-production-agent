package ai.sreagent.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemWindowTest {

    private static final Instant NOW = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant BEFORE = NOW.minus(10, ChronoUnit.MINUTES);
    private static final Instant AFTER = NOW.plus(20, ChronoUnit.MINUTES);

    // ── contains ──────────────────────────────────────────────────────

    @Test
    void contains_timestampInsideWindow_returnsTrue() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.contains(NOW.plus(5, ChronoUnit.MINUTES))).isTrue();
        assertThat(window.contains(NOW)).isTrue();  // inclusive start
        assertThat(window.contains(NOW.plus(10, ChronoUnit.MINUTES))).isTrue(); // inclusive end
    }

    @Test
    void contains_timestampBeforeWindow_returnsFalse() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.contains(BEFORE)).isFalse();
    }

    @Test
    void contains_timestampAfterWindow_returnsFalse() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.contains(AFTER)).isFalse();
    }

    @Test
    void contains_nullTimestamp_returnsFalse() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.contains(null)).isFalse();
    }

    // ── isBeforeWindow / isAfterWindow ────────────────────────────────

    @Test
    void isBeforeWindow_timestampBefore_start_returnsTrue() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.isBeforeWindow(BEFORE)).isTrue();
    }

    @Test
    void isAfterWindow_timestampAfterEnd_returnsTrue() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.isAfterWindow(AFTER)).isTrue();
    }

    // ── overlaps ──────────────────────────────────────────────────────

    @Test
    void overlaps_intervalOverlapsWindow_returnsTrue() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.overlaps(BEFORE, NOW.plus(1, ChronoUnit.MINUTES))).isTrue();
        assertThat(window.overlaps(NOW.plus(9, ChronoUnit.MINUTES), AFTER)).isTrue();
    }

    @Test
    void overlaps_intervalCompletelyOutsideWindow_returnsFalse() {
        ProblemWindow window = new ProblemWindow(
                NOW, NOW.plus(10, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
        assertThat(window.overlaps(BEFORE, BEFORE.plus(5, ChronoUnit.MINUTES))).isFalse();
    }

    // ── deriveFromIncident ────────────────────────────────────────────

    @Test
    void deriveFromIncident_alertHasStartedAt_derivesWithDefaultMargins() {
        IncidentTask incident = new IncidentTask(
                "inc-1", "HighErrorRate", "order-service", "default",
                "critical", NOW, null, null
        );

        ProblemWindow window = ProblemWindow.deriveFromIncident(incident, List.of());
        assertThat(window.source()).isEqualTo("alert");
        assertThat(window.problemStart()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
        assertThat(window.problemEnd()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(window.lookbackWindow()).isEqualTo(Duration.ofMinutes(5));
        assertThat(window.lookaheadWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(window.isValid()).isTrue();
    }

    @Test
    void deriveFromIncident_alertHasNoStartedAt_fallsBackToEvidenceRange() {
        IncidentTask incident = new IncidentTask(
                "inc-1", "HighErrorRate", "order-service", "default",
                "critical", null, null, null
        );

        Evidence ev1 = new Evidence("e1", "inc-1", "metric", "test_type", "order-service",
                NOW.minus(2, ChronoUnit.MINUTES), "test", null, 1.0);
        Evidence ev2 = new Evidence("e2", "inc-1", "metric", "test_type", "order-service",
                NOW.plus(3, ChronoUnit.MINUTES), "test", null, 1.0);

        ProblemWindow window = ProblemWindow.deriveFromIncident(incident, List.of(ev1, ev2));
        assertThat(window.source()).isEqualTo("evidence_fallback");
        assertThat(window.isValid()).isTrue();
    }

    @Test
    void deriveFromIncident_noTimestampAnywhere_returnsUnknown() {
        IncidentTask incident = new IncidentTask(
                "inc-1", "HighErrorRate", "order-service", "default",
                "critical", null, null, null
        );

        Evidence ev = new Evidence("e1", "inc-1", "metric", "test_type", "order-service",
                null, "test", null, 1.0);

        ProblemWindow window = ProblemWindow.deriveFromIncident(incident, List.of(ev));
        assertThat(window.source()).isEqualTo("unknown");
        assertThat(window.problemStart()).isNull();
        assertThat(window.problemEnd()).isNull();
        assertThat(window.isValid()).isFalse();
    }

    @Test
    void unknownWindow_doesNotThrowOnQuery() {
        ProblemWindow unknown = ProblemWindow.deriveFromIncident(
                new IncidentTask("inc-1", "test", "svc", "default", "critical", null, null, null),
                Collections.emptyList()
        );
        // Should not throw
        assertThat(unknown.contains(NOW)).isFalse();
        assertThat(unknown.isBeforeWindow(NOW)).isFalse();
        assertThat(unknown.isAfterWindow(NOW)).isFalse();
        assertThat(unknown.overlaps(NOW, AFTER)).isFalse();
    }
}
