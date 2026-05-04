package ai.sreagent.cli;

import ai.sreagent.trace.TraceEvidenceProvider;
import ai.sreagent.trace.TraceEvidenceRequest;
import ai.sreagent.trace.TraceEvidenceResult;
import ai.sreagent.trace.client.FixtureTraceQueryClient;
import ai.sreagent.trace.client.HttpTraceQueryClient;
import ai.sreagent.trace.client.TraceClientConfig;
import ai.sreagent.trace.query.TraceQueryType;
import ai.sreagent.core.domain.Evidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * CLI command to collect trace evidence from a tracing backend.
 * Supports fixture (deterministic) and HTTP (live) readers.
 */
@Command(name = "collect-trace-evidence",
        description = "Collect distributed trace evidence for RCA")
public class CollectTraceEvidenceCommand implements Callable<Integer> {

    @Option(names = "--service", description = "Target service name", required = true)
    private String service;

    @Option(names = "--namespace", description = "Kubernetes namespace", defaultValue = "default")
    private String namespace;

    @Option(names = "--operation", description = "Operation name filter (optional)")
    private String operation;

    @Option(names = "--query-type", description = "Query type: DOWNSTREAM_SLOW_SPAN, ERROR_SPAN, ROOT_SPAN_SLOW, DEPENDENCY_PATH, TIMEOUT_SPAN",
            defaultValue = "DOWNSTREAM_SLOW_SPAN")
    private String queryTypeStr;

    @Option(names = "--output", description = "Output file path", required = true)
    private String output;

    @Option(names = "--reader", description = "Reader type: fixture or http", defaultValue = "fixture")
    private String reader;

    @Option(names = "--trace-url", description = "Trace backend URL (required for http reader)")
    private String traceUrl;

    @Option(names = "--backend-type", description = "Backend type: jaeger, tempo, generic", defaultValue = "jaeger")
    private String backendType;

    @Option(names = "--lookback", description = "Lookback duration in minutes", defaultValue = "30")
    private int lookbackMinutes;

    @Override
    public Integer call() throws Exception {
        TraceQueryType queryType = TraceQueryType.fromString(queryTypeStr);
        if (queryType == null) {
            System.err.println("Unknown query type: " + queryTypeStr);
            System.err.println("Supported: DOWNSTREAM_SLOW_SPAN, ERROR_SPAN, ROOT_SPAN_SLOW, DEPENDENCY_PATH, TIMEOUT_SPAN");
            return 1;
        }

        TraceEvidenceProvider provider;
        if ("http".equalsIgnoreCase(reader)) {
            if (traceUrl == null || traceUrl.isBlank()) {
                System.err.println("HTTP reader requires --trace-url");
                return 1;
            }
            TraceClientConfig config = TraceClientConfig.of(traceUrl, backendType);
            provider = new TraceEvidenceProvider(new HttpTraceQueryClient(config));
        } else {
            provider = new TraceEvidenceProvider(new FixtureTraceQueryClient());
        }

        TraceEvidenceRequest request = TraceEvidenceRequest.builder()
                .incidentId("cli-trace-" + System.currentTimeMillis())
                .service(service)
                .namespace(namespace)
                .operation(operation)
                .lookback(Duration.ofMinutes(lookbackMinutes))
                .queryTypes(List.of(queryType))
                .build();

        TraceEvidenceResult result = provider.collect(request);

        // Write output
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        File outputFile = new File(output);
        outputFile.getParentFile().mkdirs();
        mapper.writeValue(outputFile, result);

        // Print summary
        System.out.println("Trace evidence collected");
        System.out.println("reader: " + provider.clientName());
        System.out.println("evidence count: " + result.evidenceCount());
        System.out.println("evidence types:");
        for (Evidence e : result.evidence()) {
            System.out.println("  - " + e.evidenceType());
        }
        System.out.println("output: " + outputFile.getAbsolutePath());

        return 0;
    }
}
