package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes confidence scores for verified hypotheses using a ratio-based,
 * type-coverage scoring model.
 *
 * <h3>Model v2 — Ratio-Based Coverage</h3>
 *
 * <p>Instead of accumulating absolute weights per evidence instance (which allows
 * evidence quantity to dominate score regardless of counter-evidence), this model
 * measures <b>evidence type coverage and directional consistency</b>.</p>
 *
 * <pre>
 * supportingCoverage = supporting types matched / total supporting types
 * counterCoverage    = counter types matched / total counter types
 *
 * rawScore = baseScore
 *          + supportingCoverage × SUPPORTING_BONUS_CAP
 *          - counterCoverage × COUNTER_PENALTY_CAP
 *          - missingTypes × MISSING_PENALTY_PER_ITEM
 *          - contradictions × CONTRADICTION_PENALTY
 *
 * score = clamp(rawScore, 0.0, 1.0), rounded to 2 decimals
 * </pre>
 *
 * <p>Key properties:</p>
 * <ul>
 *   <li>Score reflects <b>directional consistency</b>, not evidence quantity</li>
 *   <li>65 supporting + 121 counter → low score (counter coverage dominates)</li>
 *   <li>126 supporting + 40 counter → high score (supporting coverage dominates)</li>
 *   <li>Each evidence type counted at most once, regardless of how many instances</li>
 * </ul>
 *
 * <p>MVP calibration note:
 * Confidence weights are manually assigned based on SRE diagnostic experience.
 * They are NOT learned from historical incident data.
 * Production systems should replace this with data-driven calibration.</p>
 */
public class ConfidenceScorer {

    /** Maximum bonus from supporting evidence coverage (0–60% of score range) */
    private static final double SUPPORTING_BONUS_CAP = 0.60;

    /** Maximum penalty from counter evidence coverage (0–30% of score range) */
    private static final double COUNTER_PENALTY_CAP = 0.30;

    /** Penalty per missing core evidence type */
    private static final double MISSING_PENALTY_PER_ITEM = 0.03;

    /** Penalty per contradiction */
    private static final double CONTRADICTION_PENALTY = 0.05;

    private static final Set<String> PROVIDER_ALIAS_PREFIXES = Set.of("metric_", "log_", "trace_");

    /**
     * Provider alias → core evidence type normalization map.
     * Provider aliases are bonus evidence from optional observability providers.
     * When present, they contribute to core type coverage without inflating the denominator.
     */
    private static final Map<String, String> ALIAS_TO_CORE = Map.ofEntries(
            // metric → core
            Map.entry("metric_error_rate_spike", "error_rate_spike_after_deploy"),
            Map.entry("metric_downstream_latency_spike", "downstream_latency_spike"),
            Map.entry("metric_latency_p95_spike", "downstream_latency_spike"),
            Map.entry("metric_latency_p99_spike", "downstream_latency_spike"),
            Map.entry("metric_memory_usage_high", "memory_usage_near_limit"),
            Map.entry("metric_restart_rate_increased", "pod_restart_count_increased"),
            Map.entry("metric_cpu_usage_high", "metric_cpu_usage_high"),
            // log → core
            Map.entry("log_timeout_error", "dependency_timeout_logs"),
            Map.entry("log_downstream_timeout", "dependency_timeout_logs"),
            Map.entry("log_exception_spike", "log_exception_spike"),
            Map.entry("log_http_5xx", "log_http_5xx"),
            Map.entry("log_oom_message", "kubernetes_event_oomkilled"),
            Map.entry("log_crash_loop", "container_crash_loop_backoff"),
            Map.entry("log_retry_exhausted", "retry_timeout_config_change"),
            // trace → core
            Map.entry("trace_error_span", "trace_error_span"),
            Map.entry("trace_root_span_slow", "downstream_latency_spike"),
            Map.entry("trace_downstream_span_slow", "downstream_latency_spike"),
            Map.entry("trace_child_span_dominates_latency", "downstream_latency_spike"),
            Map.entry("trace_dependency_path", "service_dependency_match"),
            Map.entry("trace_timeout_span", "dependency_timeout_logs")
    );

    private static final String CALIBRATION_NOTE =
            "MVP confidence score uses ratio-based evidence type coverage model (v2). "
            + "Score reflects directional consistency, not evidence quantity. "
            + "Weights are manually calibrated, not learned from historical incidents.";

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

        // Collect unique evidence types present in supporting and counter evidence
        Set<String> supportingTypesPresent = new LinkedHashSet<>();
        Set<String> counterTypesPresent = new LinkedHashSet<>();

        List<String> supportingFactors = new ArrayList<>();
        List<String> counterFactors = new ArrayList<>();

        // Map evidence id → type for fast lookup
        Map<String, String> evTypeMap = evidence.stream()
                .collect(Collectors.toMap(Evidence::id, Evidence::evidenceType, (a, b) -> a));

