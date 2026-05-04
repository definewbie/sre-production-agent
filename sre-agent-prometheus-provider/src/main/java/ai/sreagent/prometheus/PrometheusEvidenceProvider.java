package ai.sreagent.prometheus;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.prometheus.client.PrometheusQueryClient;
import ai.sreagent.prometheus.mapper.PrometheusEvidenceMapper;
import ai.sreagent.prometheus.parser.PrometheusQueryResult;
import ai.sreagent.prometheus.parser.PrometheusResponseParser;
import ai.sreagent.prometheus.query.PrometheusQueryTemplate;
import ai.sreagent.prometheus.query.PrometheusQueryTemplateRegistry;
import ai.sreagent.prometheus.query.PrometheusQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Main entry point for collecting Prometheus evidence during RCA.
 * Orchestrates query building, client execution, response parsing, and evidence mapping.
 */
public class PrometheusEvidenceProvider {

    private final PrometheusQueryClient client;
    private final PrometheusQueryTemplateRegistry templateRegistry;
    private final PrometheusResponseParser parser;
    private final PrometheusEvidenceMapper mapper;

    public PrometheusEvidenceProvider(PrometheusQueryClient client) {
        this.client = client;
        this.templateRegistry = new PrometheusQueryTemplateRegistry();
        this.parser = new PrometheusResponseParser();
        this.mapper = new PrometheusEvidenceMapper();
    }

    public PrometheusEvidenceProvider(PrometheusQueryClient client,
                                      PrometheusQueryTemplateRegistry templateRegistry,
                                      PrometheusResponseParser parser,
                                      PrometheusEvidenceMapper mapper) {
        this.client = client;
        this.templateRegistry = templateRegistry;
        this.parser = parser;
        this.mapper = mapper;
    }

    /**
     * Collect Prometheus evidence for an incident based on the requested query types.
     *
     * @param request evidence collection request
     * @return result containing Evidence objects and raw summary
     */
    public PrometheusEvidenceResult collect(PrometheusEvidenceRequest request) {
        List<Evidence> allEvidence = new ArrayList<>();
        Map<String, Object> rawSummary = new LinkedHashMap<>();
        rawSummary.put("reader", client.clientName());
        rawSummary.put("service", request.service());
        rawSummary.put("namespace", request.namespace());

        Instant endTime = request.endTime() != null ? request.endTime() : Instant.now();
        Instant startTime = request.startTime() != null ? request.startTime() : endTime.minus(request.lookback());
        Duration step = Duration.ofMinutes(1);

        List<PrometheusQueryType> queryTypes = request.queryTypes();
        if (queryTypes == null || queryTypes.isEmpty()) {
            queryTypes = List.of(PrometheusQueryType.ERROR_RATE, PrometheusQueryType.LATENCY_P95,
                    PrometheusQueryType.MEMORY_USAGE, PrometheusQueryType.RESTART_RATE);
        }

        int queryCount = 0;
        for (PrometheusQueryType queryType : queryTypes) {
            Optional<PrometheusQueryTemplate> templateOpt = templateRegistry.getTemplate(queryType);
            if (templateOpt.isEmpty()) {
                continue;
            }

            PrometheusQueryTemplate template = templateOpt.get();
            String promql = template.buildQuery(request.service(), request.namespace());
            queryCount++;

            // Execute query
            String responseJson = client.queryRange(promql, startTime, endTime, step);

            // Parse response
            PrometheusQueryResult result = parser.parse(responseJson);

            // Map to Evidence
            List<Evidence> evidence = mapper.map(queryType, result, promql,
                    request.incidentId(), request.service(), request.namespace(),
                    startTime, endTime);

            allEvidence.addAll(evidence);
        }

        rawSummary.put("queryCount", queryCount);
        rawSummary.put("evidenceCount", allEvidence.size());

        // Collect distinct evidence types
        Set<String> evidenceTypes = new LinkedHashSet<>();
        for (Evidence e : allEvidence) {
            evidenceTypes.add(e.evidenceType());
        }
        rawSummary.put("evidenceTypes", evidenceTypes);

        return new PrometheusEvidenceResult(request.incidentId(), allEvidence, rawSummary);
    }

    /**
     * Quick health check — is the Prometheus client functional?
     */
    public boolean isHealthy() {
        return client.isAvailable();
    }

    public String clientName() {
        return client.clientName();
    }
}
