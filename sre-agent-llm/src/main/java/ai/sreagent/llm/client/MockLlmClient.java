package ai.sreagent.llm.client;

import ai.sreagent.core.domain.ConfidenceResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic mock LLM client.
 * Returns predictable RCA-assisted explanation text without network access.
 * Used as default provider when no real LLM is configured.
 */
public class MockLlmClient implements LlmClient {

    private static final String PROVIDER = "mock";

    @Override
    public LlmResponse complete(LlmRequest request) {
        String content = buildMockResponse(request);
        return new LlmResponse(content, PROVIDER, true);
    }

    private String buildMockResponse(LlmRequest request) {
        String userPrompt = request.userPrompt();

        // Extract key values from the prompt for deterministic output
        String decisionType = extractValue(userPrompt, "Decision Type: ");
        String selectedHypothesis = extractValue(userPrompt, "Selected Hypothesis: ");
        String scoreGap = extractValue(userPrompt, "Score Gap: ");

        StringBuilder sb = new StringBuilder();
        sb.append("## Executive Summary\n\n");
        sb.append("This incident should not be treated as a single definitive RCA. ");
        sb.append("The deterministic investigation found two plausible hypotheses: ");
        sb.append("deployment_regression and downstream_dependency_latency. ");
        sb.append("deployment_regression leads with score 0.64, but downstream_dependency_latency ");
        sb.append("remains material at 0.58. Because the score gap is only 0.06, ");
        sb.append("the safer conclusion is competing_hypotheses.\n\n");

        sb.append("## Reasoning Narrative\n\n");
        sb.append("The deployment_regression hypothesis is supported by a deploy event ");
        sb.append("within the alert window, a timeout config change in git, and an error rate spike ");
        sb.append("coinciding with the deployment. However, timeout logs existed before the deployment, ");
        sb.append("which introduces a contradiction — the deployment may not be the only cause.\n\n");
        sb.append("The downstream_dependency_latency hypothesis is supported by payment-service latency ");
        sb.append("spike, timeout logs, and service topology showing order-service depends on payment-service. ");
        sb.append("The topology evidence (order-service → payment-service) makes this a plausible alternative.\n\n");

        sb.append("## Uncertainty Explanation\n\n");
        sb.append("The score gap of 0.06 is below the 0.10 decisive threshold. ");
        sb.append("Both hypotheses have moderate confidence. ");
        sb.append("The contradiction in deployment_regression (pre-existing timeouts) prevents ");
        sb.append("it from being declared the definitive root cause. ");
        sb.append("Forcing a single RCA under these conditions would be misleading.\n\n");

        sb.append("## Next Steps\n\n");
        sb.append("1. Compare timeout error rates before and after the deployment to isolate the deployment effect.\n");
        sb.append("2. Check payment-service latency by endpoint to determine if the spike is uniform or specific.\n");
        sb.append("3. Review payment-service circuit breaker configuration.\n");
        sb.append("4. Check Redis cache hit rate — a cache miss spike could amplify latency.\n\n");

        sb.append("## Limitations\n\n");
        sb.append("- Current evidence is static JSON. Real evidence providers (K8s, Prometheus, Loki, ");
        sb.append("EC2, RDS, ElastiCache, ALB, CMDB, service topology) may provide additional context.\n");
        sb.append("- Confidence weights are manually assigned, not learned from historical data.\n");
        sb.append("- LLM explanation is advisory and does not override the deterministic decision.\n\n");

        sb.append("## Unverified Proposals\n\n");
        sb.append("- Investigate whether Redis cache warming was incomplete after deployment.\n");
        sb.append("- Check if payment-service had a simultaneous config change.\n\n");

        return sb.toString();
    }

    private String extractValue(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) return "unknown";
        int end = text.indexOf('\n', idx);
        if (end < 0) end = text.length();
        return text.substring(idx + marker.length(), end).trim();
    }
}
