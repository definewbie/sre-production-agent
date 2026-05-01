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
                .collect(Collectors.toSet());

        List<String> supportingIds = evidence.stream()
                .filter(e -> pattern.supportingEvidenceTypes().contains(e.evidenceType()))
                .map(Evidence::id)
                .toList();

        List<String> counterIds = evidence.stream()
                .filter(e -> pattern.counterEvidenceTypes().contains(e.evidenceType()))
                .map(Evidence::id)
                .toList();

        Set<String> coveredTypes = new HashSet<>();
        evidence.forEach(e -> coveredTypes.add(e.evidenceType()));

        List<String> missingEvidence = pattern.evidenceRequirements().stream()
                .filter(req -> !coveredTypes.contains(req))
                .toList();

        // Also check: any supporting evidence types that have zero coverage
        List<String> uncoveredSupportingTypes = pattern.supportingEvidenceTypes().stream()
                .filter(type -> !evidenceTypes.contains(type))
                .map(type -> "Missing expected evidence type: " + type)
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

    private List<String> detectContradictions(String patternId, Set<String> evidenceTypes) {
        List<String> contradictions = new ArrayList<>();

        switch (patternId) {
            case "deployment_regression" -> {
                if (evidenceTypes.contains("historical_timeout_logs_present")) {
                    contradictions.add(
                            "Timeout logs existed before the deployment, so the deployment may not be the only cause."
                    );
                }
                if (evidenceTypes.contains("downstream_latency_spike")) {
                    contradictions.add(
                            "Downstream payment-service latency also increased, so dependency latency remains a competing explanation."
                    );
                }
            }
            case "downstream_dependency_latency" -> {
                if (evidenceTypes.contains("downstream_5xx_absent")) {
                    contradictions.add(
                            "payment-service 5xx did not increase, so downstream failure is not fully confirmed."
                    );
                }
                if (evidenceTypes.contains("deploy_event_near_alert_window")) {
                    contradictions.add(
                            "A recent deployment is temporally correlated with the alert, so deployment regression remains a competing explanation."
                    );
                }
            }
            case "pod_oom_killed" -> {
                boolean hasOomEvidence = evidenceTypes.stream()
                        .anyMatch(t -> t.contains("oom") || t.contains("restart") || t.contains("memory"));
                if (!hasOomEvidence) {
                    contradictions.add(
                            "No OOMKilled, restart, or memory pressure evidence was found."
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
