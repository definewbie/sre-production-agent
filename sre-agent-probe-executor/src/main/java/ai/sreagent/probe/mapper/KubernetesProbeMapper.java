package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;

import java.time.Instant;
import java.util.Map;

/**
 * Maps Kubernetes probe intents to fixture Evidence.
 * Deterministic — no live cluster required.
 */
public class KubernetesProbeMapper {

    public Evidence mapToFixtureEvidence(ProbeIntent intent, String incidentId) {
        String evidenceType = inferEvidenceType(intent);
        double strength = inferStrength(intent);
        String content = buildContent(intent, evidenceType, strength);

        return new Evidence(
            "probe_k8s_" + String.format("%08x", intent.hashCode() & 0xFFFFFFFFL),
            incidentId,
            "kubernetes",
            evidenceType,
            intent.targetService(),
            Instant.now(),
            content,
            Map.of(
                "probeType", "KUBERNETES_QUERY",
                "queryIntent", intent.queryIntent(),
                "mode", "fixture"
            ),
            strength
        );
    }

    private String inferEvidenceType(ProbeIntent intent) {
        String query = intent.queryIntent().toLowerCase();
        if (query.contains("restart")) return "pod_restart_count_increased";
        if (query.contains("readiness") || query.contains("ready")) return "pod_not_ready";
        if (query.contains("oom") || query.contains("memory")) return "kubernetes_event_oomkilled";
        if (query.contains("crash")) return "container_crash_loop_backoff";
        return "pod_restart_count_increased";
    }

    private double inferStrength(ProbeIntent intent) {
        return 0.65;
    }

    private String buildContent(ProbeIntent intent, String evidenceType, double strength) {
        return "Kubernetes fixture: " + evidenceType + " detected for " + intent.targetService()
            + ". Strength=" + String.format("%.2f", strength) + ". Intent: " + intent.queryIntent();
    }
}
