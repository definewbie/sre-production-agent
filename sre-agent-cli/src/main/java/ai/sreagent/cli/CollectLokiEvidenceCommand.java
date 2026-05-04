package ai.sreagent.cli;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.loki.LokiEvidenceProvider;
import ai.sreagent.loki.LokiEvidenceRequest;
import ai.sreagent.loki.LokiEvidenceResult;
import ai.sreagent.loki.client.FixtureLokiQueryClient;
import ai.sreagent.loki.client.HttpLokiQueryClient;
import ai.sreagent.loki.client.LokiClientConfig;
import ai.sreagent.loki.client.LokiQueryClient;
import ai.sreagent.loki.query.LokiQueryType;
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

@Command(name = "collect-loki-evidence",
        description = "Collect Loki log evidence for an incident")
public class CollectLokiEvidenceCommand implements Callable<Integer> {

    @Option(names = "--service", description = "Service name to investigate", required = true)
    private String service;

    @Option(names = "--namespace", description = "Kubernetes namespace", defaultValue = "default")
    private String namespace;

    @Option(names = "--query-type", description = "Query type(s) (comma-separated LokiQueryType names)",
            split = ",")
    private List<String> queryTypeStrings;

    @Option(names = "--output", description = "Output path for collected evidence JSON", required = true)
    private String outputPath;

    @Option(names = "--reader", description = "Reader mode: fixture or http", defaultValue = "fixture")
    private String readerMode;

    @Option(names = "--loki-url", description = "Loki URL (required for http reader)")
    private String lokiUrl;

    @Option(names = "--lookback", description = "Lookback duration (e.g., 30m, 1h)", defaultValue = "30m")
    private String lookbackStr;

    private final ObjectMapper mapper;

    public CollectLokiEvidenceCommand() {
        this.mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Integer call() throws Exception {
        // 1. Parse query types
        List<LokiQueryType> queryTypes = parseQueryTypes();

        // 2. Parse time range
        Instant endTime = Instant.now();
        Duration lookback = parseLookback();

        // 3. Create client
        LokiQueryClient client = createClient();

        // 4. Create provider
        LokiEvidenceProvider provider = new LokiEvidenceProvider(client);

        System.out.println("Using reader: " + provider.clientName());
        if (!provider.isHealthy()) {
            System.err.println("Reader is not available: " + provider.clientName());
            return 1;
        }

        // 5. Build request
        String incidentId = "inc-loki-" + Instant.now().toString().replace(":", "").replace(".", "");
        LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                .incidentId(incidentId)
                .service(service)
                .namespace(namespace)
                .endTime(endTime)
                .lookback(lookback)
                .queryTypes(queryTypes)
                .build();

        // 6. Collect evidence
        LokiEvidenceResult result = provider.collect(request);

        // 7. Print summary
        System.out.println("Loki evidence collected");
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

    private List<LokiQueryType> parseQueryTypes() {
        if (queryTypeStrings == null || queryTypeStrings.isEmpty()) {
            return List.of();
        }
        return queryTypeStrings.stream()
                .map(LokiQueryType::fromString)
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

    private LokiQueryClient createClient() {
        return switch (readerMode.toLowerCase()) {
            case "http" -> {
                if (lokiUrl == null || lokiUrl.isBlank()) {
                    throw new IllegalArgumentException("--loki-url is required when using http reader");
                }
                yield new HttpLokiQueryClient(LokiClientConfig.of(lokiUrl));
            }
            default -> new FixtureLokiQueryClient();
        };
    }
}
