package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Time window bounding the incident investigation scope.
 *
 * <p>A ProblemWindow defines the temporal boundaries within which evidence
 * is considered relevant for RCA. Evidence outside this window receives
 * reduced or no temporal bonus.</p>
 *
 * <h3>Derivation priority</h3>
 * <ol>
 *   <li>Incident explicit window (if available)</li>
 *   <li>Alert startsAt + default lookback/lookahead</li>
 *   <li>Evidence timestamp min/max fallback</li>
 *   <li>Unknown fallback (score = 0, confidence = UNKNOWN)</li>
 * </ol>
 *
 * @param problemStart   start of the problem window (inclusive)
 * @param problemEnd     end of the problem window (inclusive)
 * @param lookbackWindow how far to look back before problemStart
 * @param lookaheadWindow how far to look ahead after problemEnd
 * @param source         how this window was derived
 */
public record ProblemWindow(
        @JsonProperty("problem_start") Instant problemStart,
        @JsonProperty("problem_end") Instant problemEnd,
        @JsonProperty("lookback_window") Duration lookbackWindow,
        @JsonProperty("lookahead_window") Duration lookaheadWindow,
        String source
) {

    /** Default lookback: 5 minutes before alert fires. */
    public static final Duration DEFAULT_LOOKBACK = Duration.ofMinutes(5);

    /** Default lookahead: 10 minutes after alert fires. */
    public static final Duration DEFAULT_LOOKAHEAD = Duration.ofMinutes(10);

    /**
     * Derive ProblemWindow from an incident alert and collected evidence.
     *
     * <p>If the alert has a startedAt timestamp, uses it with default
     * lookback/lookahead margins. Falls back to evidence timestamp range
     * if no alert time is available. Returns an UNKNOWN window if
     * neither source provides timestamps.</p>
     */
    public static ProblemWindow deriveFromIncident(IncidentTask incident, List<Evidence> evidence) {
        if (incident.startedAt() != null) {
            Instant start = incident.startedAt();
            return new ProblemWindow(
                    start.minus(DEFAULT_LOOKBACK),
                    start.plus(DEFAULT_LOOKAHEAD),
                    DEFAULT_LOOKBACK,
                    DEFAULT_LOOKAHEAD,
                    "alert"
            );
        }

        // Fallback: use evidence timestamp range
        Instant minTs = null;
        Instant maxTs = null;
        for (Evidence e : evidence) {
            if (e.timestamp() != null) {
                if (minTs == null || e.timestamp().isBefore(minTs)) {
                    minTs = e.timestamp();
                }
                if (maxTs == null || e.timestamp().isAfter(maxTs)) {
                    maxTs = e.timestamp();
                }
            }
        }

        if (minTs != null && maxTs != null) {
            return new ProblemWindow(
                    minTs.minus(DEFAULT_LOOKBACK),
                    maxTs.plus(DEFAULT_LOOKAHEAD),
                    DEFAULT_LOOKBACK,
                    DEFAULT_LOOKAHEAD,
                    "evidence_fallback"
            );
        }

        // No timestamp information available
        return new ProblemWindow(
                null, null, DEFAULT_LOOKBACK, DEFAULT_LOOKAHEAD, "unknown"
        );
    }

    /** Whether the given timestamp falls within this window (inclusive). */
    public boolean contains(Instant timestamp) {
        if (timestamp == null) return false;
        if (problemStart == null || problemEnd == null) return false;
        return !timestamp.isBefore(problemStart) && !timestamp.isAfter(problemEnd);
    }

    /** Whether the given timestamp is strictly before this window. */
    public boolean isBeforeWindow(Instant timestamp) {
        if (timestamp == null || problemStart == null) return false;
        return timestamp.isBefore(problemStart);
    }

    /** Whether the given timestamp is strictly after this window. */
    public boolean isAfterWindow(Instant timestamp) {
        if (timestamp == null || problemEnd == null) return false;
        return timestamp.isAfter(problemEnd);
    }

    /** Whether an interval [start, end] overlaps with this window. */
    public boolean overlaps(Instant start, Instant end) {
        if (start == null || end == null) return false;
        if (problemStart == null || problemEnd == null) return false;
        return !end.isBefore(problemStart) && !start.isAfter(problemEnd);
    }

    /** Whether this window has valid boundaries. */
    public boolean isValid() {
        return problemStart != null && problemEnd != null
                && !problemEnd.isBefore(problemStart);
    }
}
