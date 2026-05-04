package ai.sreagent.cli;

import ai.sreagent.alertmanager.AlertmanagerProvider;
import ai.sreagent.alertmanager.AlertmanagerRequest;
import ai.sreagent.alertmanager.AlertmanagerResult;
import ai.sreagent.alertmanager.client.AlertmanagerClient;
import ai.sreagent.alertmanager.client.AlertmanagerClientConfig;
import ai.sreagent.alertmanager.client.FixtureAlertmanagerClient;
import ai.sreagent.alertmanager.client.HttpAlertmanagerClient;
import ai.sreagent.core.domain.Evidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "collect-alertmanager-alerts",
        description = "Collect Alertmanager alert evidence for an incident")
public class CollectAlertmanagerAlertsCommand implements Callable<Integer> {

    @Option(names = "--service", description = "Service name to filter alerts")
    private String service;

    @Option(names = "--namespace", description = "Kubernetes namespace", defaultValue = "default")
    private String namespace;

    @Option(names = "--alert-name", description = "Alert name to filter")
    private String alertName;

    @Option(names = "--output", description = "Output path for collected evidence JSON", required = true)
    private String outputPath;

    @Option(names = {"--incident-output"}, description = "Optional separate output for incidents")
    private String incidentOutputPath;

    @Option(names = "--reader", description = "Reader mode: fixture or http", defaultValue = "fixture")
    private String readerMode;

    @Option(names = "--alertmanager-url", description = "Alertmanager URL (required for http reader)")
    private String alertmanagerUrl;

    @Option(names = "--include-resolved", description = "Include resolved alerts", defaultValue = "false")
    private boolean includeResolved;

    @Option(names = "--only-firing", description = "Only include firing alerts", defaultValue = "false")
    private boolean onlyFiring;

    private final ObjectMapper mapper;

    public CollectAlertmanagerAlertsCommand() {
        this.mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Integer call() throws Exception {
        // 1. Build label matchers
        Map<String, String> labelMatchers = new LinkedHashMap<>();
        if (service != null && !service.isBlank()) {
            labelMatchers.put("service", service);
        }
        if (namespace != null && !namespace.equals("default")) {
            labelMatchers.put("namespace", namespace);
        }
        if (alertName != null && !alertName.isBlank()) {
            labelMatchers.put("alertname", alertName);
        }

        // 2. Create client
        AlertmanagerClient client = createClient();

        // 3. Create provider
        AlertmanagerProvider provider = new AlertmanagerProvider(client);

        System.out.println("Using reader: " + provider.clientName());
        if (!provider.isHealthy()) {
            System.err.println("Reader is not available: " + provider.clientName());
            return 1;
        }

        // 4. Build request
        String incidentId = "inc-alertmanager-" + System.currentTimeMillis();
        AlertmanagerRequest request = AlertmanagerRequest.builder()
                .incidentId(incidentId)
                .labelMatchers(labelMatchers.isEmpty() ? Map.of() : labelMatchers)
                .includeResolved(includeResolved)
                .onlyFiring(onlyFiring)
                .build();

        // 5. Collect
        AlertmanagerResult result = provider.collect(request);

        // 6. Print summary
        System.out.println("Alertmanager alerts collected");
        System.out.println("reader: " + provider.clientName());
        System.out.println("incidents: " + result.incidentCount());
        System.out.println("evidence count: " + result.evidenceCount());
        if (!result.evidence().isEmpty()) {
            System.out.println("evidence types:");
            for (Evidence e : result.evidence()) {
                System.out.println("  - " + e.evidenceType());
            }
        }

        // 7. Write output
        Path outPath = Path.of(outputPath);
        Files.createDirectories(outPath.getParent());

        if (incidentOutputPath != null) {
            // Option A: separate files for incidents and evidence
            mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), result.evidence());
            System.out.println("evidence output: " + outputPath);

            Path incPath = Path.of(incidentOutputPath);
            Files.createDirectories(incPath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(incPath.toFile(), result.incidents());
            System.out.println("incident output: " + incidentOutputPath);
        } else {
            // Option B: combined output with incidents + evidence
            Map<String, Object> combined = new LinkedHashMap<>();
            combined.put("incidents", result.incidents());
            combined.put("evidence", result.evidence());
            combined.put("summary", result.rawSummary());
            mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), combined);
            System.out.println("output: " + outputPath);
        }

        return 0;
    }

    private AlertmanagerClient createClient() {
        return switch (readerMode.toLowerCase()) {
            case "http" -> {
                if (alertmanagerUrl == null || alertmanagerUrl.isBlank()) {
                    throw new IllegalArgumentException("--alertmanager-url is required when using http reader");
                }
                yield new HttpAlertmanagerClient(AlertmanagerClientConfig.of(alertmanagerUrl));
            }
            default -> new FixtureAlertmanagerClient();
        };
    }
}
