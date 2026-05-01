package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes confidence scores for verified hypotheses using a deterministic,
 * explainable scoring formula.
 *
 * Formula:
 *   rawScore = pattern.baseScore
 *            + sum(weight for each matched supporting evidence type)
 *            - sum(abs(weight) for each matched counter evidence type)
 *            - missingPenalty
 *            - contradictionPenalty
 *   score = clamp(rawScore, 0.0, 1.0), rounded to 2 decimals
 *
 * MVP calibration note:
 *   Confidence weights are manually assigned based on SRE diagnostic experience.
 *   They are NOT learned from historical incident data.
 *   Production systems should replace this with data-driven calibration.
 */
public class ConfidenceScorer {

    private static final double MISSING_PENALTY_PER_ITEM = 0.10;
    private static final double CONTRADICTION_PENALTY = 0.00;
    private static final String CALIBRATION_NOTE =
            "MVP confidence score is based on manually assigned, explainable SRE diagnostic weights. "
            + "It is not learned from historical incidents yet.";

    /**
     * Score a single hypothesis given its verification result, pattern, and evidence.
     */
    public ConfidenceResult score(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            VerificationResult verification,
            List<Evidence> evidence
    ) {
        Map<String, Double> weights = pattern.confidenceWeights();

        // Build evidence type → id mapping for factor descriptions
        List<String> supportingFactors = new ArrayList<>();
        List<String> counterFactors = new ArrayList<>();

        double supportingBonus = 0.0;
        for (String evId : verification.supportingEvidenceIds()) {
            evidence.stream()
                    .filter(e -> e.id().equals(evId))
                    .findFirst()
                    .ifPresent(e -> {
                        supportingFactors.add(e.evidenceType() + ": " + e.content());
                    });
            // Find the evidence type for weight lookup
            String evType = evidence.stream()
                    .filter(e -> e.id().equals(evId))
                    .map(Evidence::evidenceType)
                    .findFirst().orElse(null);
            if (evType != null && weights.containsKey(evType)) {
                supportingBonus += weights.get(evType);
            }
        }

        double counterPenalty = 0.0;
        for (String evId : verification.counterEvidenceIds()) {
            evidence.stream()
                    .filter(e -> e.id().equals(evId))
                    .findFirst()
                    .ifPresent(e -> {
                        counterFactors.add(e.evidenceType() + ": " + e.content());
                    });
            String evType = evidence.stream()
                    .filter(e -> e.id().equals(evId))
                    .map(Evidence::evidenceType)
                    .findFirst().orElse(null);
            if (evType != null && weights.containsKey(evType)) {
                counterPenalty += Math.abs(weights.get(evType));
            }
        }

        // Only penalize missing evidence types (prefixed "Missing expected evidence type: "),
        // not human-readable evidenceRequirements which are descriptive text, not matchable types.
        long missingTypeCount = verification.missingEvidence().stream()
                .filter(m -> m.startsWith("Missing expected evidence type: "))
                .count();
        double missingPenalty = missingTypeCount * MISSING_PENALTY_PER_ITEM;
        double contradictionPenalty = verification.contradictions().size() * CONTRADICTION_PENALTY;

        double rawScore = pattern.baseScore() + supportingBonus - counterPenalty
                - missingPenalty - contradictionPenalty;

        double score = Math.round(Math.max(0.0, Math.min(1.0, rawScore)) * 100.0) / 100.0;

        String level = mapLevel(score);
        String decision = mapDecision(score);

        return new ConfidenceResult(
                hypothesis.id(),
                score,
                level,
                List.copyOf(supportingFactors),
                List.copyOf(counterFactors),
                verification.missingEvidence(),
                verification.contradictions(),
                decision,
                CALIBRATION_NOTE
        );
    }

    /**
     * Score all hypotheses in batch.
     */
    public List<ConfidenceResult> scoreAll(
            List<Hypothesis> hypotheses,
            Map<String, DiagnosticPattern> patterns,
            List<VerificationResult> verifications,
            List<Evidence> evidence
    ) {
        Map<String, VerificationResult> verByHyp = new java.util.LinkedHashMap<>();
        for (VerificationResult vr : verifications) {
            verByHyp.put(vr.hypothesisId(), vr);
        }

        List<ConfidenceResult> results = new ArrayList<>();
        for (Hypothesis h : hypotheses) {
            DiagnosticPattern pattern = patterns.get(h.patternId());
            VerificationResult vr = verByHyp.get(h.id());
            if (pattern == null || vr == null) {
                continue;
            }
            results.add(score(h, pattern, vr, evidence));
        }
        return results;
    }

    private String mapLevel(double score) {
        if (score >= 0.80) return "high";
        if (score >= 0.60) return "medium";
        if (score >= 0.40) return "low";
        return "very_low";
    }

    private String mapDecision(double score) {
        if (score >= 0.80) return "likely_root_cause";
        if (score >= 0.60) return "probable_root_cause";
        if (score >= 0.40) return "uncertain";
        return "insufficient_evidence";
    }
}
