package ai.sreagent.llm.proposer;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LlmHypothesisProposalPromptBuilderTest {

    private final LlmHypothesisProposalPromptBuilder builder = new LlmHypothesisProposalPromptBuilder();

    @Test
    @DisplayName("system prompt contains guardrail instructions")
    void systemPromptGuardrails() {
        String sys = builder.getSystemPrompt();
        assertThat(sys).contains("LLM 提议，验证裁决");
        assertThat(sys).contains("你不得决定最终根因");
        assertThat(sys).contains("你不得更改 RCA 决策");
        assertThat(sys).contains("你不得更改置信度分数");
        assertThat(sys).contains("你不得编造证据");
        assertThat(sys).contains("你必须将所有提议标注为未验证");
    }

    @Test
    @DisplayName("user prompt contains incident summary for Scenario E")
    void userPromptScenarioE() {
        var result = ProposerTestHelper.scenarioEResult();
        var evidence = ProposerTestHelper.normalizedEvidence();
        var request = builder.build(result, evidence);

        String user = request.userPrompt();
        assertThat(user).contains("## Incident Summary");
        assertThat(user).contains("## Deterministic RCA Decision");
        assertThat(user).contains("competing_hypotheses");
        assertThat(user).contains("## Hypothesis Scores");
        assertThat(user).contains("## Score Gap");
        assertThat(user).contains("## Normalized Evidence");
        assertThat(user).contains("## Verification Summary");
        assertThat(user).contains("## Required Output");
    }

    @Test
    @DisplayName("user prompt contains evidence category and signal")
    void userPromptEvidenceDetails() {
        var result = ProposerTestHelper.scenarioEResult();
        var evidence = ProposerTestHelper.normalizedEvidence();
        var request = builder.build(result, evidence);

        String user = request.userPrompt();
        assertThat(user).contains("signal=");
        assertThat(user).contains("causalRole=");
    }

    @Test
    @DisplayName("user prompt handles empty evidence")
    void userPromptEmptyEvidence() {
        var result = ProposerTestHelper.scenarioEResult();
        var request = builder.build(result, List.of());

        String user = request.userPrompt();
        assertThat(user).contains("(no normalized evidence)");
    }
}
