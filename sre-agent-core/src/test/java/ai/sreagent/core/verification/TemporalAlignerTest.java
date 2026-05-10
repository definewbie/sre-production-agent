package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalAlignerTest {

    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");

    private static ProblemWindow validWindow() {
        return new ProblemWindow(
                T0, T0.plus(15, ChronoUnit.MINUTES),
                Duration.ofMinutes(5), Duration.ofMinutes(10), "alert"
        );
    }

    private static Hypothesis testHypothesis(String affectedService) {
        return new Hypothesis(
                "hyp-1", "inc-1", "deployment_regression",
                "Test Hypothesis", "change_regression",
                affectedService, "candidate cause text"
        );
    }

    // ── Candidate before impacted → positive score ────────────────────

    @Test
    void candidateBeforeImpacted_returnsPositiveScore() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                // candidate evidence (order-service) — earlier
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        T0.plus(1, ChronoUnit.MINUTES), "error spike", null, 1.0),
                // impacted evidence (payment-service) — later
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        T0.plus(5, ChronoUnit.MINUTES), "latency spike", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.score()).isGreaterThan(0.0);
        assertThat(result.confidence()).isEqualTo(TemporalConfidence.HIGH);
        assertThat(result.candidateFirstSeen()).isNotNull();
        assertThat(result.impactedFirstSeen()).isNotNull();
        assertThat(result.candidateFirstSeen()).isBefore(result.impactedFirstSeen());
        assertThat(result.explanation()).contains("candidate anomaly appeared before impacted");
    }

    // ── Candidate after impacted → negative score ─────────────────────

    @Test
    void candidateAfterImpacted_returnsNegativeScore() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                // candidate evidence — later
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        T0.plus(10, ChronoUnit.MINUTES), "error spike", null, 1.0),
                // impacted evidence — earlier
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        T0.plus(2, ChronoUnit.MINUTES), "latency spike", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        // Score should be negative (reverse causality)
        assertThat(result.score()).isLessThan(0.0);
        assertThat(result.confidence()).isEqualTo(TemporalConfidence.HIGH);
        assertThat(result.explanation()).contains("reverse causality");
    }

    // ── Simultaneous → small positive ─────────────────────────────────

    @Test
    void candidateAndImpactedSimultaneous_returnsSmallPositiveScore() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        Instant sameTime = T0.plus(3, ChronoUnit.MINUTES);
        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        sameTime, "error spike", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        sameTime, "latency spike", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.explanation()).contains("simultaneously");
    }

    // ── Evidence inside window → bonus ────────────────────────────────

    @Test
    void allEvidenceInsideWindow_receivesBonus() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        T0.plus(2, ChronoUnit.MINUTES), "inside", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        T0.plus(5, ChronoUnit.MINUTES), "inside", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.evidenceInsideWindow()).isEqualTo(2);
        assertThat(result.evidenceOutsideWindow()).isEqualTo(0);
        // With both inside + candidate before impacted, score should be positive
        assertThat(result.score()).isGreaterThan(0.0);
    }

    // ── Evidence outside window → no bonus or small penalty ───────────

    @Test
    void evidenceOutsideWindow_noSignificantPenalty() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        // All evidence is outside the window
        Instant wayBefore = T0.minus(30, ChronoUnit.MINUTES);
        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        wayBefore, "outside", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        wayBefore.plus(1, ChronoUnit.MINUTES), "outside", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.evidenceInsideWindow()).isEqualTo(0);
        assertThat(result.evidenceOutsideWindow()).isEqualTo(2);
        // Outside penalty is bounded — should not be extreme
        assertThat(result.score()).isGreaterThanOrEqualTo(TemporalAlignmentResult.scoreMin());
    }

    // ── Missing timestamps → score 0, confidence UNKNOWN ──────────────

    @Test
    void missingTimestamps_returnsUnknown() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        null, "no timestamp", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        null, "no timestamp", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.confidence()).isEqualTo(TemporalConfidence.UNKNOWN);
    }

    // ── Cannot distinguish candidate/impacted → LOW ───────────────────

    @Test
    void cannotDistinguishCandidateFromImpacted_returnsLowConfidence() {
        ProblemWindow window = validWindow();
        // All evidence is from the same service (candidate only, no impacted)
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        T0.plus(1, ChronoUnit.MINUTES), "only candidate", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "error_rate_spike", "order-service",
                        T0.plus(2, ChronoUnit.MINUTES), "only candidate", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.confidence()).isEqualTo(TemporalConfidence.LOW);
        assertThat(result.impactedFirstSeen()).isNull();
    }

    // ── Score bounds ──────────────────────────────────────────────────

    @Test
    void scoreIsWithinBoundedRange() {
        ProblemWindow window = validWindow();
        // Worst case: candidate after impacted + all evidence outside window
        Instant wayAfter = T0.plus(60, ChronoUnit.MINUTES);
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        wayAfter.plus(1, ChronoUnit.MINUTES), "outside", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        wayAfter, "outside", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        assertThat(result.score()).isGreaterThanOrEqualTo(TemporalAlignmentResult.scoreMin());
        assertThat(result.score()).isLessThanOrEqualTo(TemporalAlignmentResult.scoreMax());
    }

    // ── Null window / empty evidence ──────────────────────────────────

    @Test
    void nullWindow_returnsUnknown() {
        Hypothesis hyp = testHypothesis("order-service");
        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "test", "order-service",
                        T0, "test", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(null, evidence, hyp);

        assertThat(result).isEqualTo(TemporalAlignmentResult.UNKNOWN);
    }

    @Test
    void emptyEvidence_returnsUnknown() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, List.of(), hyp);

        assertThat(result).isEqualTo(TemporalAlignmentResult.UNKNOWN);
    }

    // ── Section 3b: Temporal semantic boundary tests ─────────────────

    /**
     * Stale anomaly scenario: candidate anomaly evidence appears very far
     * (2+ hours) before ProblemWindow. Current algorithm does not have
     * time-distance decay — this test verifies the score remains bounded
     * and does not explode even with extreme temporal offsets.
     *
     * <p>This is a known semantic limitation: for runtime anomalies
     * (latency/error/timeout/resource), evidence far outside the window may
     * be stale/unrelated. Refinement planned for V.2-RCA-1A.5 Fault Mode
     * Evidence Contract.</p>
     */
    @Test
    void candidateFarBeforeWindow_keepsScoreInBoundedRange() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        Instant twoHoursBefore = T0.minus(2, ChronoUnit.HOURS);
        List<Evidence> evidence = List.of(
                // candidate evidence (order-service) — 2h before window
                new Evidence("e1", "inc-1", "metric", "error_rate_spike", "order-service",
                        twoHoursBefore, "stale runtime anomaly", null, 1.0),
                // impacted evidence (payment-service) — inside window
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        T0.plus(5, ChronoUnit.MINUTES), "inside window", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        // Score must stay within [-0.15, +0.15] regardless of time distance
        assertThat(result.score())
                .as("temporalScore must stay bounded even with stale candidate evidence")
                .isGreaterThanOrEqualTo(TemporalAlignmentResult.scoreMin())
                .isLessThanOrEqualTo(TemporalAlignmentResult.scoreMax());

        // Even with correct causality (candidate before impacted), the score
        // is bounded and cannot alone decide root cause
        assertThat(result.candidateFirstSeen()).isNotNull();
        assertThat(result.impactedFirstSeen()).isNotNull();
    }

    /**
     * Edge case: candidate evidence appears after ProblemWindow but before
     * impacted evidence (detected late). Verifies the temporal algorithm
     * does not produce extreme negative scores in this scenario.
     */
    @Test
    void candidateAfterWindowButBeforeImpacted_producesBoundedScore() {
        ProblemWindow window = validWindow();
        Hypothesis hyp = testHypothesis("order-service");

        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "deploy", "deploy_event_near_alert_window",
                        "order-service",
                        T0.plus(20, ChronoUnit.MINUTES), // after window
                        "late-detected deployment", null, 1.0),
                new Evidence("e2", "inc-1", "metric", "latency_spike", "payment-service",
                        T0.plus(25, ChronoUnit.MINUTES), // even later
                        "impacted after candidate", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(window, evidence, hyp);

        // Both evidence outside window → windowScore = -0.05 (penalty)
        // candidate before impacted → +0.10
        // Expected: +0.10 - 0.05 = +0.05, bounded as always
        assertThat(result.score()).isBetween(
                TemporalAlignmentResult.scoreMin(), TemporalAlignmentResult.scoreMax());
        // Downstream decision-maker sees this as a small positive signal,
        // not as sufficient evidence for root cause.
    }

    @Test
    void invalidWindow_returnsUnknown() {
        ProblemWindow invalid = new ProblemWindow(null, null,
                Duration.ofMinutes(5), Duration.ofMinutes(10), "unknown");
        Hypothesis hyp = testHypothesis("order-service");
        List<Evidence> evidence = List.of(
                new Evidence("e1", "inc-1", "metric", "test", "order-service",
                        T0, "test", null, 1.0)
        );

        TemporalAligner aligner = new TemporalAligner();
        TemporalAlignmentResult result = aligner.align(invalid, evidence, hyp);

        assertThat(result).isEqualTo(TemporalAlignmentResult.UNKNOWN);
    }
}
