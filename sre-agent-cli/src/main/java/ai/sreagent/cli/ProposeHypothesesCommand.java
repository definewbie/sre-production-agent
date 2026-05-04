package ai.sreagent.cli;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.evidence.NormalizedEvidenceView;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.llm.proposer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: propose-hypotheses
 * Runs deterministic investigation, normalizes evidence, generates LLM hypothesis proposals.
 */
@CommandLine.Command(
    name = "propose-hypotheses",
    description = "Generate LLM hypothesis proposals based on deterministic RCA result"
)
public class ProposeHypothesesCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--alert", required = true, description = "Alert JSON file path")
    private String alertPath;

    @CommandLine.Option(names = "--evidence", required = true, description = "Evidence JSON file path")
    private String evidencePath;

    @CommandLine.Option(names = "--output", required = true, description = "Output file path")
    private String outputPath;

    @CommandLine.Option(names = "--proposer", defaultValue = "mock", description = "Proposer implementation: mock")
    private String proposerType;

    @Override
    public Integer call() throws Exception {
        // 1. Run deterministic investigation
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.run(new File(alertPath), new File(evidencePath));

        // 2. Normalize evidence
        List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(result.evidence());

        // 3. Generate proposals
        LlmHypothesisProposer proposer = createProposer();
        LlmHypothesisProposalResult proposalResult = proposer.propose(result, normalized);

        // 4. Apply guardrails
        ProposalGuardrail guardrail = new ProposalGuardrail();
        List<UnverifiedHypothesisProposal> validated = proposalResult.proposals().stream()
            .map(guardrail::validate)
            .toList();

        // Rebuild result with validated proposals
        LlmHypothesisProposalResult finalResult = new LlmHypothesisProposalResult(
            proposalResult.incidentId(),
            proposalResult.baseDecisionType(),
            proposalResult.baseSelectedHypothesisId(),
            proposalResult.baseConfidenceScore(),
            proposalResult.baseScoreGap(),
            validated,
            proposalResult.advisoryOnly(),
            proposalResult.modelProvider()
        );

        // 5. Write output
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();
        mapper.writeValue(outFile, finalResult);

        // 6. Print summary
        System.out.println("Hypothesis proposals generated");
        System.out.println("proposer: " + proposer.proposerName());
        System.out.println("base decision: " + finalResult.baseDecisionType());
        System.out.println("base confidence: " + String.format("%.2f", finalResult.baseConfidenceScore()));
        System.out.println("proposal count: " + validated.size());
        System.out.println("advisory only: " + finalResult.advisoryOnly());

        for (var p : validated) {
            System.out.println("proposal:");
            System.out.println("  - " + p.proposalId());
            System.out.println("    status: " + p.status());
            System.out.println("    canAffectDecision: " + p.canAffectDecision());
            if (p.verificationPlan() != null && p.verificationPlan().probeIntents() != null) {
                System.out.println("    probe intents:");
                for (var pi : p.verificationPlan().probeIntents()) {
                    System.out.println("    - " + pi.probeType());
                }
            }
        }

        System.out.println("output: " + outputPath);
        return 0;
    }

    private LlmHypothesisProposer createProposer() {
        return switch (proposerType) {
            case "mock" -> new MockLlmHypothesisProposer();
            default -> throw new IllegalArgumentException("Unknown proposer type: " + proposerType);
        };
    }
}
