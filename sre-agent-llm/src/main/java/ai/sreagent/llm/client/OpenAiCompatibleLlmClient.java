package ai.sreagent.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI-compatible LLM client.
 * Supports any API that follows the OpenAI Chat Completions format:
 * POST /v1/chat/completions with { model, messages, temperature }.
 * <p>
 * Configuration via environment variables:
 *   LLM_BASE_URL  — e.g. https://api.openai.com, https://openrouter.ai/api
 *   LLM_API_KEY   — Bearer token
 *   LLM_MODEL     — e.g. gpt-4o, deepseek/deepseek-chat-v3-0324
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        try {
            // Build OpenAI-compatible request body
            String body = MAPPER.writeValueAsString(Map.of(
                    "model", model,
                    "messages", java.util.List.of(
                            Map.of("role", "system", "content", request.systemPrompt()),
                            Map.of("role", "user", "content", request.userPrompt())
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2048
            ));

            String endpoint;
            if (baseUrl.endsWith("/chat/completions")) {
                endpoint = baseUrl;
            } else if (baseUrl.contains("/v4") || baseUrl.contains("/v3") || baseUrl.contains("/v2")) {
                // Provider already includes version segment (e.g. /api/paas/v4)
                endpoint = baseUrl + "/chat/completions";
            } else {
                // Standard OpenAI-style: append /v1/chat/completions
                endpoint = baseUrl + "/v1/chat/completions";
            }
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> httpResponse = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("LLM API error: HTTP " + httpResponse.statusCode()
                        + " — " + abbreviate(httpResponse.body(), 500));
            }

            JsonNode root = MAPPER.readTree(httpResponse.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            if (content.isEmpty()) {
                throw new RuntimeException("LLM API returned empty content: " + abbreviate(httpResponse.body(), 500));
            }

            return new LlmResponse(content, "openai-compatible(" + model + ")", false);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
        }
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
