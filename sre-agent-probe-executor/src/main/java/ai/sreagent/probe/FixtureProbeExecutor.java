package ai.sreagent.probe;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.evidence.EvidenceNormalizer;
import ai.sreagent.core.evidence.NormalizedEvidence;
import ai.sreagent.llm.proposer.ProbeIntent;
import ai.sreagent.llm.proposer.ProbeType;
import ai.sreagent.probe.mapper.*;

import java.util.*;

/**
 * Fixture-based probe executor for deterministic testing and demo.
 * Routes each ProbeIntent to the appropriate provider mapper
 * and returns Evidence + NormalizedEvidence.
 * No live backend required.
 */
public class FixtureProbeExecutor implements ProbeExecutor {

    private final Map<ProbeType, Object> mappers;

    public FixtureProbeExecutor() {
        Map<ProbeType, Object> map = new EnumMap<>(ProbeType.class);
        map.put(ProbeType.PROMETHEUS_QUERY, new PrometheusProbeMapper());
        map.put(ProbeType.LOKI_QUERY, new LokiProbeMapper());
        map.put(ProbeType.TRACE_QUERY, new TraceProbeMapper());
        map.put(ProbeType.KUBERNETES_QUERY, new KubernetesProbeMapper());
        map.put(ProbeType.ALERTMANAGER_QUERY, new AlertmanagerProbeMapper());
        this.mappers = map;
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionPlan plan) {
        List<Evidence> allEvidence = new ArrayList<>();
        List<String> executedIds = new ArrayList<>();
        List<String> skippedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (ProbeIntent intent : plan.probeIntents()) {
            try {
                Evidence evidence = routeAndMap(intent, plan.incidentId());
                if (evidence != null) {
                    allEvidence.add(evidence);
                    executedIds.add(intent.queryIntent());
                } else {
                    skippedIds.add(intent.queryIntent());
                }
            } catch (Exception e) {
                errors.add(intent.probeType() + ": " + e.getMessage());
                skippedIds.add(intent.queryIntent());
            }
        }

        List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(allEvidence);

        ProbeExecutionStatus status = determineStatus(executedIds, errors);

        return new ProbeExecutionResult(
            plan.incidentId(),
            plan.proposalId(),
            status,
            List.copyOf(allEvidence),
            normalized,
            List.copyOf(executedIds),
            List.copyOf(skippedIds),
            List.copyOf(errors),
            false  // canAffectDecision always false
        );
    }

    private Evidence routeAndMap(ProbeIntent intent, String incidentId) {
        Object mapper = mappers.get(intent.probeType());
        if (mapper == null) {
            return null;
        }

        return switch (intent.probeType()) {
            case PROMETHEUS_QUERY -> ((PrometheusProbeMapper) mapper).mapToFixtureEvidence(intent, incidentId);
            case LOKI_QUERY -> ((LokiProbeMapper) mapper).mapToFixtureEvidence(intent, incidentId);
            case TRACE_QUERY -> ((TraceProbeMapper) mapper).mapToFixtureEvidence(intent, incidentId);
            case KUBERNETES_QUERY -> ((KubernetesProbeMapper) mapper).mapToFixtureEvidence(intent, incidentId);
            case ALERTMANAGER_QUERY -> ((AlertmanagerProbeMapper) mapper).mapToFixtureEvidence(intent, incidentId);
            default -> null;
        };
    }

    private ProbeExecutionStatus determineStatus(List<String> executed, List<String> errors) {
        if (!errors.isEmpty() && executed.isEmpty()) {
            return ProbeExecutionStatus.FAILED;
        }
        if (!executed.isEmpty()) {
            return ProbeExecutionStatus.EXECUTED;
        }
        return ProbeExecutionStatus.SKIPPED_BY_POLICY;
    }
}
