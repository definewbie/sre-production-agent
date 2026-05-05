
package ai.sreagent.server.live;

import ai.sreagent.core.workflow.InvestigationResult;
import ai.sreagent.llm.proposer.LlmHypothesisProposalResult;

import java.util.List;
import java.util.Map;

/**
 * Result of a live scenario RCA run (Step V).
 * Contains the deterministic RCA result plus optional advisory layers.
 * baseDecision is immutable — LLM/probe layers are advisory only.
 */
public record LiveScenarioResult(
    String scenarioId,
    String scenarioName,
    String phase,
    LiveScenarioStatus status,
    String incidentId,
    InvestigationResult baseRca,
    LlmHypothesisProposalResult llmProposal,
    LiveEvidenceReport evidenceReport,
    long durationMs,
    String errorMessage,
    // Time window fields (Phase 4)
    int waitSeconds,
    int lookbackSeconds,
    int stepSeconds,
    String evidenceWindowStart,
    String evidenceWindowEnd,
    // Scenario G dynamic report (Phase 4)
    String scenarioReport
) {

    public enum LiveScenarioStatus {
        RUNNING, COMPLETED, FAILED
    }

    /**
     * Create a running result.
     */
    public static LiveScenarioResult running(String scenarioId, String scenarioName) {
        return new LiveScenarioResult(scenarioId, scenarioName, "collecting",
                LiveScenarioStatus.RUNNING, null, null, null, null, 0, null,
                0, 0, 0, null, null, null);
    }

    /**
     * Create a completed result.
     */
    public static LiveScenarioResult completed(String scenarioId, String scenarioName,
                                                InvestigationResult baseRca,
                                                LlmHypothesisProposalResult llmProposal,
                                                LiveEvidenceReport evidenceReport,
                                                long durationMs,
                                                int waitSeconds, int lookbackSeconds,
                                                int stepSeconds,
                                                String evidenceWindowStart,
                                                String evidenceWindowEnd,
                                                String scenarioReport) {
        return new LiveScenarioResult(scenarioId, scenarioName, "completed",
                LiveScenarioStatus.COMPLETED, baseRca.incidentId(), baseRca,
                llmProposal, evidenceReport, durationMs, null,
                waitSeconds, lookbackSeconds, stepSeconds,
                evidenceWindowStart, evidenceWindowEnd, scenarioReport);
    }

    /**
     * Create a failed result.
     */
    public static LiveScenarioResult failed(String scenarioId, String scenarioName,
                                             String errorMessage) {
        return new LiveScenarioResult(scenarioId, scenarioName, "failed",
                LiveScenarioStatus.FAILED, null, null, null, null, 0, errorMessage,
                0, 0, 0, null, null, null);
    }
}
