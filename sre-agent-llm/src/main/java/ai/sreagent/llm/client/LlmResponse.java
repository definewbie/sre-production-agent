package ai.sreagent.llm.client;

/**
 * Response from an LLM completion endpoint.
 */
public record LlmResponse(
        String content,
        String provider,
        boolean mock
) {}
