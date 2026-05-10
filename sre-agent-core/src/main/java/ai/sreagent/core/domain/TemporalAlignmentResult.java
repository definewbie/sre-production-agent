package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of temporal alignment analysis — measures whether candidate
 * anomaly evidence precedes impacted service evidence in time.
 *
 * <p>This is a bounded scoring dimension, not a hard gate.
 * Score range: -0.15 to +0.15.</p>
 *
 * @param score                  temporal alignment score in [-0.15, 0.15]
 * @param confidence             confidence level of the temporal assessment
 * @param explanation            human-readable explanation of the alignment
 * @param candidateFirstSeen     earliest timestamp of candidate entity evidence
 * @param impactedFirstSeen      earliest timestamp of impacted entity evidence
 * @param evidenceInsideWindow   count of evidence within the problem window
 * @param evidenceOutsideWindow  count of evidence outside the problem window
 */
public record TemporalAlignmentResult(
        double score,
        @JsonProperty("confidence") TemporalConfidence confidence,
        String explanation,
        @JsonProperty("candidate_first_seen") Instant candidateFirstSeen,
        @JsonProperty("impacted_first_seen") Instant impactedFirstSeen,
        @JsonProperty("evidence_inside_window") int evidenceInsideWindow,
        @JsonProperty("evidence_outside_window") int evidenceOutsideWindow
) {

    /** Sentinel result for when no temporal information is available. */
    public static final TemporalAlignmentResult UNKNOWN = new TemporalAlignmentResult(
            0.0, TemporalConfidence.UNKNOWN,
            "No timestamp information available for temporal alignment.",
            null, null, 0, 0
    );

    /** Score for candidate anomaly appearing before impacted anomaly. */
    static final double CANDIDATE_BEFORE_IMPACTED_BONUS = 0.10;

    /** Score for candidate and impacted appearing simultaneously. */
    static final double SIMULTANEOUS_BONUS = 0.05;

    /** Penalty for candidate appearing after impacted (reverse causality — stronger than bonus to avoid cancellation). */
    static final double CANDIDATE_AFTER_IMPACTED_PENALTY = 0.10;

    /** Maximum bonus for evidence inside the problem window. */
    static final double INSIDE_WINDOW_MAX_BONUS = 0.05;

    /** Maximum penalty for evidence outside the problem window. */
    static final double OUTSIDE_WINDOW_MAX_PENALTY = 0.05;

    /** Overall score lower bound. */
    static final double SCORE_MIN = -0.15;

    /** Overall score upper bound. */
    static final double SCORE_MAX = 0.15;

    /** Public accessors for constants used by TemporalAligner. */
    public static double candidateBeforeImpactedBonus() { return CANDIDATE_BEFORE_IMPACTED_BONUS; }
    public static double simultaneousBonus() { return SIMULTANEOUS_BONUS; }
    public static double candidateAfterImpactedPenalty() { return CANDIDATE_AFTER_IMPACTED_PENALTY; }
    public static double insideWindowMaxBonus() { return INSIDE_WINDOW_MAX_BONUS; }
    public static double outsideWindowMaxPenalty() { return OUTSIDE_WINDOW_MAX_PENALTY; }
    public static double scoreMin() { return SCORE_MIN; }
    public static double scoreMax() { return SCORE_MAX; }

    /** Convenience: whether this result represents a meaningful temporal signal. */
    public boolean hasSignal() {
        return confidence != TemporalConfidence.UNKNOWN
                && confidence != TemporalConfidence.LOW;
    }
}
