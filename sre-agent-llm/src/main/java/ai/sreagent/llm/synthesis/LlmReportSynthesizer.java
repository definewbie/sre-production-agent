package ai.sreagent.llm.synthesis;

import ai.sreagent.core.domain.ConfidenceResult;
import ai.sreagent.core.domain.InvestigationDecision;
import ai.sreagent.core.domain.HypothesisComparison;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.LlmRequest;
import ai.sreagent.llm.client.LlmResponse;
import ai.sreagent.llm.model.LlmEnhancedReport;
import ai.sreagent.llm.prompt.LlmPromptBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes an LLM-enhanced RCA report from a deterministic InvestigationResult.
 *
 * Key guardrail: deterministic fields (baseDecisionType, baseSelectedHypothesisId,
 * baseConfidenceScore, baseScoreGap) are always taken from InvestigationResult,
 * never from LLM output. LLM output is advisory only.
 */
public class LlmReportSynthesizer {

    private static final String DEFAULT_EVIDENCE_SCOPE_NOTE =
            "This explanation is based only on verified structured evidence currently available to the workflow. " +
            "Future evidence providers may include K8s, EC2, RDS, ElastiCache, ALB, CMDB, and service topology.";

    private final LlmClient client;
    private final LlmPromptBuilder promptBuilder;

    public LlmReportSynthesizer(LlmClient client) {
        this.client = client;
        this.promptBuilder = new LlmPromptBuilder();
    }

    public LlmEnhancedReport synthesize(InvestigationResult result) {
        // 1. Build prompt from deterministic result
        LlmRequest request = promptBuilder.build(result);

        // 2. Call LLM client
        LlmResponse response = client.complete(request);

        // 3. Parse sections from response
        String content = response.content();
        String executiveSummary = extractSection(content, "Executive Summary");
        String reasoningNarrative = extractSection(content, "Reasoning Narrative");
        String uncertaintyExplanation = extractSection(content, "Uncertainty Explanation");
        String nextStepsExplanation = extractSection(content, "Next Steps");
        String limitations = extractSection(content, "Limitations");
        List<String> unverifiedProposals = extractProposals(content);

        // 4. Build report — deterministic fields from InvestigationResult, NOT from LLM
        InvestigationDecision decision = result.decision();
        HypothesisComparison comparison = result.comparison();

        return new LlmEnhancedReport(
                result.incidentId(),
                decision.decisionType(),                    // from deterministic result
                decision.selectedHypothesisId(),            // from deterministic result
                decision.confidenceScore(),                 // from deterministic result
                comparison.scoreGap(),                      // from deterministic result
                executiveSummary,
                reasoningNarrative,
                uncertaintyExplanation,
                nextStepsExplanation,
                limitations,
                unverifiedProposals,
                DEFAULT_EVIDENCE_SCOPE_NOTE,
                response.provider(),
                true                                        // always advisory
        );
    }

    /**
     * Extract a section from LLM output by heading.
     * Sections are marked as "## Heading" in markdown.
     */
    private String extractSection(String content, String heading) {
        // Try ## heading first, then # heading
        Pattern pattern = Pattern.compile(
                "##\\s+" + Pattern.quote(heading) + "\\s*\\n(.*?)(?=\\n##|\\Z)",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * Extract unverified proposals as a list.
     */
    private List<String> extractProposals(String content) {
        String section = extractSection(content, "Unverified Proposals");
        if (section.isEmpty()) {
            return List.of();
        }
        List<String> proposals = new ArrayList<>();
        for (String line : section.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                proposals.add(trimmed.substring(2).trim());
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                proposals.add(trimmed.replaceFirst("^\\d+\\.\\s+", "").trim());
            }
        }
        return proposals;
    }
}
