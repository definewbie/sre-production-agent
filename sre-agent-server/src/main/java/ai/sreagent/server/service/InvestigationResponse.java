package ai.sreagent.server.service;

import java.util.List;
import java.util.Map;

/**
 * REST response DTO for an investigation run.
 */
public record InvestigationResponse(
        String incidentId,
        String decisionType,
        String selectedHypothesisId,
        double confidenceScore,
        double scoreGap,
        Map<String, Double> scores,
        List<String> competingHypotheses,
        String reportUrl,
        String traceUrl
) {}
