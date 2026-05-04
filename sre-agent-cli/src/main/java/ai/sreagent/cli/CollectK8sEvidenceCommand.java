package ai.sreagent.cli;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.k8s.FixtureKubernetesResourceReader;
import ai.sreagent.k8s.JavaClientKubernetesResourceReader;
import ai.sreagent.k8s.KubernetesClientConfig;
import ai.sreagent.k8s.KubernetesEvidenceProvider;
import ai.sreagent.k8s.KubernetesFaultMode;
import ai.sreagent.k8s.KubectlKubernetesResourceReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "collect-k8s-evidence",
        description = "Collect Kubernetes evidence for an incident")
public class CollectK8sEvidenceCommand implements Callable<Integer> {

    @Option(names = "--alert", description = "Path to alert JSON file (IncidentTask)")
    private String alertPath;

    @Option(names = "--service", description = "Service name to investigate")
    private String service;

    @Option(names = "--namespace", description = "Kubernetes namespace", defaultValue = "default")
    private String namespace;

    @Option(names = "--output", description = "Output path for collected evidence JSON", required = true)
    private String outputPath;

    @Option(names = "--reader", description = "Reader mode: fixture, kubectl, java-client", defaultValue = "fixture")
    private String readerMode;

    @Option(names = "--client-mode", description = "Java client mode: kubeconfig, in-cluster", defaultValue = "kubeconfig")
    private String clientMode;

    @Option(names = "--kubeconfig", description = "Path to kubeconfig file (java-client kubeconfig mode)")
    private String kubeconfigPath;

    @Option(names = "--detect-faults", description = "Also detect K8s fault modes")
    private boolean detectFaults;

    private final ObjectMapper mapper;

    public CollectK8sEvidenceCommand() {
        this.mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Integer call() throws Exception {
        // 1. Build or load incident
        IncidentTask incident = resolveIncident();

        // 2. Create provider with appropriate reader
        KubernetesEvidenceProvider provider = createProvider();

        System.out.println("Using reader: " + provider.readerName());
        if (!provider.isHealthy()) {
            System.err.println("Reader is not available: " + provider.readerName());
            return 1;
        }

        // 3. Collect evidence (semantic for live readers, generic for fixture)
        List<Evidence> evidence;
        if ("kubectl".equals(readerMode) || "java-client".equals(readerMode)) {
            evidence = provider.collectSemanticEvidence(incident);
        } else {
            evidence = provider.collectEvidence(incident);
        }
        System.out.println("Collected " + evidence.size() + " evidence items from Kubernetes");

        // 4. Detect faults if requested
        if (detectFaults) {
            List<KubernetesFaultMode> faults = provider.detectFaults(incident.namespace(), incident.service());
            System.out.println("Detected fault modes:");
            for (KubernetesFaultMode fault : faults) {
                System.out.println("  - " + fault.id() + ": " + fault.description());
            }
        }

        // 5. Write output
        Path outPath = Path.of(outputPath);
        Files.createDirectories(outPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), evidence);
        System.out.println("Evidence written to: " + outputPath);

        return 0;
    }

    private IncidentTask resolveIncident() throws Exception {
        if (alertPath != null) {
            EvidenceLoader loader = new EvidenceLoader();
            return loader.loadAlert(new File(alertPath));
        }

        // Create synthetic incident from CLI args
        String svc = service != null ? service : "unknown-service";
        String ns = namespace;
        return new IncidentTask(
            "inc-k8s-" + Instant.now().toString().replace(":", "").replace(".", ""),
            "K8sEvidenceCollection",
            svc, ns, "info",
            Instant.now(),
            Map.of("app", svc),
            Map.of()
        );
    }

    private KubernetesEvidenceProvider createProvider() {
        return switch (readerMode) {
            case "kubectl" -> new KubernetesEvidenceProvider(new KubectlKubernetesResourceReader());
            case "java-client" -> new KubernetesEvidenceProvider(createJavaClientReader());
            default -> new KubernetesEvidenceProvider(new FixtureKubernetesResourceReader());
        };
    }

    private JavaClientKubernetesResourceReader createJavaClientReader() {
        KubernetesClientConfig config = switch (clientMode.toLowerCase()) {
            case "in-cluster" -> KubernetesClientConfig.inCluster();
            default -> {
                KubernetesClientConfig.KubernetesClientMode mode =
                    KubernetesClientConfig.KubernetesClientMode.KUBECONFIG;
                yield new KubernetesClientConfig(mode, kubeconfigPath, null, 10_000);
            }
        };
        return new JavaClientKubernetesResourceReader(config);
    }
}
