
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
    String errorMessage
) {

    public enum LiveScenarioStatus {
        RUNNING, COMPLETED, FAILED
    }

    /**
     * Create a running result.
     */
    public static LiveScenarioResult running(String scenarioId, String scenarioName) {
        return new LiveScenarioResult(scenarioId, scenarioName, "collecting",
                LiveScenarioStatus.RUNNING, null, null, null, null, 0, null);
    }

    /**
     * Create a completed result.
     */
    public static LiveScenarioResult completed(String scenarioId, String scenarioName,
                                                InvestigationResult baseRca,
                                                LlmHypothesisProposalResult llmProposal,
                                                LiveEvidenceReport evidenceReport,
                                                long durationMs) {
        return new LiveScenarioResult(scenarioId, scenarioName, "completed",
                LiveScenarioStatus.COMPLETED, baseRca.incidentId(), baseRca,
                llmProposal, evidenceReport, durationMs, null);
    }

    /**
     * Create a failed result.
     */
    public static LiveScenarioResult failed(String scenarioId, String scenarioName,
                                             String errorMessage) {
        return new LiveScenarioResult(scenarioId, scenarioName, "failed",
                LiveScenarioStatus.FAILED, null, null, null, null, 0, errorMessage);
    }
}
