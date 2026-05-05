package ai.sreagent.llm.proposer;

import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmClient;
import ai.sreagent.llm.client.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Real LLM-backed hypothesis proposer.
 * Calls LLM via LlmClient and parses JSON proposals.
 * Falls back to MockLlmHypothesisProposer on any error.
 */
public class LlmHypothesisProposerImpl implements LlmHypothesisProposer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LlmClient llmClient;
    private final LlmHypothesisProposalPromptBuilder promptBuilder;

    public LlmHypothesisProposerImpl(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.promptBuilder = new LlmHypothesisProposalPromptBuilder();
    }

    @Override
    public LlmHypothesisProposalResult propose(
            InvestigationResult result,
            List<NormalizedEvidence> normalizedEvidence
    ) {
        try {
            LlmRequest request = promptBuilder.build(result, normalizedEvidence);
            var response = llmClient.complete(request);

            return parseProposals(result, response.content());
        } catch (Exception e) {
            // Fallback to mock on any LLM error
            return new MockLlmHypothesisProposer().propose(result, normalizedEvidence);
        }
    }

    @Override
    public String proposerName() {
        return "llm-hypothesis-proposer(" + llmClient.getClass().getSimpleName() + ")";
    }

    private LlmHypothesisProposalResult parseProposals(
            InvestigationResult result, String llmOutput
    ) {
        String incidentId = result.incidentId();
        String decisionType = result.decision().decisionType();
        double topScore = result.decision().confidenceScore();
        double scoreGap = result.comparison() != null ? result.comparison().scoreGap() : 1.0;

        List<UnverifiedHypothesisProposal> proposals = new ArrayList<>();

        // Try to extract JSON array from LLM output
        try {
            String json = extractJson(llmOutput);
            if (json != null) {
                JsonNode root = MAPPER.readTree(json);
                JsonNode arr = root.isArray() ? root : (root.has("proposals") ? root.get("proposals") : null);
                if (arr != null && arr.isArray()) {
                    for (JsonNode node : arr) {
                        proposals.add(parseOneProposal(node));
                    }
                }
            }
        } catch (Exception e) {
            // JSON parse failed — proposals stays empty
        }

        boolean advisoryOnly = proposals.isEmpty();

        // If no valid proposals parsed, include LLM raw text as a single "text proposal"
        if (proposals.isEmpty()) {
            proposals.add(new UnverifiedHypothesisProposal(
                    "llm_text_fallback",
                    "LLM 分析建议（文本）",
                    "llm_text_analysis",
                    result.incident() != null ? result.incident().service() : "unknown",
                    llmOutput.length() > 500 ? llmOutput.substring(0, 500) : llmOutput,
                    "由 LLM 直接生成的分析文本",
                    List.of(),
                    new VerificationPlan(List.of(), List.of(), List.of(), List.of()),
                    0.10,
                    ProposalStatus.UNVERIFIED_PROPOSAL,
                    false
            ));
            advisoryOnly = true;
        }

        return new LlmHypothesisProposalResult(
                incidentId, decisionType,
                result.decision().selectedHypothesisId(),
                topScore, scoreGap,
                List.copyOf(proposals), advisoryOnly,
                llmClient.getClass().getSimpleName()
        );
    }

    private UnverifiedHypothesisProposal parseOneProposal(JsonNode node) {
        return new UnverifiedHypothesisProposal(
                node.path("proposalId").asText("llm_prop_" + System.nanoTime()),
                node.path("title").asText("未命名假设"),
                node.path("rootCauseType").asText("unknown"),
                node.path("affectedService").asText("unknown"),
                node.path("candidateCause").asText(""),
                node.path("reasoning").asText(""),
                toStringList(node.path("supportingSignals")),
                new VerificationPlan(
                        toStringList(node.path("verificationPlan").path("requiredEvidence")),
                        toStringList(node.path("verificationPlan").path("missingEvidence")),
                        toStringList(node.path("verificationPlan").path("counterEvidenceToCheck")),
                        List.of()
                ),
                Math.min(node.path("priorConfidence").asDouble(0.20), 0.50),
                ProposalStatus.UNVERIFIED_PROPOSAL,
                false
        );
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return result;
    }

    /**
     * Extract JSON from LLM output — may be wrapped in markdown code block.
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) return null;
        // Try to find ```json ... ``` block
        int start = text.indexOf("```json");
        if (start >= 0) {
            int end = text.indexOf("```", start + 7);
            if (end > start) return text.substring(start + 7, end).trim();
        }
        // Try raw JSON array
        int arrStart = text.indexOf('[');
        int objStart = text.indexOf('{');
        if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
            int end = text.lastIndexOf(']');
            if (end > arrStart) return text.substring(arrStart, end + 1);
        }
        if (objStart >= 0) {
            int end = text.lastIndexOf('}');
            if (end > objStart) return text.substring(objStart, end + 1);
        }
        return null;
    }
}
