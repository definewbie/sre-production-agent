package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import java.time.Instant;
import java.util.*;

/**
 * Aligns evidence timestamps against the problem window to determine
 * temporal causality — whether candidate anomaly evidence precedes
 * impacted service evidence in time.
 *
 * <h3>Scoring rules</h3>
 * <ul>
 *   <li>candidate anomaly before impacted anomaly → +0.10</li>
 *   <li>candidate and impacted simultaneous → +0.05</li>
 *   <li>candidate after impacted (reverse causality) → -0.05</li>
 *   <li>evidence inside problem window → up to +0.05</li>
 *   <li>evidence outside problem window → up to -0.05</li>
 * </ul>
 *
 * <h3>Bounds</h3>
 * <p>Total score clamped to [-0.10, +0.15]. Missing timestamps → score 0,
 * confidence UNKNOWN — no penalty for legacy evidence without timestamps.</p>
 */
public class TemporalAligner {

    /**
     * Compute temporal alignment for a hypothesis against available evidence.
     *
     * @param window    the problem time window
     * @param evidence  all collected evidence for this incident
     * @param hypothesis the candidate hypothesis being scored
     * @return temporal alignment result with score and explanation
     */
    public TemporalAlignmentResult align(
            ProblemWindow window,
            List<Evidence> evidence,
            Hypothesis hypothesis
    ) {
        if (window == null || !window.isValid()) {
            return TemporalAlignmentResult.UNKNOWN;
        }
        if (evidence == null || evidence.isEmpty()) {
            return TemporalAlignmentResult.UNKNOWN;
        }

        // Separate candidate and impacted evidence by service name
        String affectedService = hypothesis.affectedService();

        List<Evidence> candidateEvidence = evidence.stream()
                .filter(e -> e.timestamp() != null)
                .filter(e -> affectedService != null && affectedService.equals(e.service()))
                .toList();

        List<Evidence> impactedEvidence = evidence.stream()
                .filter(e -> e.timestamp() != null)
                .filter(e -> affectedService == null || !affectedService.equals(e.service()))
                .toList();

        int totalWithTimestamp = (int) evidence.stream()
                .filter(e -> e.timestamp() != null).count();

        // If no evidence has timestamps, return UNKNOWN
        if (totalWithTimestamp == 0) {
            return TemporalAlignmentResult.UNKNOWN;
        }

        // Determine confidence level
        TemporalConfidence confidence;
        if (candidateEvidence.isEmpty() || impactedEvidence.isEmpty()) {
            confidence = TemporalConfidence.LOW;
        } else {
            confidence = TemporalConfidence.HIGH;
        }

        // Find firstSeen timestamps
        Optional<Instant> candidateFirst = candidateEvidence.stream()
                .map(Evidence::timestamp)
                .filter(Objects::nonNull)
                .min(Instant::compareTo);

        Optional<Instant> impactedFirst = impactedEvidence.stream()
                .map(Evidence::timestamp)
                .filter(Objects::nonNull)
                .min(Instant::compareTo);

        // If we can't distinguish candidate from impacted (e.g., all evidence
        // belongs to one service), downgrade confidence
        if (candidateFirst.isEmpty() && impactedFirst.isEmpty()) {
            return TemporalAlignmentResult.UNKNOWN;
        }
        if (candidateFirst.isEmpty() || impactedFirst.isEmpty()) {
            confidence = TemporalConfidence.LOW;
        }

        // Compute causality score (candidate before/after impacted)
        double causalityScore = 0.0;
        String causalityExplanation = "";

        if (candidateFirst.isPresent() && impactedFirst.isPresent()) {
            Instant c = candidateFirst.get();
            Instant i = impactedFirst.get();

            if (c.isBefore(i)) {
                causalityScore = TemporalAlignmentResult.candidateBeforeImpactedBonus();
                causalityExplanation = STR."candidate anomaly appeared before impacted anomaly (candidate: \{c}, impacted: \{i})";
            } else if (c.equals(i)) {
                causalityScore = TemporalAlignmentResult.simultaneousBonus();
                causalityExplanation = STR."candidate and impacted anomalies appeared simultaneously at \{c}";
            } else {
                // candidate after impacted → reverse causality
                causalityScore = -TemporalAlignmentResult.candidateAfterImpactedPenalty();
                causalityExplanation = STR."candidate anomaly appeared after impacted anomaly — reverse causality (candidate: \{c}, impacted: \{i})";
            }
        } else {
            causalityExplanation = "insufficient candidate/impacted timestamp distinction for causality assessment";
        }

        // Compute window coverage score
        int insideWindow = 0;
        int outsideWindow = 0;

        for (Evidence e : evidence) {
            if (e.timestamp() == null) continue;
            if (window.contains(e.timestamp())) {
                insideWindow++;
            } else {
                outsideWindow++;
            }
        }

        double windowScore = 0.0;
        int totalWithTs = insideWindow + outsideWindow;
        if (totalWithTs > 0) {
            double insideRatio = (double) insideWindow / totalWithTs;
            double outsideRatio = (double) outsideWindow / totalWithTs;

            // Inside window: bonus proportional to ratio, capped
            double insideBonus = insideRatio * TemporalAlignmentResult.insideWindowMaxBonus();

            // Outside window: penalty proportional to ratio, capped
            double outsidePenalty = outsideRatio * TemporalAlignmentResult.outsideWindowMaxPenalty();

            windowScore = insideBonus - outsidePenalty;
        }

        // Combine scores and clamp to bounds
        double rawScore = causalityScore + windowScore;
        double score = Math.max(TemporalAlignmentResult.scoreMin(),
                Math.min(TemporalAlignmentResult.scoreMax(), rawScore));

        // Round to 2 decimals
        score = Math.round(score * 100.0) / 100.0;

        // Build explanation
        StringBuilder expl = new StringBuilder();
        expl.append(causalityExplanation);
        expl.append(STR."; evidence inside window: \{insideWindow}, outside: \{outsideWindow}");
        expl.append(STR."; window coverage score: \{String.format("%.2f", windowScore)}");

        return new TemporalAlignmentResult(
                score,
                confidence,
                expl.toString(),
                candidateFirst.orElse(null),
                impactedFirst.orElse(null),
                insideWindow,
                outsideWindow
        );
    }

    /**
     * Align all hypotheses in batch.
     *
     * @return map of hypothesis ID → temporal alignment result
     */
    public Map<String, TemporalAlignmentResult> alignAll(
            ProblemWindow window,
            List<Evidence> evidence,
            List<Hypothesis> hypotheses
    ) {
        Map<String, TemporalAlignmentResult> results = new LinkedHashMap<>();
        for (Hypothesis h : hypotheses) {
            results.put(h.id(), align(window, evidence, h));
        }
        return results;
    }
}
