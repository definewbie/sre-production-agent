package ai.sreagent.llm.proposer;

import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProposalGuardrailTest {

    private final ProposalGuardrail guardrail = new ProposalGuardrail();

    private UnverifiedHypothesisProposal validProposal() {
        return new UnverifiedHypothesisProposal(
            "test_proposal", "Test title", "test_cause", "svc",
            "test candidate", "test reasoning",
            List.of("signal_a"),
            new VerificationPlan(
                List.of("timeout config"),
                List.of("retry logs"),
                List.of("independent error"),
                List.of(new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "svc", "check", "metric", "why"))
            ),
            0.35, ProposalStatus.UNVERIFIED_PROPOSAL, false
        );
    }

    @Test
    @DisplayName("valid proposal passes guardrail")
    void validProposalPasses() {
        var result = guardrail.validate(validProposal());
        assertThat(result.status()).isEqualTo(ProposalStatus.UNVERIFIED_PROPOSAL);
        assertThat(result.canAffectDecision()).isFalse();
    }

    @Test
    @DisplayName("isValid returns true for valid proposal")
    void isValidTrue() {
        assertThat(guardrail.isValid(validProposal())).isTrue();
    }

    @Test
    @DisplayName("canAffectDecision=true is corrected and rejected")
    void canAffectDecisionRejected() {
        var proposal = new UnverifiedHypothesisProposal(
            "bad_prop", "title", "cause", "svc",
            "candidate", "reasoning",
            List.of("signal"),
            new VerificationPlan(List.of("evidence"), null, null,
                List.of(new ProbeIntent(ProbeType.LOKI_QUERY, "svc", "svc", "q", "t", "r"))),
            0.30, ProposalStatus.UNVERIFIED_PROPOSAL, true
        );
        var result = guardrail.validate(proposal);
        assertThat(result.canAffectDecision()).isFalse();
        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED_BY_GUARDRAIL);
    }

    @Test
    @DisplayName("priorConfidence > 0.5 is capped to 0.5")
    void highPriorConfidenceCapped() {
        var proposal = new UnverifiedHypothesisProposal(
            "high_conf", "title", "cause", "svc",
            "candidate", "reasoning",
            List.of("signal"),
            new VerificationPlan(List.of("evidence"), null, null,
                List.of(new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "svc", "q", "t", "r"))),
            0.90, ProposalStatus.UNVERIFIED_PROPOSAL, false
        );
        var result = guardrail.validate(proposal);
        assertThat(result.priorConfidence()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("negative priorConfidence is capped to 0.0")
    void negativePriorConfidenceCapped() {
        var proposal = new UnverifiedHypothesisProposal(
            "neg_conf", "title", "cause", "svc",
            "candidate", "reasoning",
            List.of("signal"),
            new VerificationPlan(List.of("evidence"), null, null,
                List.of(new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "svc", "q", "t", "r"))),
            -0.5, ProposalStatus.UNVERIFIED_PROPOSAL, false
        );
        var result = guardrail.validate(proposal);
        assertThat(result.priorConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("proposal without verification plan is rejected")
    void noPlanRejected() {
        var proposal = new UnverifiedHypothesisProposal(
            "no_plan", "title", "cause", "svc",
            "candidate", "reasoning",
            List.of("signal"),
            new VerificationPlan(null, null, null, null),
            0.30, ProposalStatus.UNVERIFIED_PROPOSAL, false
        );
        var result = guardrail.validate(proposal);
        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED_BY_GUARDRAIL);
    }

    @Test
    @DisplayName("proposal claiming root cause confirmed is rejected")
    void claimsFinalRcaRejected() {
        var proposal = new UnverifiedHypothesisProposal(
            "claim_rca", "Root cause confirmed: X", "cause", "svc",
            "candidate", "reasoning with root cause confirmed statement",
            List.of("signal"),
            new VerificationPlan(List.of("evidence"), null, null,
                List.of(new ProbeIntent(ProbeType.PROMETHEUS_QUERY, "svc", "svc", "q", "t", "r"))),
            0.30, ProposalStatus.UNVERIFIED_PROPOSAL, false
        );
        var result = guardrail.validate(proposal);
        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED_BY_GUARDRAIL);
    }

    @Test
    @DisplayName("null proposal returns null")
    void nullProposal() {
        assertThat(guardrail.validate(null)).isNull();
    }

    @Test
    @DisplayName("isValid returns false for null")
    void isValidNull() {
        assertThat(guardrail.isValid(null)).isFalse();
    }
}
