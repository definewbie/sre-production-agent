package ai.sreagent.alertmanager.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;

import java.time.Instant;
import java.util.*;

/**
 * Maps Alertmanager alerts to semantic Evidence objects.
 * Each alert can produce one or more evidence items based on its state.
 */
public class AlertmanagerEvidenceMapper {

    /**
     * Map a list of alerts to Evidence objects.
     * Empty alerts list produces alert_no_signal evidence.
     */
    public List<Evidence> map(List<AlertmanagerAlert> alerts, String incidentId, String service, String namespace) {
        if (alerts == null || alerts.isEmpty()) {
            return List.of(buildNoSignalEvidence(incidentId, service, namespace));
        }

        List<Evidence> evidenceList = new ArrayList<>();
        for (AlertmanagerAlert alert : alerts) {
            evidenceList.addAll(mapAlert(alert, incidentId));
        }

        // Check for grouped alerts (multiple alerts with same service/namespace)
        if (alerts.size() > 1) {
            evidenceList.add(buildGroupedEvidence(alerts, incidentId));
        }

        return evidenceList;
    }

    private List<Evidence> mapAlert(AlertmanagerAlert alert, String incidentId) {
        List<Evidence> list = new ArrayList<>();

        // Core state evidence
        if (alert.isFiring()) {
            list.add(buildFiringEvidence(alert, incidentId));
        } else if (alert.isResolved()) {
            list.add(buildResolvedEvidence(alert, incidentId));
        }

        // Severity evidence
        String severity = alert.severity();
        if ("critical".equalsIgnoreCase(severity) || "high".equalsIgnoreCase(severity)
                || "page".equalsIgnoreCase(severity)) {
            list.add(buildSeverityHighEvidence(alert, incidentId));
        }

        // Silenced evidence
        if (alert.isSilenced()) {
            list.add(buildSilencedEvidence(alert, incidentId));
        }

        // Inhibited evidence
        if (alert.isInhibited()) {
            list.add(buildInhibitedEvidence(alert, incidentId));
        }

        return list;
    }

    private Evidence buildFiringEvidence(AlertmanagerAlert alert, String incidentId) {
        return buildEvidence(
                incidentId, AlertmanagerEvidenceTypes.ALERT_FIRING, alert, 0.80,
                "Alertmanager shows " + alert.alertName() + " firing for " + alert.service() + "."
        );
    }

    private Evidence buildResolvedEvidence(AlertmanagerAlert alert, String incidentId) {
        return buildEvidence(
                incidentId, AlertmanagerEvidenceTypes.ALERT_RESOLVED, alert, 0.50,
                "Alertmanager shows " + alert.alertName() + " resolved for " + alert.service() + "."
        );
    }

    private Evidence buildSeverityHighEvidence(AlertmanagerAlert alert, String incidentId) {
        return buildEvidence(
                incidentId, AlertmanagerEvidenceTypes.ALERT_SEVERITY_HIGH, alert, 0.75,
                "Alertmanager shows " + alert.alertName() + " with severity " + alert.severity() + " for " + alert.service() + "."
        );
    }

    private Evidence buildSilencedEvidence(AlertmanagerAlert alert, String incidentId) {
        return buildEvidence(
                incidentId, AlertmanagerEvidenceTypes.ALERT_SILENCED, alert, 0.60,
                "Alertmanager shows " + alert.alertName() + " is silenced for " + alert.service() + "."
        );
    }

    private Evidence buildInhibitedEvidence(AlertmanagerAlert alert, String incidentId) {
        return buildEvidence(
                incidentId, AlertmanagerEvidenceTypes.ALERT_INHIBITED, alert, 0.60,
                "Alertmanager shows " + alert.alertName() + " is inhibited for " + alert.service() + "."
        );
    }

    private Evidence buildGroupedEvidence(List<AlertmanagerAlert> alerts, String incidentId) {
        AlertmanagerAlert first = alerts.get(0);
        Map<String, Object> attrs = buildCommonAttrs(first);
        attrs.put("groupedAlertCount", alerts.size());

        Set<String> alertNames = new LinkedHashSet<>();
        for (AlertmanagerAlert a : alerts) {
            alertNames.add(a.alertName());
        }
        attrs.put("groupedAlertNames", alertNames);

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                AlertmanagerEvidenceTypes.SOURCE,
                AlertmanagerEvidenceTypes.ALERT_GROUPED,
                first.service(),
                first.startsAt() != null ? first.startsAt() : Instant.now(),
                "Alertmanager shows " + alerts.size() + " grouped alerts for " + first.service() + ".",
                attrs,
                0.65
        );
    }

    private Evidence buildNoSignalEvidence(String incidentId, String service, String namespace) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("service", service != null ? service : "unknown");
        attrs.put("namespace", namespace != null ? namespace : "default");

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                AlertmanagerEvidenceTypes.SOURCE,
                AlertmanagerEvidenceTypes.ALERT_NO_SIGNAL,
                service,
                Instant.now(),
                "Alertmanager returned no alerts for " + service + ".",
                attrs,
                0.0
        );
    }

    private Evidence buildEvidence(String incidentId, String evidenceType, AlertmanagerAlert alert,
                                    double strength, String content) {
        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                AlertmanagerEvidenceTypes.SOURCE,
                evidenceType,
                alert.service(),
                alert.startsAt() != null ? alert.startsAt() : Instant.now(),
                content,
                buildCommonAttrs(alert),
                strength
        );
    }

    private Map<String, Object> buildCommonAttrs(AlertmanagerAlert alert) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("alertName", alert.alertName());
        attrs.put("service", alert.service());
        attrs.put("namespace", alert.namespace());
        attrs.put("severity", alert.severity());
        attrs.put("state", alert.state());
        if (alert.startsAt() != null) attrs.put("startsAt", alert.startsAt().toString());
        if (alert.endsAt() != null) attrs.put("endsAt", alert.endsAt().toString());
        attrs.put("fingerprint", alert.fingerprint() != null ? alert.fingerprint() : "");
        attrs.put("labels", alert.labels());
        attrs.put("annotations", alert.annotations());
        attrs.put("silencedBy", alert.silencedBy());
        attrs.put("inhibitedBy", alert.inhibitedBy());
        return attrs;
    }
}
