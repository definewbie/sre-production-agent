package ai.sreagent.llm.client;

/**
 * Client interface for LLM completion.
 * Implementations: MockLlmClient (deterministic), OpenAiCompatibleClient (optional future).
 */
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
