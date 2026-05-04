package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;

import java.time.Instant;
import java.util.Map;

/**
 * Maps Loki probe intents to fixture Evidence.
 * Deterministic — no live Loki required.
 */
public class LokiProbeMapper {

    public Evidence mapToFixtureEvidence(ProbeIntent intent, String incidentId) {
        String evidenceType = inferEvidenceType(intent);
        double strength = inferStrength(intent);
        String content = buildContent(intent, evidenceType, strength);

        return new Evidence(
            "probe_loki_" + String.format("%08x", intent.hashCode() & 0xFFFFFFFFL),
            incidentId,
            "loki",
            evidenceType,
            intent.targetService(),
            Instant.now(),
            content,
            Map.of(
                "probeType", "LOKI_QUERY",
                "queryIntent", intent.queryIntent(),
                "mode", "fixture"
            ),
            strength
        );
    }

    private String inferEvidenceType(ProbeIntent intent) {
        String query = intent.queryIntent().toLowerCase();
        if (query.contains("timeout") && query.contains("downstream")) return "log_downstream_timeout";
        if (query.contains("timeout")) return "log_timeout_error";
        if (query.contains("retry")) return "log_retry_exhausted";
        if (query.contains("exception")) return "log_exception";
        if (query.contains("oom") || query.contains("memory")) return "log_oom_detected";
        if (query.contains("crash")) return "log_crash_loop_detected";
        if (query.contains("db")) return "log_db_connection_timeout";
        return "log_timeout_error";
    }

    private double inferStrength(ProbeIntent intent) {
        return 0.70;
    }

    private String buildContent(ProbeIntent intent, String evidenceType, double strength) {
        return "Loki fixture: " + evidenceType + " detected for " + intent.targetService()
            + ". Strength=" + String.format("%.2f", strength) + ". Intent: " + intent.queryIntent();
    }
}
