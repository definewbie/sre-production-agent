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

    /** Reduced penalty when the owning provider is observable-blind. */
    private static final double BLIND_PROVIDER_MISSING_PENALTY_PER_ITEM = 0.01;

    /** Score ceiling when most observability providers are blind. */
    private static final double DEGRADED_CONFIDENCE_CAP = 0.50;

    /** Penalty per contradiction */
    private static final double CONTRADICTION_PENALTY = 0.05;

    /** Maximum bonus from corroborating evidence (optional — no penalty when absent) */
    private static final double CORROBORATING_BONUS_CAP = 0.10;

    /** Maximum bounded bonus from topology causality for dependency-propagation hypotheses. */
    private static final double TOPOLOGY_CAUSALITY_BONUS_CAP = 0.10;

    private static final Set<String> PROVIDER_ALIAS_PREFIXES = Set.of("metric_", "log_", "trace_");
    private static final List<String> OBSERVABILITY_PROVIDERS = List.of("metric", "log", "trace");

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
            Map.entry("metric_cpu_usage_high", "cpu_usage_high"),
            // log → core
            Map.entry("log_timeout_error", "dependency_timeout_logs"),
            Map.entry("log_downstream_timeout", "dependency_timeout_logs"),
            Map.entry("log_exception_spike", "exception_logs_present"),
            Map.entry("log_http_5xx", "http_5xx_logs_present"),
            Map.entry("log_oom_message", "kubernetes_event_oomkilled"),
            Map.entry("log_crash_loop", "container_crash_loop_backoff"),
            Map.entry("log_retry_exhausted", "retry_timeout_config_change"),
            // trace → core
            Map.entry("trace_error_span", "error_traces_present"),
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
     * Uses TemporalAlignmentResult.UNKNOWN and TopologyEdge.NONE for backward compatibility.
     */
    public ConfidenceResult score(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            VerificationResult verification,
            List<Evidence> evidence
    ) {
        return score(hypothesis, pattern, verification, evidence,
                TemporalAlignmentResult.UNKNOWN, resolveTopologyEdge(hypothesis, pattern, evidence));
    }

    /**
     * Score a single hypothesis with temporal alignment result.
     * Uses TopologyEdge.NONE for backward compatibility.
     */
    public ConfidenceResult score(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            VerificationResult verification,
            List<Evidence> evidence,
            TemporalAlignmentResult temporalResult
    ) {
        return score(hypothesis, pattern, verification, evidence, temporalResult,
                resolveTopologyEdge(hypothesis, pattern, evidence));
    }

    /**
     * Score a single hypothesis with temporal alignment and topology edge.
     */
    public ConfidenceResult score(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            VerificationResult verification,
            List<Evidence> evidence,
            TemporalAlignmentResult temporalResult,
            TopologyEdge topologyEdge
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

        // Corroborating evidence — optional bonus when present, no penalty when absent.
        // Corroborating types (e.g., deploy_event_near_alert_window) boost confidence
        // when found but don't hurt the score when missing.
        // Check against ALL evidence types (not just supporting), since VE won't classify
        // corroborating types as supporting or counter.
        Set<String> allEvidenceTypes = evidence.stream()
                .map(Evidence::evidenceType)
                .map(ConfidenceScorer::normalizeEvidenceType)
                .collect(Collectors.toSet());

        double weightedCorroboratingCoverage = 0.0;
        List<String> corroboratingTypes = pattern.corroboratingEvidenceTypes();
        if (corroboratingTypes != null && !corroboratingTypes.isEmpty()) {
            double totalCorroboratingWeight = 0.0;
            double matchedCorroboratingWeight = 0.0;
            for (String type : corroboratingTypes) {
                double w = weights.getOrDefault(type, 0.05);
                totalCorroboratingWeight += w;
                if (allEvidenceTypes.contains(type)) {
                    matchedCorroboratingWeight += w;
                }
            }
            if (totalCorroboratingWeight > 0) {
                weightedCorroboratingCoverage = matchedCorroboratingWeight / totalCorroboratingWeight;
            }
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

        ProviderHealth providerHealth = assessProviderHealth(evidence);

        // Missing evidence penalty. Provider-blind missing evidence is not equivalent
        // to confirmed absence, so it receives a lower penalty.
        List<String> missingTypes = verification.missingEvidence().stream()
                .filter(m -> m.startsWith("Missing expected evidence type: "))
                .map(m -> m.substring("Missing expected evidence type: ".length()))
                .toList();
        double missingPenalty = missingTypes.stream()
                .mapToDouble(type -> missingPenaltyFor(type, providerHealth))
                .sum();

        // Contradiction penalty
        double contradictionPenalty = verification.contradictions().size() * CONTRADICTION_PENALTY;

        // Final score calculation
        double temporalScore = temporalResult != null ? temporalResult.score() : 0.0;
        double topologyScore = topologyCausalityScore(pattern, topologyEdge);

        double rawScore = pattern.baseScore()
                + weightedSupportingCoverage * SUPPORTING_BONUS_CAP
                + weightedCorroboratingCoverage * CORROBORATING_BONUS_CAP
                - weightedCounterCoverage * COUNTER_PENALTY_CAP
                - missingPenalty
                - contradictionPenalty
                + temporalScore
                + topologyScore;

        double cappedScore = providerHealth.blindProviders().size() >= 2
                ? Math.min(rawScore, DEGRADED_CONFIDENCE_CAP)
                : rawScore;
        double score = Math.round(Math.max(0.0, Math.min(1.0, cappedScore)) * 100.0) / 100.0;

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
                CALIBRATION_NOTE,
                temporalScore,
                temporalResult != null ? temporalResult.confidence().name() : "UNKNOWN",
                temporalResult != null ? temporalResult.candidateFirstSeen() : null,
                temporalResult != null ? temporalResult.impactedFirstSeen() : null,
                temporalResult != null ? temporalResult.explanation() : "",
                topologyEdge,
                topologyScore,
                providerHealth.diagnosticQuality(),
                providerHealth.blindProviders()
        );
    }

    private double topologyCausalityScore(DiagnosticPattern pattern, TopologyEdge topologyEdge) {
        if (pattern == null || topologyEdge == null || !topologyEdge.isPresent()) {
            return 0.0;
        }
        if (!isTopologySensitivePattern(pattern.id())) {
            return 0.0;
        }

        double confidenceMultiplier = switch (topologyEdge.edgeConfidence()) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.70;
            case LOW -> 0.35;
        };
        double pathMultiplier = topologyEdge.pathLength() <= 1
                ? 1.0
                : Math.max(0.40, 1.0 / topologyEdge.pathLength());
        double directionMultiplier = topologyEdge.direction() == PropagationDirection.UNKNOWN
                ? 0.50
                : 1.0;

        double score = TOPOLOGY_CAUSALITY_BONUS_CAP
                * confidenceMultiplier
                * pathMultiplier
                * directionMultiplier;
        return Math.round(score * 100.0) / 100.0;
    }

    private boolean isTopologySensitivePattern(String patternId) {
        return "downstream_dependency_latency".equals(patternId);
    }

    private ProviderHealth assessProviderHealth(List<Evidence> evidence) {
        Set<String> providersWithNoSignal = new LinkedHashSet<>();
        Set<String> activeProviders = new LinkedHashSet<>();

        for (Evidence item : evidence) {
            String type = item.evidenceType();
            String provider = providerForEvidenceType(type);
            if (provider == null) {
                continue;
            }
            if (type.endsWith("_no_signal")) {
                providersWithNoSignal.add(provider);
            } else {
                activeProviders.add(provider);
            }
        }

        List<String> blindProviders = OBSERVABILITY_PROVIDERS.stream()
                .filter(p -> providersWithNoSignal.contains(p) && !activeProviders.contains(p))
                .toList();
        String quality = blindProviders.isEmpty()
                ? "FULL"
                : blindProviders.size() >= 2 ? "SEVERELY_DEGRADED" : "DEGRADED";
        return new ProviderHealth(Set.copyOf(activeProviders), List.copyOf(blindProviders), quality);
    }

    private double missingPenaltyFor(String coreType, ProviderHealth providerHealth) {
        Set<String> candidateProviders = providersForCoreType(coreType);
        if (candidateProviders.isEmpty()) {
            return MISSING_PENALTY_PER_ITEM;
        }
        if (candidateProviders.stream().anyMatch(providerHealth.activeProviders()::contains)) {
            return MISSING_PENALTY_PER_ITEM;
        }
        if (candidateProviders.stream().allMatch(providerHealth.blindProviders()::contains)) {
            return 0.0;
        }
        if (candidateProviders.stream().anyMatch(providerHealth.blindProviders()::contains)) {
            return BLIND_PROVIDER_MISSING_PENALTY_PER_ITEM;
        }
        return MISSING_PENALTY_PER_ITEM;
    }

    private Set<String> providersForCoreType(String coreType) {
        return ALIAS_TO_CORE.entrySet().stream()
                .filter(e -> e.getValue().equals(coreType))
                .map(e -> providerForEvidenceType(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String providerForEvidenceType(String type) {
        if (type == null) return null;
        if (type.startsWith("metric_")) return "metric";
        if (type.startsWith("log_")) return "log";
        if (type.startsWith("trace_")) return "trace";
        return null;
    }

    private record ProviderHealth(
            Set<String> activeProviders,
            List<String> blindProviders,
            String diagnosticQuality
    ) {}

    /**
     * Resolve a TopologyEdge from available topology evidence.
     *
     * <p>Topology source → confidence mapping:</p>
     * <ul>
     *   <li>TRACE evidence (trace_dependency_path) → HIGH</li>
     *   <li>OBSERVED_DEPENDENCY (downstream latency/log correlation) → MEDIUM</li>
     *   <li>CONFIGURED_TOPOLOGY (service_dependency_match from static config) → MEDIUM</li>
     *   <li>STATIC_FALLBACK (heuristic inference) → LOW</li>
     * </ul>
     *
     * <p>When no topology evidence is found, returns TopologyEdge.NONE.</p>
     */
    private TopologyEdge resolveTopologyEdge(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            List<Evidence> evidence
    ) {
        // Look for trace-derived dependency path (highest confidence)
        var traceDepEvidence = evidence.stream()
                .filter(e -> "trace_dependency_path".equals(e.evidenceType()))
                .findFirst();

        if (traceDepEvidence.isPresent()) {
            Evidence e = traceDepEvidence.get();
            return buildEdge(
                    e.service(), extractToService(e), TopologyEdgeSource.TRACE,
                    PropagationDirection.UPSTREAM_TO_DOWNSTREAM, 1,
                    "从 trace evidence 推断：" + e.service() + " → " + extractToService(e)
                            + "（span parent-child 关系）"
            );
        }

        // Look for static topology evidence (service_dependency_match)
        var staticTopoEvidence = evidence.stream()
                .filter(e -> "service_dependency_match".equals(e.evidenceType()))
                .findFirst();

        if (staticTopoEvidence.isPresent()) {
            Evidence e = staticTopoEvidence.get();
            String src = e.source() != null ? e.source().toLowerCase() : "static";
            TopologyEdgeSource edgeSrc = switch (src) {
                case "trace" -> TopologyEdgeSource.TRACE;
                case "metric", "log", "prometheus", "loki" -> TopologyEdgeSource.OBSERVED_DEPENDENCY;
                case "k8s", "kubernetes", "config", "topology" -> TopologyEdgeSource.CONFIGURED_TOPOLOGY;
                default -> TopologyEdgeSource.STATIC_FALLBACK;
            };
            return buildEdge(
                    e.service(), extractToService(e), edgeSrc,
                    PropagationDirection.UPSTREAM_TO_DOWNSTREAM, 1,
                    "从 " + src + " 证据推断：" + e.service() + " → " + extractToService(e)
                            + "（" + e.content() + "）"
            );
        }

        // Check for observed dependency signals (downstream latency/timeout patterns)
        boolean hasDownstreamSignal = evidence.stream()
                .anyMatch(e -> {
                    String t = normalizeEvidenceType(e.evidenceType());
                    return "downstream_latency_spike".equals(t)
                            || "dependency_timeout_logs".equals(t);
                });

        if (hasDownstreamSignal) {
            String affectedService = hypothesis.affectedService();
            // Try to infer downstream service from evidence content
            String downstreamService = evidence.stream()
                    .filter(e -> {
                        String t = normalizeEvidenceType(e.evidenceType());
                        return "downstream_latency_spike".equals(t)
                                || "dependency_timeout_logs".equals(t);
                    })
                    .findFirst()
                    .map(this::extractToService)
                    .orElse("unknown-downstream");
            return buildEdge(
                    affectedService, downstreamService,
                    TopologyEdgeSource.OBSERVED_DEPENDENCY,
                    PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT, 1,
                    "从 observed dependency 信号推断：" + affectedService + " → " + downstreamService
                            + "（下游延迟/超时证据）"
            );
        }

        return TopologyEdge.NONE;
    }

    /**
     * Attempt to extract the downstream/to service name from evidence content.
     * Heuristic: looks for "→ service" or "dependency" patterns.
     */
    private String extractToService(Evidence evidence) {
        String content = evidence.content();
        if (content == null) return "unknown";

        // Try "X → Y" pattern
        int arrowIdx = content.indexOf("→");
        if (arrowIdx >= 0) {
            String after = content.substring(arrowIdx + 1).trim();
            // Take first word as service name
            int spaceIdx = after.indexOf(" ");
            if (spaceIdx >= 0) {
                return after.substring(0, spaceIdx).replaceAll("[^a-zA-Z0-9_-]", "");
            }
            return after.replaceAll("[^a-zA-Z0-9_-]", "");
        }

        java.util.regex.Matcher dependsMatcher = java.util.regex.Pattern
                .compile("(?i)depends\\s+on\\s+([a-zA-Z0-9_-]+)")
                .matcher(content);
        if (dependsMatcher.find()) {
            return dependsMatcher.group(1);
        }

        // Try to find service name from evidence.service that differs from the source
        if (!evidence.service().equals("unknown") && !evidence.service().isEmpty()) {
            return evidence.service();
        }

        // Fallback: try "payment-service" pattern in content
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([a-z]+-service)")
                .matcher(content);
        if (m.find()) {
            return m.group(1);
        }

        return "unknown";
    }

    private TopologyEdge buildEdge(
            String fromService, String toService,
            TopologyEdgeSource source, PropagationDirection direction,
            int pathLength, String explanation
    ) {
        return new TopologyEdge(
                fromService, toService, source,
                TopologyEdge.deriveConfidence(source), direction,
                pathLength, explanation
        );
    }

    /**
     * Score all hypotheses in batch (no temporal alignment).
     */
    public List<ConfidenceResult> scoreAll(
            List<Hypothesis> hypotheses,
            Map<String, DiagnosticPattern> patterns,
            List<VerificationResult> verifications,
            List<Evidence> evidence
    ) {
        return scoreAll(hypotheses, patterns, verifications, evidence, Map.of());
    }

    /**
     * Score all hypotheses in batch with temporal alignment results.
     */
    public List<ConfidenceResult> scoreAll(
            List<Hypothesis> hypotheses,
            Map<String, DiagnosticPattern> patterns,
            List<VerificationResult> verifications,
            List<Evidence> evidence,
            Map<String, TemporalAlignmentResult> temporalResults
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
            TemporalAlignmentResult temporal = temporalResults.getOrDefault(
                    h.id(), TemporalAlignmentResult.UNKNOWN);
            results.add(score(h, pattern, vr, evidence, temporal));
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
