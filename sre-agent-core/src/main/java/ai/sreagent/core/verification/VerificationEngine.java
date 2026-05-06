package ai.sreagent.core.verification;

import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.Hypothesis;
import ai.sreagent.core.domain.VerificationResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Verifies a hypothesis against collected evidence.
 * Classifies evidence into supporting / counter / missing,
 * and generates human-readable contradictions.
 * Fully deterministic.
 */
public class VerificationEngine {

    /** Low-signal / non-diagnostic types that must never influence supporting or counter scoring. */
    private static final Set<String> IGNORED_TYPES = Set.of(
            "NONE", "k8s_no_signal", "k8s_runtime_healthy", "restart_count_observed"
    );

    /**
     * Verify a single hypothesis against available evidence.
     * Requires the DiagnosticPattern that spawned the hypothesis.
     */
    public VerificationResult verify(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            List<Evidence> evidence
    ) {
        Set<String> evidenceTypes = evidence.stream()
                .map(Evidence::evidenceType)
                .filter(t -> !IGNORED_TYPES.contains(t))
                .collect(Collectors.toSet());

        List<String> supportingIds = evidence.stream()
                .filter(e -> !IGNORED_TYPES.contains(e.evidenceType()))
                .filter(e -> pattern.supportingEvidenceTypes().contains(e.evidenceType()))
                .map(Evidence::id)
                .toList();

        List<String> counterIds = evidence.stream()
                .filter(e -> !IGNORED_TYPES.contains(e.evidenceType()))
                .filter(e -> pattern.counterEvidenceTypes().contains(e.evidenceType()))
                .map(Evidence::id)
                .toList();

        Set<String> coveredTypes = new HashSet<>();
        evidence.forEach(e -> coveredTypes.add(e.evidenceType()));

        List<String> missingEvidence = pattern.evidenceRequirements().stream()
                .filter(req -> !coveredTypes.contains(req))
                .toList();

        // Also check: any supporting evidence types that have zero coverage.
        // Provider alias types (metric_*, log_*, trace_*) are optional bonus evidence
        // from observability providers and should NOT generate missing penalties.
        // Only core evidence types count as missing.
        List<String> uncoveredSupportingTypes = pattern.supportingEvidenceTypes().stream()
                .filter(type -> !evidenceTypes.contains(type))
                .filter(type -> !isProviderAlias(type))
                .map(type -> STR."Missing expected evidence type: \{type}")
                .toList();

        List<String> allMissing = new ArrayList<>(missingEvidence);
        allMissing.addAll(uncoveredSupportingTypes);

        List<String> contradictions = detectContradictions(pattern.id(), evidenceTypes);

        String explanation = buildExplanation(
                hypothesis, pattern, supportingIds, counterIds, allMissing, contradictions
        );

        return new VerificationResult(
                hypothesis.id(),
                supportingIds,
                counterIds,
                List.copyOf(allMissing),
                contradictions,
                explanation
        );
    }

    /**
     * Verify all hypotheses in batch.
     */
    public Map<String, VerificationResult> verifyAll(
            List<Hypothesis> hypotheses,
            Map<String, DiagnosticPattern> patterns,
            List<Evidence> evidence
    ) {
        Map<String, VerificationResult> results = new LinkedHashMap<>();
        for (Hypothesis h : hypotheses) {
            DiagnosticPattern pattern = patterns.get(h.patternId());
            if (pattern == null) {
                continue;
            }
            results.put(h.id(), verify(h, pattern, evidence));
        }
        return results;
    }

    // ── Contradiction rules (deterministic, pattern-specific) ─────────────

    /**
     * Returns true if the evidence type is a provider alias (from Prometheus, Loki, Trace, etc.)
     * Provider aliases are bonus evidence that should not generate missing penalties
     * when absent, since their providers may not be deployed or consulted.
     */
    private boolean isProviderAlias(String evidenceType) {
        return evidenceType.startsWith("metric_")
                || evidenceType.startsWith("log_")
                || evidenceType.startsWith("trace_");
    }

    private List<String> detectContradictions(String patternId, Set<String> evidenceTypes) {
        List<String> contradictions = new ArrayList<>();

        switch (patternId) {
            case "deployment_regression" -> {
                if (evidenceTypes.contains("historical_timeout_logs_present")) {
                    contradictions.add(
                            "Timeout logs existed before the deployment, so the deployment may not be the only cause."
                    );
                }
                if (evidenceTypes.contains("downstream_latency_spike")
                        || evidenceTypes.contains("metric_downstream_latency_spike")
                        || evidenceTypes.contains("trace_downstream_span_slow")) {
                    contradictions.add(
                            "Downstream payment-service latency also increased, so dependency latency remains a competing explanation."
                    );
                }
            }
            case "downstream_dependency_latency" -> {
                if (evidenceTypes.contains("downstream_5xx_absent")
                        || evidenceTypes.contains("log_http_5xx")) {
                    contradictions.add(
                            "payment-service 5xx did not increase, so downstream failure is not fully confirmed."
                    );
                }
                // NOTE: metric_error_rate_spike was previously a contradiction trigger here,
                // but it's also a supporting type for downstream_latency — error rate spikes
                // are expected during latency events. Only an explicit deploy event near the
                // alert window should trigger the deployment competing explanation.
                if (evidenceTypes.contains("deploy_event_near_alert_window")) {
                    contradictions.add(
                            "A recent deployment is temporally correlated with the alert, so deployment regression remains a competing explanation."
                    );
                }
            }
            case "pod_oom_killed" -> {
                boolean hasOomEvidence = evidenceTypes.stream()
                        .anyMatch(t -> t.contains("oom") || t.contains("OOM"));
                boolean hasMemoryPressure = evidenceTypes.stream()
                        .anyMatch(t -> t.contains("memory") && !t.contains("_no_signal"));
                boolean hasRestartEvidence = evidenceTypes.stream()
                        .anyMatch(t -> t.contains("restart") && !t.contains("_no_signal"));
                if (!hasOomEvidence && !hasMemoryPressure && !hasRestartEvidence) {
                    contradictions.add(
                            "No OOMKilled, memory pressure, or restart evidence was found."
                    );
                }
            }
            case "pod_crash_loop" -> {
                boolean hasCrashLoopBackoff = evidenceTypes.contains("container_crash_loop_backoff");
                boolean hasRestartEvidence = evidenceTypes.stream()
                        .anyMatch(t -> t.equals("pod_restart_count_increased"));
                boolean hasNotReady = evidenceTypes.stream()
                        .anyMatch(t -> t.equals("pod_not_ready"));
                // If we have restart/not_ready but NO actual CrashLoopBackOff state,
                // the pod is restarting for a different reason (e.g. latency-induced probe failure).
                // This should not strongly support crash_loop.
                if (!hasCrashLoopBackoff) {
                    contradictions.add(
                            "No CrashLoopBackOff state detected. Pod restarts may be caused by probe failures or other non-crash issues."
                    );
                }
            }
        }

        return contradictions;
    }

    private String buildExplanation(
            Hypothesis hypothesis,
            DiagnosticPattern pattern,
            List<String> supportingIds,
            List<String> counterIds,
            List<String> missing,
            List<String> contradictions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hypothesis [").append(hypothesis.title()).append("]: ");
        sb.append(supportingIds.size()).append(" supporting, ");
        sb.append(counterIds.size()).append(" counter");
        if (!missing.isEmpty()) {
            sb.append(", ").append(missing.size()).append(" missing");
        }
        if (!contradictions.isEmpty()) {
            sb.append(", ").append(contradictions.size()).append(" contradiction(s)");
        }
        return sb.toString();
    }
}
