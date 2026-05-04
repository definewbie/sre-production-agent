package ai.sreagent.probe;

import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes LLM-generated probe intents to a validated execution plan.
 * Filters unsupported probe types and preserves deterministic ordering.
 */
public class ProbeIntentRouter {

    private static final List<ProbeType> SUPPORTED_TYPES = List.of(
        ProbeType.PROMETHEUS_QUERY,
        ProbeType.LOKI_QUERY,
        ProbeType.TRACE_QUERY,
        ProbeType.KUBERNETES_QUERY,
        ProbeType.ALERTMANAGER_QUERY
    );

    public ProbeExecutionPlan createPlan(
        String incidentId,
        String proposalId,
        List<ProbeIntent> probeIntents,
        ProbeExecutionMode mode
    ) {
        List<ProbeIntent> supported = new ArrayList<>();
        for (ProbeIntent intent : probeIntents) {
            if (SUPPORTED_TYPES.contains(intent.probeType())) {
                supported.add(intent);
            }
            // Unsupported types (CMDB_QUERY, HUMAN_REVIEW) are silently filtered
        }
        return new ProbeExecutionPlan(incidentId, proposalId, supported, mode, false);
    }

    public static List<ProbeType> supportedTypes() {
        return SUPPORTED_TYPES;
    }
}