        for (String evId : verification.supportingEvidenceIds()) {
            String evType = evTypeMap.get(evId);
            if (evType != null) {
                String normalized = normalizeEvidenceType(evType);
                supportingTypesPresent.add(normalized);
                evidence.stream()
                        .filter(e -> e.id().equals(evId))
                        .findFirst()
                        .ifPresent(e -> supportingFactors.add(e.evidenceType() + ": " + e.content()));
            }
        }

        for (String evId : verification.counterEvidenceIds()) {
            String evType = evTypeMap.get(evId);
            if (evType != null) {
                String normalized = normalizeEvidenceType(evType);
                counterTypesPresent.add(normalized);
                evidence.stream()
                        .filter(e -> e.id().equals(evId))
                        .findFirst()
                        .ifPresent(e -> counterFactors.add(e.evidenceType() + ": " + e.content()));
            }
        }

        // Collect core (non-alias) supporting and counter types for denominator
        List<String> coreSupportingTypes = pattern.supportingEvidenceTypes().stream()
                .filter(t -> !isProviderAlias(t))
                .toList();
        List<String> coreCounterTypes = pattern.counterEvidenceTypes().stream()
                .filter(t -> !isProviderAlias(t))
                .toList();

        // --- Ratio-based coverage calculation ---
        // Supporting coverage: how many core expected supporting types are actually present
        int totalSupportingTypes = coreSupportingTypes.size();
        long matchedSupportingTypes = coreSupportingTypes.stream()
                .filter(supportingTypesPresent::contains)
                .count();
        double supportingCoverage = totalSupportingTypes > 0
                ? (double) matchedSupportingTypes / totalSupportingTypes
                : 0.0;

        // Counter coverage: how many core expected counter types are actually present
        int totalCounterTypes = coreCounterTypes.size();
        long matchedCounterTypes = coreCounterTypes.stream()
                .filter(counterTypesPresent::contains)
                .count();
        double counterCoverage = totalCounterTypes > 0
                ? (double) matchedCounterTypes / totalCounterTypes
                : 0.0;

        // Apply type-weighted adjustments for supporting coverage
        // Types with higher weights contribute more to coverage score
        double weightedSupportingCoverage = 0.0;
        double totalSupportingWeight = 0.0;
        for (String type : coreSupportingTypes) {
            double w = weights.getOrDefault(type, 0.05); // default weight for unregistered types
            totalSupportingWeight += w;
            if (supportingTypesPresent.contains(type)) {
                weightedSupportingCoverage += w;
            }
        }
        if (totalSupportingWeight > 0) {
            weightedSupportingCoverage /= totalSupportingWeight;
        }

        // Apply type-weighted adjustments for counter coverage
        double weightedCounterCoverage = 0.0;
        double totalCounterWeight = 0.0;
        for (String type : coreCounterTypes) {
            double w = weights.getOrDefault(type, 0.05); // default weight for unregistered types
            totalCounterWeight += w;
            if (counterTypesPresent.contains(type)) {
                weightedCounterCoverage += w;
            }
        }
        if (totalCounterWeight > 0) {
            weightedCounterCoverage /= totalCounterWeight;
        }

        // Missing evidence penalty
        long missingTypeCount = verification.missingEvidence().stream()
                .filter(m -> m.startsWith("Missing expected evidence type: "))
                .count();
        double missingPenalty = missingTypeCount * MISSING_PENALTY_PER_ITEM;

        // Contradiction penalty
        double contradictionPenalty = verification.contradictions().size() * CONTRADICTION_PENALTY;

        // Final score calculation
        double rawScore = pattern.baseScore()
                + weightedSupportingCoverage * SUPPORTING_BONUS_CAP
                - weightedCounterCoverage * COUNTER_PENALTY_CAP
                - missingPenalty
                - contradictionPenalty;

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
        Map<String, VerificationResult> verByHyp = new LinkedHashMap<>();
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

    /** Returns true if the evidence type is a provider alias (metric_*, log_*, trace_*). */
    static boolean isProviderAlias(String evidenceType) {
        if (evidenceType == null) return false;
        return evidenceType.length() > 6
                && (evidenceType.startsWith("metric_")
                        || evidenceType.startsWith("log_")
                        || evidenceType.startsWith("trace_"));
    }

    /**
     * Normalize a provider alias to its core evidence type.
     * If the type is not an alias, returns it unchanged.
     */
    static String normalizeEvidenceType(String type) {
        if (type == null) return null;
        String core = ALIAS_TO_CORE.get(type);
        return core != null ? core : type;
    }

    private String mapDecision(double score) {
        if (score >= 0.80) return "likely_root_cause";
        if (score >= 0.60) return "probable_root_cause";
        if (score >= 0.40) return "uncertain";
        return "insufficient_evidence";
    }
}
