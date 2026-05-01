package ai.sreagent.core.workflow;

import ai.sreagent.core.domain.*;

import java.util.List;

/**
 * Immutable result of an investigation workflow run.
 * Carries all domain objects produced during the investigation.
 */
public record InvestigationResult(
        String incidentId,
        IncidentTask incident,
        List<Hypothesis> hypotheses,
        List<VerificationResult> verificationResults,
        List<ConfidenceResult> confidenceResults,
        HypothesisComparison comparison,
        InvestigationDecision decision,
        List<Evidence> evidence,
        String markdownReport,
        List<EventTraceEntry> eventTrace
) {}
