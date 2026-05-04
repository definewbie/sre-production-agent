package ai.sreagent.llm.proposer;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MockLlmHypothesisProposerTest {

    private MockLlmHypothesisProposer proposer;

    @BeforeEach
    void setUp() {
        proposer = new MockLlmHypothesisProposer();
    }

    @Test
    @DisplayName("proposerName returns mock identifier")
    void proposerName() {
        assertThat(proposer.proposerName()).isEqualTo("mock-llm-hypothesis-proposer");
    }

    @Test
    @DisplayName("competing_hypotheses triggers 1 proposal")
    void competingHypotheses() {
        var result = ProposerTestHelper.scenarioEResult();
        var proposalResult = proposer.propose(result, ProposerTestHelper.normalizedEvidence());

        assertThat(proposalResult.proposals()).hasSize(1);
        assertThat(proposalResult.advisoryOnly()).isFalse();
        assertThat(proposalResult.modelProvider()).isEqualTo("mock");

        var proposal = proposalResult.proposals().get(0);
        assertThat(proposal.proposalId()).isEqualTo("llm_prop_deployment_timeout_amplification");
        assertThat(proposal.status()).isEqualTo(ProposalStatus.UNVERIFIED_PROPOSAL);
        assertThat(proposal.canAffectDecision()).isFalse();
        assertThat(proposal.rootCauseType()).isEqualTo("deployment_downstream_amplification_loop");
        assertThat(proposal.priorConfidence()).isBetween(0.0, 0.5);
    }

    @Test
    @DisplayName("likely_root_cause with high confidence produces 0 proposals")
    void likelyRootCauseNoProposal() {
        var result = ProposerTestHelper.scenarioFResult();
        var proposalResult = proposer.propose(result, List.of());

        assertThat(proposalResult.proposals()).isEmpty();
        assertThat(proposalResult.advisoryOnly()).isTrue();
    }

    @Test
    @DisplayName("proposal includes probe intents for all observability pillars")
    void proposalIncludesProbeIntents() {
        var result = ProposerTestHelper.scenarioEResult();
        var proposalResult = proposer.propose(result, ProposerTestHelper.normalizedEvidence());
        var proposal = proposalResult.proposals().get(0);

        List<ProbeType> probeTypes = proposal.verificationPlan().probeIntents().stream()
            .map(ProbeIntent::probeType)
            .toList();

        assertThat(probeTypes).contains(
            ProbeType.PROMETHEUS_QUERY,
            ProbeType.LOKI_QUERY,
            ProbeType.TRACE_QUERY,
            ProbeType.KUBERNETES_QUERY
        );
    }

    @Test
    @DisplayName("proposal base fields match deterministic RCA result")
    void baseFieldsMatch() {
        var result = ProposerTestHelper.scenarioEResult();
        var proposalResult = proposer.propose(result, ProposerTestHelper.normalizedEvidence());

        assertThat(proposalResult.baseDecisionType()).isEqualTo(result.decision().decisionType());
        assertThat(proposalResult.baseSelectedHypothesisId()).isEqualTo(result.decision().selectedHypothesisId());
        assertThat(proposalResult.incidentId()).isEqualTo(result.incidentId());
    }

    @Test
    @DisplayName("proposal has non-empty supporting signals")
    void supportingSignals() {
        var result = ProposerTestHelper.scenarioEResult();
        var proposalResult = proposer.propose(result, ProposerTestHelper.normalizedEvidence());
        var proposal = proposalResult.proposals().get(0);

        assertThat(proposal.supportingSignals()).isNotEmpty();
    }

    @Test
    @DisplayName("verification plan has required and missing evidence")
    void verificationPlanContent() {
        var result = ProposerTestHelper.scenarioEResult();
        var proposalResult = proposer.propose(result, ProposerTestHelper.normalizedEvidence());
        var plan = proposalResult.proposals().get(0).verificationPlan();

        assertThat(plan.requiredEvidence()).isNotEmpty();
        assertThat(plan.missingEvidence()).isNotEmpty();
        assertThat(plan.counterEvidenceToCheck()).isNotEmpty();
    }
}
