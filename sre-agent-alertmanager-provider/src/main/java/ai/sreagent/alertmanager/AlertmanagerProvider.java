package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.client.AlertmanagerClient;
import ai.sreagent.alertmanager.client.FixtureAlertmanagerClient;
import ai.sreagent.alertmanager.filter.AlertmanagerAlertFilter;
import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceMapper;
import ai.sreagent.alertmanager.mapper.AlertmanagerIncidentMapper;
import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import ai.sreagent.alertmanager.parser.AlertmanagerResponseParser;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;

import java.util.*;

/**
 * Main entry point for collecting Alertmanager alert evidence during RCA.
 * Orchestrates client execution, response parsing, filtering,
 * incident mapping, and evidence mapping.
 */
public class AlertmanagerProvider {

    private final AlertmanagerClient client;
    private final AlertmanagerResponseParser parser;
    private final AlertmanagerAlertFilter filter;
    private final AlertmanagerIncidentMapper incidentMapper;
    private final AlertmanagerEvidenceMapper evidenceMapper;

    public AlertmanagerProvider(AlertmanagerClient client) {
        this.client = client;
        this.parser = new AlertmanagerResponseParser();
        this.filter = new AlertmanagerAlertFilter();
        this.incidentMapper = new AlertmanagerIncidentMapper();
        this.evidenceMapper = new AlertmanagerEvidenceMapper();
    }

    public AlertmanagerProvider(AlertmanagerClient client,
                                 AlertmanagerResponseParser parser,
                                 AlertmanagerAlertFilter filter,
                                 AlertmanagerIncidentMapper incidentMapper,
                                 AlertmanagerEvidenceMapper evidenceMapper) {
        this.client = client;
        this.parser = parser;
        this.filter = filter;
        this.incidentMapper = incidentMapper;
        this.evidenceMapper = evidenceMapper;
    }

    /**
     * Collect Alertmanager alerts and convert to IncidentTask + Evidence.
     */
    public AlertmanagerResult collect(AlertmanagerRequest request) {
        Map<String, Object> rawSummary = new LinkedHashMap<>();
        rawSummary.put("reader", client.clientName());

        // 1. Set fixture hint if using fixture client and no explicit fixture was set
        if (client instanceof FixtureAlertmanagerClient fixtureClient) {
            if (!fixtureClient.hasExplicitFixture()) {
                fixtureClient.setFixtureName(resolveFixtureName(request));
            }
        }

        // 2. Fetch alerts
        String responseJson = client.getAlerts(request.labelMatchers(), request.includeResolved());
        rawSummary.put("rawResponseLength", responseJson != null ? responseJson.length() : 0);

        // 3. Parse response
        List<AlertmanagerAlert> allAlerts = parser.parse(responseJson);
        rawSummary.put("totalAlertCount", allAlerts.size());

        // 4. Filter alerts
        List<AlertmanagerAlert> filteredAlerts = filter.filter(
                allAlerts, request.labelMatchers(),
                request.includeResolved(), request.onlyFiring());
        rawSummary.put("filteredAlertCount", filteredAlerts.size());

        // 5. Map to IncidentTasks
        List<IncidentTask> incidents = new ArrayList<>();
        for (AlertmanagerAlert alert : filteredAlerts) {
            incidents.add(incidentMapper.map(alert));
        }

        // 6. Map to Evidence
        String service = extractService(request);
        String namespace = extractNamespace(request);
        List<Evidence> evidence = evidenceMapper.map(filteredAlerts,
                request.incidentId(), service, namespace);

        rawSummary.put("incidentCount", incidents.size());
        rawSummary.put("evidenceCount", evidence.size());

        Set<String> evidenceTypes = new LinkedHashSet<>();
        for (Evidence e : evidence) {
            evidenceTypes.add(e.evidenceType());
        }
        rawSummary.put("evidenceTypes", evidenceTypes);

        return new AlertmanagerResult(incidents, evidence, rawSummary);
    }

    public boolean isHealthy() {
        return client.isAvailable();
    }

    public String clientName() {
        return client.clientName();
    }

    private String resolveFixtureName(AlertmanagerRequest request) {
        if (request.labelMatchers() != null) {
            String alertName = request.labelMatchers().get("alertname");
            if (alertName != null) {
                return alertName.toLowerCase().replace(" ", "_") + ".json";
            }
        }
        return "firing_high_error_rate.json";
    }

    private String extractService(AlertmanagerRequest request) {
        if (request.labelMatchers() != null) {
            String svc = request.labelMatchers().get("service");
            if (svc != null) return svc;
        }
        return "unknown-service";
    }

    private String extractNamespace(AlertmanagerRequest request) {
        if (request.labelMatchers() != null) {
            String ns = request.labelMatchers().get("namespace");
            if (ns != null) return ns;
        }
        return "default";
    }
}
