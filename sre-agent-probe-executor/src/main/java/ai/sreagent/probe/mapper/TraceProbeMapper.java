package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;

import java.time.Instant;
import java.util.Map;

/**
 * Maps Trace probe intents to fixture Evidence.
 * Deterministic — no live trace backend required.
 */
public class TraceProbeMapper {

    public Evidence mapToFixtureEvidence(ProbeIntent intent, String incidentId) {
        String evidenceType = inferEvidenceType(intent);
        double strength = inferStrength(intent);
        String content = buildContent(intent, evidenceType, strength);

        return new Evidence(
            "probe_trace_" + String.format("%08x", intent.hashCode() & 0xFFFFFFFFL),
            incidentId,
            "tracing",
            evidenceType,
            intent.targetService(),
            Instant.now(),
            content,
            Map.of(
                "probeType", "TRACE_QUERY",
                "queryIntent", intent.queryIntent(),
                "mode", "fixture"
            ),
            strength
        );
    }

    private String inferEvidenceType(ProbeIntent intent) {
        String query = intent.queryIntent().toLowerCase();
        if (query.contains("downstream") && (query.contains("slow") || query.contains("latency")))
            return "trace_downstream_span_slow";
        if (query.contains("error")) return "trace_error_span";
        if (query.contains("timeout")) return "trace_timeout_span";
        if (query.contains("dependency") || query.contains("path")) return "trace_dependency_path";
        if (query.contains("child") && query.contains("dominat")) return "trace_child_span_dominates_latency";
        if (query.contains("slow") || query.contains("latency")) return "trace_root_span_slow";
        return "trace_downstream_span_slow";
    }

    private double inferStrength(ProbeIntent intent) {
        return 0.72;
    }

    private String buildContent(ProbeIntent intent, String evidenceType, double strength) {
        return "Trace fixture: " + evidenceType + " detected for " + intent.targetService()
            + ". Strength=" + String.format("%.2f", strength) + ". Intent: " + intent.queryIntent();
    }
}
