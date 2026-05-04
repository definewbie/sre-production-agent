package ai.sreagent.server.service;

import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.llm.proposer.*;
import ai.sreagent.probe.*;
import ai.sreagent.probe.policy.ProbeExecutionPolicy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Service for probe execution (Step S).
 * Orchestrates: investigate → propose → plan → execute.
 * All operations use fixture mode — no live backend.
 */
@Service
public class ProbeExecutionService {

    private static final String ALERT_PATH = "examples/alerts/competing_hypotheses.json";
    private static final String EVIDENCE_PATH = "examples/evidence/competing_hypotheses.json";

    public ProbeExecutionResult proposeAndExecuteScenarioE() throws Exception {
        // 1. Run deterministic investigation
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult result = workflow.run(
                resolveProjectFile(ALERT_PATH), resolveProjectFile(EVIDENCE_PATH));

        // 2. Normalize evidence
        List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(result.evidence());

        // 3. Generate proposals
        MockLlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
        LlmHypothesisProposalResult proposalResult = proposer.propose(result, normalized);

        if (proposalResult.proposals().isEmpty()) {
            return new ProbeExecutionResult(
                result.incident().id(),
                "none",
                ProbeExecutionStatus.SKIPPED_BY_POLICY,
                List.of(), List.of(), List.of(), List.of(),
                List.of("No proposals generated"),
                false
            );
        }

        // 4. Create plan for first proposal
        UnverifiedHypothesisProposal proposal = proposalResult.proposals().get(0);
        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan plan = router.createPlan(
            result.incident().id(),
            proposal.proposalId(),
            proposal.verificationPlan().probeIntents(),
            ProbeExecutionMode.FIXTURE
        );

        // 5. Execute probes
        FixtureProbeExecutor executor = new FixtureProbeExecutor();
        return executor.execute(plan);
    }

    private File resolveProjectFile(String relativePath) {
        File f = new File(relativePath);
        if (f.exists()) return f;
        f = new File("../" + relativePath);
        if (f.exists()) return f;
        f = new File("../../" + relativePath);
        if (f.exists()) return f;
        throw new RuntimeException("Cannot find file: " + relativePath);
    }
}
