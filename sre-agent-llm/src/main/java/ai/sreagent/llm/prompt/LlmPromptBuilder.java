package ai.sreagent.llm.prompt;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.client.LlmRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the LLM prompt from a deterministic InvestigationResult.
 *
 * Key invariant: the prompt includes strict constraints that prevent the LLM
 * from overriding the deterministic decision, scores, or evidence.
 */
public class LlmPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an RCA reasoning and report synthesis assistant.
            
            You may help explain the deterministic investigation result.
            
            You must only use the provided structured investigation result.
            
            You must not infer a new final root cause.
            You must not change the decision.
            You must not change confidence scores.
            You must not invent evidence.
            You must not hide counter evidence.
            You must not claim certainty when the deterministic decision is competing_hypotheses.
            You must preserve uncertainty.
            You may suggest additional investigation probes, but they must be labeled as unverified proposals.
            You must not invent K8s, EC2, RDS, ElastiCache, ALB, CMDB, or topology facts.
            LLM can assist, but cannot adjudicate.
            """;

    private static final String EVIDENCE_SCOPE_NOTE =
            "This explanation is based only on verified structured evidence currently available to the workflow. " +
            "Future evidence providers may include K8s, EC2, RDS, ElastiCache, ALB, CMDB, and service topology.";

    public LlmRequest build(InvestigationResult result) {
        String userPrompt = buildUserPrompt(result);
        return new LlmRequest(SYSTEM_PROMPT, userPrompt, Map.of("incidentId", result.incidentId()));
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String getEvidenceScopeNote() {
        return EVIDENCE_SCOPE_NOTE;
    }

    private String buildUserPrompt(InvestigationResult result) {
        StringBuilder sb = new StringBuilder();

        // 1. Incident summary
        IncidentTask incident = result.incident();
        sb.append("# Investigation Result for Synthesis\n\n");
        sb.append("Incident ID: ").append(result.incidentId()).append("\n");
        sb.append("Alert: ").append(incident.alertName()).append("\n");
        sb.append("Service: ").append(incident.service()).append("\n");
        sb.append("Severity: ").append(incident.severity()).append("\n\n");

        // 2. Deterministic decision
        InvestigationDecision decision = result.decision();
        sb.append("## Deterministic Decision\n");
        sb.append("Decision Type: ").append(decision.decisionType()).append("\n");
        sb.append("Selected Hypothesis: ").append(decision.selectedHypothesisId()).append("\n");
        sb.append("Confidence Score: ").append(decision.confidenceScore()).append("\n");
        sb.append("Rationale: ").append(decision.rationale()).append("\n");
        if (!decision.competingHypotheses().isEmpty()) {
            sb.append("Competing Hypotheses: ").append(String.join(", ", decision.competingHypotheses())).append("\n");
        }
        sb.append("\n");

        // 3. Hypothesis scores
        sb.append("## Hypothesis Scores\n");
        for (ConfidenceResult cr : result.confidenceResults()) {
            sb.append("- ").append(cr.hypothesisId())
              .append(": score=").append(String.format("%.2f", cr.score()))
              .append(", level=").append(cr.level())
              .append(", decision=").append(cr.decision()).append("\n");
        }
        sb.append("\n");

        // 4. Score gap
        HypothesisComparison comparison = result.comparison();
        sb.append("## Hypothesis Comparison\n");
        sb.append("Leading Hypothesis: ").append(comparison.leadingHypothesisId()).append("\n");
        sb.append("Score Gap: ").append(String.format("%.2f", comparison.scoreGap())).append("\n");
        sb.append("Near Tie: ").append(comparison.nearTie()).append("\n");
        sb.append("Comparison Summary: ").append(comparison.comparisonSummary()).append("\n\n");

        // 5. Verification results
        sb.append("## Verification Results\n");
        for (VerificationResult vr : result.verificationResults()) {
            sb.append("### ").append(vr.hypothesisId()).append("\n");
            sb.append("Supporting Evidence: ").append(String.join(", ", vr.supportingEvidenceIds())).append("\n");
            sb.append("Counter Evidence: ").append(String.join(", ", vr.counterEvidenceIds())).append("\n");
            if (!vr.missingEvidence().isEmpty()) {
                sb.append("Missing Evidence: ").append(String.join(", ", vr.missingEvidence())).append("\n");
            }
            if (!vr.contradictions().isEmpty()) {
                sb.append("Contradictions: ").append(String.join(", ", vr.contradictions())).append("\n");
            }
            sb.append("Explanation: ").append(vr.explanation()).append("\n\n");
        }

        // 6. Evidence details
        sb.append("## Evidence Items\n");
        for (Evidence e : result.evidence()) {
            sb.append("- [").append(e.id()).append("] type=").append(e.evidenceType())
              .append(", service=").append(e.service())
              .append(", strength=").append(String.format("%.2f", e.strength()))
              .append(", content=").append(e.content()).append("\n");
        }
        sb.append("\n");

        // 7. Next probes
        sb.append("## Suggested Next Probes\n");
        for (String probe : decision.nextProbes()) {
            sb.append("- ").append(probe).append("\n");
        }
        sb.append("\n");

        // 8. Event trace summary
        sb.append("## Event Trace Summary\n");
        sb.append("Total events: ").append(result.eventTrace().size()).append("\n");
        for (EventTraceEntry entry : result.eventTrace()) {
            sb.append("- [").append(entry.eventId()).append("] ")
              .append(entry.eventType()).append("\n");
        }
        sb.append("\n");

        // 9. Constraints reminder
        sb.append("## Constraints\n");
        sb.append("- Treat only provided structured evidence as available facts.\n");
        sb.append("- Do not infer missing K8s, EC2, RDS, ElastiCache, ALB, or CMDB facts.\n");
        sb.append("- If discussing future investigation, label it as an unverified proposal.\n");
        sb.append("- LLM can assist, but cannot adjudicate.\n\n");

        // 10. Output format
        sb.append("## Output Format\n");
        sb.append("Produce a structured explanation with these sections:\n");
        sb.append("- Executive Summary\n");
        sb.append("- Reasoning Narrative\n");
        sb.append("- Uncertainty Explanation\n");
        sb.append("- Next Steps\n");
        sb.append("- Limitations\n");
        sb.append("- Unverified Proposals\n");

        return sb.toString();
    }
}
