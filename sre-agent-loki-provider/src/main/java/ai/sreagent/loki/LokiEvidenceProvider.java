package ai.sreagent.loki;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.loki.client.FixtureLokiQueryClient;
import ai.sreagent.loki.client.LokiQueryClient;
import ai.sreagent.loki.mapper.LokiEvidenceMapper;
import ai.sreagent.loki.parser.LokiQueryResult;
import ai.sreagent.loki.parser.LokiResponseParser;
import ai.sreagent.loki.query.LokiQueryTemplate;
import ai.sreagent.loki.query.LokiQueryTemplateRegistry;
import ai.sreagent.loki.query.LokiQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Main entry point for collecting Loki log evidence during RCA.
 * Orchestrates LogQL query building, client execution, response parsing, and evidence mapping.
 */
public class LokiEvidenceProvider {

    private final LokiQueryClient client;
    private final LokiQueryTemplateRegistry templateRegistry;
    private final LokiResponseParser parser;
    private final LokiEvidenceMapper mapper;

    public LokiEvidenceProvider(LokiQueryClient client) {
        this.client = client;
        this.templateRegistry = new LokiQueryTemplateRegistry();
        this.parser = new LokiResponseParser();
        this.mapper = new LokiEvidenceMapper();
    }

    public LokiEvidenceProvider(LokiQueryClient client,
                                 LokiQueryTemplateRegistry templateRegistry,
                                 LokiResponseParser parser,
                                 LokiEvidenceMapper mapper) {
        this.client = client;
        this.templateRegistry = templateRegistry;
        this.parser = parser;
        this.mapper = mapper;
    }

    /**
     * Collect Loki log evidence for an incident based on the requested query types.
     */
    public LokiEvidenceResult collect(LokiEvidenceRequest request) {
        List<Evidence> allEvidence = new ArrayList<>();
        Map<String, Object> rawSummary = new LinkedHashMap<>();
        rawSummary.put("reader", client.clientName());
        rawSummary.put("service", request.service());
        rawSummary.put("namespace", request.namespace());

        Instant endTime = request.endTime() != null ? request.endTime() : Instant.now();
        Instant startTime = request.startTime() != null ? request.startTime() : endTime.minus(request.lookback());
        Duration step = Duration.ofMinutes(1);

        List<LokiQueryType> queryTypes = request.queryTypes();
        if (queryTypes == null || queryTypes.isEmpty()) {
            queryTypes = List.of(LokiQueryType.TIMEOUT_ERROR, LokiQueryType.EXCEPTION_LOGS,
                    LokiQueryType.CRASH_LOGS, LokiQueryType.HTTP_5XX_LOGS);
        }

        int queryCount = 0;
        for (LokiQueryType queryType : queryTypes) {
            Optional<LokiQueryTemplate> templateOpt = templateRegistry.getTemplate(queryType);
            if (templateOpt.isEmpty()) {
                continue;
            }

            LokiQueryTemplate template = templateOpt.get();
            String logql = template.buildQuery(request.service(), request.namespace());
            queryCount++;

            // Set query type hint on fixture client for fixture resolution
            if (client instanceof FixtureLokiQueryClient fixtureClient) {
                fixtureClient.setQueryTypeHint(queryType);
            }

            // Execute query
            String responseJson = client.queryRange(logql, startTime, endTime, step);

            // Parse response
            LokiQueryResult result = parser.parse(responseJson);

            // Map to Evidence
            List<Evidence> evidence = mapper.map(queryType, result, logql,
                    request.incidentId(), request.service(), request.namespace(),
                    startTime, endTime);

            allEvidence.addAll(evidence);
        }

        rawSummary.put("queryCount", queryCount);
        rawSummary.put("evidenceCount", allEvidence.size());

        Set<String> evidenceTypes = new LinkedHashSet<>();
        for (Evidence e : allEvidence) {
            evidenceTypes.add(e.evidenceType());
        }
        rawSummary.put("evidenceTypes", evidenceTypes);

        return new LokiEvidenceResult(request.incidentId(), allEvidence, rawSummary);
    }

    public boolean isHealthy() {
        return client.isAvailable();
    }

    public String clientName() {
        return client.clientName();
    }
}
