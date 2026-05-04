package ai.sreagent.probe.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.llm.proposer.ProbeIntent;

import java.time.Instant;
import java.util.Map;

/**
 * Maps Alertmanager probe intents to fixture Evidence.
 * Deterministic — no live Alertmanager required.
 */
public class AlertmanagerProbeMapper {

    public Evidence mapToFixtureEvidence(ProbeIntent intent, String incidentId) {
        String evidenceType = inferEvidenceType(intent);
        double strength = inferStrength(intent);
        String content = buildContent(intent, evidenceType, strength);

        return new Evidence(
            "probe_am_" + String.format("%08x", intent.hashCode() & 0xFFFFFFFFL),
            incidentId,
            "alertmanager",
            evidenceType,
            intent.targetService(),
            Instant.now(),
            content,
            Map.of(
                "probeType", "ALERTMANAGER_QUERY",
                "queryIntent", intent.queryIntent(),
                "mode", "fixture"
            ),
            strength
        );
    }

    private String inferEvidenceType(ProbeIntent intent) {
        String query = intent.queryIntent().toLowerCase();
        if (query.contains("firing")) return "alert_firing";
        if (query.contains("severity")) return "alert_severity_high";
        if (query.contains("silence")) return "alert_silenced";
        if (query.contains("inhibit")) return "alert_inhibited";
        if (query.contains("group")) return "alert_grouped";
        return "alert_firing";
    }

    private double inferStrength(ProbeIntent intent) {
        return 0.68;
    }

    private String buildContent(ProbeIntent intent, String evidenceType, double strength) {
        return "Alertmanager fixture: " + evidenceType + " detected for " + intent.targetService()
            + ". Strength=" + String.format("%.2f", strength) + ". Intent: " + intent.queryIntent();
    }
}
