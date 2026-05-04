package ai.sreagent.llm.proposer;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class LlmProposalTriggerPolicyTest {

    private final LlmProposalTriggerPolicy policy = new LlmProposalTriggerPolicy();

    @Test
    @DisplayName("triggers on competing_hypotheses")
    void triggersOnCompetingHypotheses() {
        var result = ProposerTestHelper.scenarioEResult();
        assertThat(policy.shouldPropose(result)).isTrue();
    }

    @Test
    @DisplayName("triggers on uncertain_requires_more_evidence")
    void triggersOnUncertain() {
        var result = ProposerTestHelper.buildResult("uncertain_requires_more_evidence", 0.50, 0.20);
        assertThat(policy.shouldPropose(result)).isTrue();
    }

    @Test
    @DisplayName("triggers on insufficient_evidence")
    void triggersOnInsufficient() {
        var result = ProposerTestHelper.buildResult("insufficient_evidence", 0.30, 0.10);
        assertThat(policy.shouldPropose(result)).isTrue();
    }

    @Test
    @DisplayName("triggers on low confidence < 0.60")
    void triggersOnLowConfidence() {
        var result = ProposerTestHelper.buildResult("likely_root_cause", 0.55, 0.20);
        assertThat(policy.shouldPropose(result)).isTrue();
    }

    @Test
    @DisplayName("triggers on small score gap < 0.10")
    void triggersOnSmallGap() {
        var result = ProposerTestHelper.buildResult("likely_root_cause", 0.75, 0.08);
        assertThat(policy.shouldPropose(result)).isTrue();
    }

    @Test
    @DisplayName("does NOT trigger on likely_root_cause with confidence >= 0.80 and gap >= 0.15")
    void noTriggerOnClearRca() {
        var result = ProposerTestHelper.scenarioFResult();
        assertThat(policy.shouldPropose(result)).isFalse();
    }

    @Test
    @DisplayName("returns false on null result")
    void nullResult() {
        assertThat(policy.shouldPropose(null)).isFalse();
    }

    @Test
    @DisplayName("returns false on null decision")
    void nullDecision() {
        var result = ProposerTestHelper.buildResultWithNullDecision();
        assertThat(policy.shouldPropose(result)).isFalse();
    }
}
