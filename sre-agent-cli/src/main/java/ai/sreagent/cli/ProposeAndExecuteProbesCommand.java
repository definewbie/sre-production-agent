package ai.sreagent.cli;

import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.llm.proposer.*;
import ai.sreagent.probe.*;
import ai.sreagent.probe.policy.ProbeExecutionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: propose-and-execute-probes
 * Runs deterministic investigation → generates proposals → executes probes in fixture mode.
 */
@CommandLine.Command(
    name = "propose-and-execute-probes",
    description = "Generate LLM hypothesis proposals and execute probes (fixture mode by default)"
)
public class ProposeAndExecuteProbesCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--alert", required = true, description = "Alert JSON file path")
    private String alertPath;

    @CommandLine.Option(names = "--evidence", required = true, description = "Evidence JSON file path")
    private String evidencePath;

    @CommandLine.Option(names = "--output", required = true, description = "Output file path")
    private String outputPath;

    @CommandLine.Option(names = "--mode", defaultValue = "fixture", description = "Execution mode: fixture|mock|live")
    private String modeStr;

    @CommandLine.Option(names = "--proposer", defaultValue = "mock", description = "Proposer implementation: mock")
    private String proposerType;

    @Override
    public Integer call() throws Exception {
        ProbeExecutionMode mode = ProbeExecutionMode.valueOf(modeStr.toUpperCase());

        // 1. Run deterministic investigation
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.run(new File(alertPath), new File(evidencePath));

        // 2. Normalize evidence
        List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(result.evidence());

        // 3. Generate proposals
        LlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
        LlmHypothesisProposalResult proposalResult = proposer.propose(result, normalized);

        if (proposalResult.proposals().isEmpty()) {
            System.out.println("No hypothesis proposals generated.");
            System.out.println("base decision: " + result.decision().decisionType());
            return 0;
        }

        // 4. Take first proposal and create plan
        UnverifiedHypothesisProposal proposal = proposalResult.proposals().get(0);
        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan plan = router.createPlan(
            result.incident().id(),
            proposal.proposalId(),
            proposal.verificationPlan().probeIntents(),
            mode
        );

        // 5. Check policy
        ProbeExecutionPolicy policy = new ProbeExecutionPolicy();
        if (!policy.allows(plan)) {
            System.out.println("Probe execution blocked by policy.");
            System.out.println("mode: " + mode);
            return 1;
        }

        // 6. Execute probes
        ProbeExecutor executor = new FixtureProbeExecutor();
        ProbeExecutionResult probeResult = executor.execute(plan);

        // 7. Write output
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();
        mapper.writeValue(outFile, probeResult);

        // 8. Print summary
        System.out.println("Probe execution completed");
        System.out.println("mode: " + modeStr);
        System.out.println("proposal: " + proposal.proposalId());
        System.out.println("executed probes: " + probeResult.executedProbeIds().size());
        System.out.println("skipped probes: " + probeResult.skippedProbeIds().size());
        System.out.println("new evidence: " + probeResult.evidence().size());
        System.out.println("normalized evidence: " + probeResult.normalizedEvidence().size());
        System.out.println("can affect decision: " + probeResult.canAffectDecision());

        if (!probeResult.evidence().isEmpty()) {
            System.out.println("evidence types:");
            probeResult.evidence().stream()
                .map(e -> "- " + e.evidenceType())
                .distinct()
                .forEach(System.out::println);
        }

        System.out.println("output: " + outputPath);
        return 0;
    }
}
