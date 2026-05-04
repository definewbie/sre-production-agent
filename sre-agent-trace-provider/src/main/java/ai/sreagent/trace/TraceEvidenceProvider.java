package ai.sreagent.trace;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.trace.client.FixtureTraceQueryClient;
import ai.sreagent.trace.client.TraceQueryClient;
import ai.sreagent.trace.mapper.TraceEvidenceMapper;
import ai.sreagent.trace.parser.ParsedTrace;
import ai.sreagent.trace.parser.TraceResponseParser;
import ai.sreagent.trace.query.TraceQueryTemplate;
import ai.sreagent.trace.query.TraceQueryTemplateRegistry;
import ai.sreagent.trace.query.TraceQueryType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Main entry point for collecting trace evidence during RCA.
 * Orchestrates trace query building, client execution, response parsing, and evidence mapping.
 */
public class TraceEvidenceProvider {

    private final TraceQueryClient client;
    private final TraceQueryTemplateRegistry templateRegistry;
    private final TraceResponseParser parser;
    private final TraceEvidenceMapper mapper;

    public TraceEvidenceProvider(TraceQueryClient client) {
        this.client = client;
        this.templateRegistry = new TraceQueryTemplateRegistry();
        this.parser = new TraceResponseParser();
        this.mapper = new TraceEvidenceMapper();
    }

    public TraceEvidenceProvider(TraceQueryClient client,
                                  TraceQueryTemplateRegistry templateRegistry,
                                  TraceResponseParser parser,
                                  TraceEvidenceMapper mapper) {
        this.client = client;
        this.templateRegistry = templateRegistry;
        this.parser = parser;
        this.mapper = mapper;
    }

    /**
     * Collect trace evidence for an incident based on the requested query types.
     */
    public TraceEvidenceResult collect(TraceEvidenceRequest request) {
        List<Evidence> allEvidence = new ArrayList<>();
        Map<String, Object> rawSummary = new LinkedHashMap<>();
        rawSummary.put("reader", client.clientName());
        rawSummary.put("service", request.service());
        rawSummary.put("namespace", request.namespace());

        Instant endTime = request.endTime() != null ? request.endTime() : Instant.now();
        Instant startTime = request.startTime() != null ? request.startTime() : endTime.minus(request.lookback());

        List<TraceQueryType> queryTypes = request.queryTypes();
        if (queryTypes == null || queryTypes.isEmpty()) {
            queryTypes = List.of(TraceQueryType.DOWNSTREAM_SLOW_SPAN, TraceQueryType.ERROR_SPAN,
                    TraceQueryType.ROOT_SPAN_SLOW, TraceQueryType.TIMEOUT_SPAN);
        }

        int queryCount = 0;
        for (TraceQueryType queryType : queryTypes) {
            Optional<TraceQueryTemplate> templateOpt = templateRegistry.getTemplate(queryType);
            if (templateOpt.isEmpty()) {
                continue;
            }

            queryCount++;

            // Set query type hint on fixture client for fixture resolution
            if (client instanceof FixtureTraceQueryClient fixtureClient) {
                if (!fixtureClient.hasExplicitFixture()) {
                    fixtureClient.setQueryTypeHint(queryType);
                }
            }

            // Execute query
            String responseJson = client.findTraces(
                    request.service(), startTime, endTime, 20);

            // Parse response
            List<ParsedTrace> traces = parser.parse(responseJson);

            // Map to Evidence
            List<Evidence> evidence = mapper.map(queryType, traces,
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

        return new TraceEvidenceResult(request.incidentId(), allEvidence, rawSummary);
    }

    public boolean isHealthy() {
        return client.isAvailable();
    }

    public String clientName() {
        return client.clientName();
    }
}
