package ai.sreagent.llm.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    @Test
    void returnsDeterministicContent() {
        MockLlmClient client = new MockLlmClient();
        LlmRequest request = new LlmRequest("system", "user prompt with decision info");
        LlmResponse response = client.complete(request);

        assertThat(response.content()).isNotBlank();
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.mock()).isTrue();
    }

    @Test
    void contentMentionsCompetingHypotheses() {
        MockLlmClient client = new MockLlmClient();
        LlmResponse response = client.complete(new LlmRequest("system", "user"));

        assertThat(response.content()).contains("competing_hypotheses");
    }

    @Test
    void contentMentionsScores() {
        MockLlmClient client = new MockLlmClient();
        LlmResponse response = client.complete(new LlmRequest("system", "user"));

        assertThat(response.content()).contains("0.64");
        assertThat(response.content()).contains("0.58");
    }

    @Test
    void contentMentionsAdvisory() {
        MockLlmClient client = new MockLlmClient();
        LlmResponse response = client.complete(new LlmRequest("system", "user"));

        assertThat(response.content()).containsIgnoringCase("advisory");
    }

    @Test
    void contentMentionsScoreGap() {
        MockLlmClient client = new MockLlmClient();
        LlmResponse response = client.complete(new LlmRequest("system", "user"));

        assertThat(response.content()).contains("0.06");
    }

    @Test
    void contentDoesNotForceSingleRca() {
        MockLlmClient client = new MockLlmClient();
        LlmResponse response = client.complete(new LlmRequest("system", "user"));

        assertThat(response.content()).containsIgnoringCase("should not be treated as a single definitive RCA");
    }
}
