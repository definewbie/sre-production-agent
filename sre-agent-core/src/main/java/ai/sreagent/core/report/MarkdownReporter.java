package ai.sreagent.core.report;

import ai.sreagent.core.domain.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a deterministic Markdown RCA report from investigation results.
 * Zero external dependencies beyond domain objects.
 */
public class MarkdownReporter {

    public String generate(
            IncidentTask incident,
            List<Hypothesis> hypotheses,
            List<VerificationResult> verificationResults,
            List<ConfidenceResult> confidenceResults,
            HypothesisComparison comparison,
            InvestigationDecision decision,
            List<Evidence> evidence) {

        StringBuilder sb = new StringBuilder();

        Map<String, VerificationResult> verifMap = verificationResults.stream()
                .collect(Collectors.toMap(VerificationResult::hypothesisId, v -> v));
        Map<String, ConfidenceResult> confMap = confidenceResults.stream()
                .collect(Collectors.toMap(ConfidenceResult::hypothesisId, c -> c));
        Map<String, Evidence> evidenceMap = evidence.stream()
                .collect(Collectors.toMap(Evidence::id, e -> e));

        // Title
        sb.append("# Competing Hypotheses Report: ")
                .append(incident.alertName())
                .append(" on ")
                .append(incident.service())
                .append("\n\n");

        // Decision
        sb.append("## Decision\n\n");
        sb.append("Decision: ").append(decision.decisionType()).append("\n");
        sb.append("Selected hypothesis: ").append(decision.selectedHypothesisId()).append("\n");
        if (!decision.competingHypotheses().isEmpty()) {
            sb.append("Competing hypothesis: ").append(String.join(", ", decision.competingHypotheses())).append("\n");
        }
        sb.append("Confidence score: ").append(formatScore(decision.confidenceScore())).append("\n");
        sb.append("Score gap: ").append(formatScore(comparison.scoreGap())).append("\n\n");

        // Summary
        sb.append("## Summary\n\n");
        sb.append(incident.service()).append(" triggered ").append(incident.alertName());
        sb.append(" at ").append(incident.startedAt()).append(".\n\n");
        sb.append(decision.rationale()).append("\n\n");

        // Hypothesis Scores
        sb.append("## Hypothesis Scores\n\n");
        sb.append("| Hypothesis | Score | Level | Decision |\n");
        sb.append("|---|---:|---|---|\n");
        confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .forEach(c -> sb.append(String.format("| %s | %.2f | %s | %s |\n",
                        c.hypothesisId(), c.score(), c.level(), c.decision())));
        sb.append("\n");

        // Leading Hypothesis
        sb.append("## Leading Hypothesis\n\n");
        sb.append(comparison.leadingHypothesisId()).append("\n\n");

        // Competing Hypotheses
        if (!comparison.competingHypothesisIds().isEmpty()) {
            sb.append("## Competing Hypotheses\n\n");
            comparison.competingHypothesisIds().forEach(h ->
                    sb.append("- ").append(h).append("\n"));
            sb.append("\n");
        }

        // Why Leading Leads
        ConfidenceResult leadingConf = confMap.get(comparison.leadingHypothesisId());
        if (leadingConf != null) {
            sb.append("## Why ").append(hypothesisTitle(comparison.leadingHypothesisId()))
                    .append(" Leads\n\n");
            for (String factor : leadingConf.supportingFactors()) {
                sb.append("- ").append(factor).append("\n");
            }
            sb.append("\n");
        }

        // Why Competing Remains Plausible
        for (String compId : comparison.competingHypothesisIds()) {
            ConfidenceResult compConf = confMap.get(compId);
            if (compConf != null) {
                sb.append("## Why ").append(hypothesisTitle(compId))
                        .append(" Remains Plausible\n\n");
                for (String factor : compConf.supportingFactors()) {
                    sb.append("- ").append(factor).append("\n");
                }
                sb.append("\n");
            }
        }

        // Counter Evidence
        sb.append("## Counter Evidence\n\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            if (!c.counterFactors().isEmpty()) {
                sb.append("### Against ").append(c.hypothesisId()).append("\n\n");
                for (String factor : c.counterFactors()) {
                    sb.append("- ").append(factor).append("\n");
                }
                sb.append("\n");
            }
        }

        // Contradictions
        sb.append("## Contradictions\n\n");
        for (ConfidenceResult c : confidenceResults.stream()
                .sorted(Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .toList()) {
            VerificationResult vr = verifMap.get(c.hypothesisId());
            if (vr != null && !vr.contradictions().isEmpty()) {
                for (String contra : vr.contradictions()) {
                    sb.append("- ").append(contra).append("\n");
                }
            }
        }
        sb.append("\n");

        // Suggested Next Probes
        sb.append("## Suggested Next Probes\n\n");
        if (!decision.nextProbes().isEmpty()) {
            for (int i = 0; i < decision.nextProbes().size(); i++) {
                sb.append(i + 1).append(". ").append(decision.nextProbes().get(i)).append("\n");
            }
        }
        sb.append("\n");

        // Calibration Notes
        sb.append("## Calibration Notes\n\n");
        ConfidenceResult firstConf = confidenceResults.stream()
                .findFirst().orElse(null);
        if (firstConf != null && firstConf.calibrationNotes() != null) {
            sb.append(firstConf.calibrationNotes()).append("\n\n");
        }

        // Event Trace Note
        sb.append("## Event Trace Note\n\n");
        sb.append("Run the CLI with --show-trace to inspect the investigation path.\n");

        return sb.toString();
    }

    private String formatScore(double score) {
        return String.format("%.2f", score);
    }

    private String hypothesisTitle(String hypothesisId) {
        return hypothesisId.replace("hyp_", "").replace("_", " ");
    }
}
