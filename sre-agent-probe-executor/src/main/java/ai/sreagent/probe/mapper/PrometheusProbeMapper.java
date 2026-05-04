package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Maps Prometheus probe intents to fixture Evidence.
 * Deterministic — no live Prometheus required.
 */
public class PrometheusProbeMapper {

    public Evidence mapToFixtureEvidence(ProbeIntent intent, String incidentId) {
        String evidenceType = inferEvidenceType(intent);
        double strength = inferStrength(intent);
        String content = buildContent(intent, evidenceType, strength);

        return new Evidence(
            "probe_prom_" + String.format("%08x", intent.hashCode() & 0xFFFFFFFFL),
            incidentId,
            "prometheus",
            evidenceType,
            intent.targetService(),
            Instant.now(),
            content,
            Map.of(
                "probeType", "PROMETHEUS_QUERY",
                "queryIntent", intent.queryIntent(),
                "mode", "fixture"
            ),
            strength
        );
    }

    private String inferEvidenceType(ProbeIntent intent) {
        String query = intent.queryIntent().toLowerCase();
        if (query.contains("p95") || query.contains("latency")) {
            if (query.contains("downstream")) return "metric_downstream_latency_spike";
            return "metric_latency_p95_spike";
        }
        if (query.contains("error")) return "metric_error_rate_spike";
        if (query.contains("restart")) return "metric_restart_rate_increased";
        if (query.contains("memory")) return "metric_memory_usage_high";
        if (query.contains("cpu")) return "metric_cpu_usage_high";
        return "metric_latency_p95_spike";
    }

    private double inferStrength(ProbeIntent intent) {
        return 0.75;
    }

    private String buildContent(ProbeIntent intent, String evidenceType, double strength) {
        return "Prometheus fixture: " + evidenceType + " detected for " + intent.targetService()
            + ". Strength=" + String.format("%.2f", strength) + ". Intent: " + intent.queryIntent();
    }
}
