package ai.sreagent.llm.client;

import java.util.Map;

/**
 * Request to an LLM completion endpoint.
 */
public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        Map<String, Object> metadata
) {
    public LlmRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, Map.of());
    }
}
