package ai.sreagent.cli;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.prometheus.PrometheusEvidenceProvider;
import ai.sreagent.prometheus.PrometheusEvidenceRequest;
import ai.sreagent.prometheus.PrometheusEvidenceResult;
import ai.sreagent.prometheus.client.FixturePrometheusQueryClient;
import ai.sreagent.prometheus.client.HttpPrometheusQueryClient;
import ai.sreagent.prometheus.client.PrometheusClientConfig;
import ai.sreagent.prometheus.client.PrometheusQueryClient;
import ai.sreagent.prometheus.query.PrometheusQueryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "collect-prometheus-evidence",
        description = "Collect Prometheus metrics evidence for an incident")
public class CollectPrometheusEvidenceCommand implements Callable<Integer> {

    @Option(names = "--service", description = "Service name to investigate", required = true)
    private String service;

    @Option(names = "--namespace", description = "Kubernetes namespace", defaultValue = "default")
    private String namespace;

    @Option(names = "--query-type", description = "Query type(s): ERROR_RATE, LATENCY_P95, LATENCY_P99, DOWNSTREAM_LATENCY_P95, MEMORY_USAGE, CPU_USAGE, RESTART_RATE, REQUEST_RATE",
            split = ",")
    private List<String> queryTypeStrings;

    @Option(names = "--output", description = "Output path for collected evidence JSON", required = true)
    private String outputPath;

    @Option(names = "--reader", description = "Reader mode: fixture or http", defaultValue = "fixture")
    private String readerMode;

    @Option(names = "--prometheus-url", description = "Prometheus URL (required for http reader)")
    private String prometheusUrl;

    @Option(names = "--start", description = "Start time (ISO-8601)")
    private String startTimeStr;

    @Option(names = "--end", description = "End time (ISO-8601)")
    private String endTimeStr;

    @Option(names = "--lookback", description = "Lookback duration (e.g., 30m, 1h)", defaultValue = "30m")
    private String lookbackStr;

    private final ObjectMapper mapper;

    public CollectPrometheusEvidenceCommand() {
        this.mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Integer call() throws Exception {
        // 1. Parse query types
        List<PrometheusQueryType> queryTypes = parseQueryTypes();

        // 2. Parse time range
        Instant endTime = endTimeStr != null ? Instant.parse(endTimeStr) : Instant.now();
        Instant startTime = startTimeStr != null ? Instant.parse(startTimeStr) : null;
        Duration lookback = parseLookback();

        // 3. Create client
        PrometheusQueryClient client = createClient();

        // 4. Create provider
        PrometheusEvidenceProvider provider = new PrometheusEvidenceProvider(client);

        System.out.println("Using reader: " + provider.clientName());
        if (!provider.isHealthy()) {
            System.err.println("Reader is not available: " + provider.clientName());
            return 1;
        }

        // 5. Build request
        String incidentId = "inc-prom-" + Instant.now().toString().replace(":", "").replace(".", "");
        PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                .incidentId(incidentId)
                .service(service)
                .namespace(namespace)
                .startTime(startTime)
                .endTime(endTime)
                .lookback(lookback)
                .queryTypes(queryTypes)
                .build();

        // 6. Collect evidence
        PrometheusEvidenceResult result = provider.collect(request);

        // 7. Print summary
        System.out.println("Prometheus evidence collected");
        System.out.println("reader: " + provider.clientName());
        System.out.println("evidence count: " + result.evidenceCount());
        if (!result.evidence().isEmpty()) {
            System.out.println("evidence types:");
            for (Evidence e : result.evidence()) {
                System.out.println("  - " + e.evidenceType());
            }
        }

        // 8. Write output
        Path outPath = Path.of(outputPath);
        Files.createDirectories(outPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), result.evidence());
        System.out.println("output: " + outputPath);

        return 0;
    }

    private List<PrometheusQueryType> parseQueryTypes() {
        if (queryTypeStrings == null || queryTypeStrings.isEmpty()) {
            return List.of();
        }
        return queryTypeStrings.stream()
                .map(PrometheusQueryType::fromString)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Duration parseLookback() {
        if (lookbackStr == null) return Duration.ofMinutes(30);
        String lower = lookbackStr.toLowerCase();
        if (lower.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(lower.replace("h", "")));
        }
        if (lower.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(lower.replace("m", "")));
        }
        if (lower.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(lower.replace("s", "")));
        }
        return Duration.ofMinutes(30);
    }

    private PrometheusQueryClient createClient() {
        return switch (readerMode.toLowerCase()) {
            case "http" -> {
                if (prometheusUrl == null || prometheusUrl.isBlank()) {
                    throw new IllegalArgumentException("--prometheus-url is required when using http reader");
                }
                yield new HttpPrometheusQueryClient(PrometheusClientConfig.of(prometheusUrl));
            }
            default -> new FixturePrometheusQueryClient();
        };
    }
}
