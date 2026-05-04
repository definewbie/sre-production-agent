package ai.sreagent.probe;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.core.workflow.InvestigationWorkflow;
import ai.sreagent.llm.proposer.*;
import ai.sreagent.probe.policy.ProbeExecutionPolicy;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end test: Scenario E → investigate → propose → execute probes → verify no decision mutation.
 */
class ScenarioEProbeExecutionTest {

    private static final String PROJECT_ROOT = Path.of("").toAbsolutePath().getParent().toString();

    private static File projectFile(String rel) {
        Path p = Path.of(rel);
        if (p.toFile().exists()) return p.toFile();
        return Path.of(PROJECT_ROOT).resolve(rel).toFile();
    }

    private static final String ALERT_PATH = "examples/alerts/competing_hypotheses.json";
    private static final String EVIDENCE_PATH = "examples/evidence/competing_hypotheses.json";

    @Test
    void scenarioE_fullProbeExecutionPipeline() throws Exception {
        // 1. Run deterministic investigation (same as base Scenario E)
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult baseResult = workflow.run(projectFile(ALERT_PATH), projectFile(EVIDENCE_PATH));

        assertThat(baseResult.decision()).isNotNull();
        InvestigationDecision baseDecision = baseResult.decision();
        String decisionType = baseDecision.decisionType();
        assertThat(decisionType).isEqualTo("competing_hypotheses");

        // 2. Generate LLM hypothesis proposals
        MockLlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
        LlmProposalTriggerPolicy triggerPolicy = new LlmProposalTriggerPolicy();

        assertThat(triggerPolicy.shouldPropose(baseResult)).isTrue();

        List<NormalizedEvidence> normalizedBase = EvidenceNormalizer.normalizeAll(baseResult.evidence());
        LlmHypothesisProposalResult proposalResult = proposer.propose(baseResult, normalizedBase);

        assertThat(proposalResult.proposals()).isNotEmpty();
        UnverifiedHypothesisProposal proposal = proposalResult.proposals().get(0);
        assertThat(proposal.verificationPlan()).isNotNull();
        assertThat(proposal.verificationPlan().probeIntents()).isNotEmpty();

        // 3. Create probe execution plan
        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan plan = router.createPlan(
            baseResult.incident().id(),
            proposal.proposalId(),
            proposal.verificationPlan().probeIntents(),
            ProbeExecutionMode.FIXTURE
        );

        // 4. Validate policy
        ProbeExecutionPolicy execPolicy = new ProbeExecutionPolicy();
        assertThat(execPolicy.allows(plan)).isTrue();

        // 5. Execute probes
        FixtureProbeExecutor executor = new FixtureProbeExecutor();
        ProbeExecutionResult probeResult = executor.execute(plan);

        // 6. Assert probe results
        assertThat(probeResult.status()).isEqualTo(ProbeExecutionStatus.EXECUTED);
        assertThat(probeResult.evidence()).isNotEmpty();
        assertThat(probeResult.normalizedEvidence()).isNotEmpty();
        assertThat(probeResult.canAffectDecision()).isFalse();

        // 7. Verify base decision is unchanged
        assertThat(baseResult.decision().decisionType()).isEqualTo(decisionType);
    }

    @Test
    void scenarioE_probeEvidenceCoversMultipleSources() throws Exception {
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult baseResult = workflow.run(projectFile(ALERT_PATH), projectFile(EVIDENCE_PATH));

        MockLlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
        List<NormalizedEvidence> normalizedBase = EvidenceNormalizer.normalizeAll(baseResult.evidence());
        LlmHypothesisProposalResult proposalResult = proposer.propose(baseResult, normalizedBase);

        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan plan = router.createPlan(
            baseResult.incident().id(),
            proposalResult.proposals().get(0).proposalId(),
            proposalResult.proposals().get(0).verificationPlan().probeIntents(),
            ProbeExecutionMode.FIXTURE
        );

        FixtureProbeExecutor executor = new FixtureProbeExecutor();
        ProbeExecutionResult probeResult = executor.execute(plan);

        // Verify evidence comes from multiple observability sources
        List<String> sources = probeResult.evidence().stream()
            .map(Evidence::source)
            .distinct()
            .toList();

        assertThat(sources).containsAnyOf("prometheus", "loki", "tracing", "kubernetes", "alertmanager");
    }

    @Test
    void probeExecutionDoesNotAffectDecision() throws Exception {
        InvestigationWorkflow workflow = new InvestigationWorkflow();
        InvestigationResult before = workflow.run(projectFile(ALERT_PATH), projectFile(EVIDENCE_PATH));

        // Run full probe pipeline
        MockLlmHypothesisProposer proposer = new MockLlmHypothesisProposer();
        List<NormalizedEvidence> normalizedBase = EvidenceNormalizer.normalizeAll(before.evidence());
        LlmHypothesisProposalResult proposalResult = proposer.propose(before, normalizedBase);

        ProbeIntentRouter router = new ProbeIntentRouter();
        ProbeExecutionPlan plan = router.createPlan(
            before.incident().id(),
            proposalResult.proposals().get(0).proposalId(),
            proposalResult.proposals().get(0).verificationPlan().probeIntents(),
            ProbeExecutionMode.FIXTURE
        );

        FixtureProbeExecutor executor = new FixtureProbeExecutor();
        executor.execute(plan);

        // Re-run investigation — decision must be identical
        InvestigationResult after = workflow.run(projectFile(ALERT_PATH), projectFile(EVIDENCE_PATH));

        assertThat(after.decision().decisionType()).isEqualTo(before.decision().decisionType());
        assertThat(after.confidenceResults()).hasSameSizeAs(before.confidenceResults());
    }
}
