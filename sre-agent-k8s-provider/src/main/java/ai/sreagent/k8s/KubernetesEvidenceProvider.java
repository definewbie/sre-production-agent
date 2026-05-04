package ai.sreagent.k8s;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;

import java.io.IOException;
import java.util.*;

/**
 * Main entry point for collecting Kubernetes evidence during RCA.
 * Orchestrates resource reading, JSON parsing, and evidence mapping.
 */
public class KubernetesEvidenceProvider {

    private final KubernetesResourceReader reader;
    private final KubernetesJsonParser parser;
    private final KubernetesEvidenceMapper mapper;

    public KubernetesEvidenceProvider(KubernetesResourceReader reader) {
        this.reader = reader;
        this.parser = new KubernetesJsonParser();
        this.mapper = new KubernetesEvidenceMapper();
    }

    public KubernetesEvidenceProvider(KubernetesResourceReader reader,
                                       KubernetesJsonParser parser,
                                       KubernetesEvidenceMapper mapper) {
        this.reader = reader;
        this.parser = parser;
        this.mapper = mapper;
    }

    /**
     * Collect all relevant Kubernetes evidence for an incident.
     * Reads pods, deployments, events and maps them to Evidence objects.
     *
     * @param incident the incident to collect evidence for
     * @return list of Evidence objects from Kubernetes resources
     */
    public List<Evidence> collectEvidence(IncidentTask incident) throws IOException {
        List<Evidence> evidence = new ArrayList<>();
        String ns = incident.namespace();
        Map<String, String> labels = Map.of("app", incident.service());

        // 1. Collect Pod status
        String podJson = reader.listResources("pods", ns, labels);
        KubernetesJsonParser.ParsedPod pod = parser.parsePod(podJson);
        if (pod != null) {
            evidence.add(mapper.mapPodToEvidence(pod, incident.id()));
        }

        // 2. Collect Deployment status
        String deployJson = reader.readResource("deployments", incident.service(), ns, null);
        KubernetesJsonParser.ParsedDeployment deployment = parser.parseDeployment(deployJson);
        if (deployment != null) {
            evidence.add(mapper.mapDeploymentToEvidence(deployment, incident.id()));
        }

        // 3. Collect Events
        String eventsJson = reader.listResources("events", ns, labels);
        List<KubernetesJsonParser.ParsedEvent> events = parser.parseEvents(eventsJson);
        evidence.addAll(mapper.mapEventsToEvidence(events, incident.id()));

        return evidence;
    }

    /**
     * Detect Kubernetes fault modes from current pod state.
     */
    public List<KubernetesFaultMode> detectFaults(String namespace, String serviceName) throws IOException {
        Map<String, String> labels = Map.of("app", serviceName);
        String podJson = reader.listResources("pods", namespace, labels);
        KubernetesJsonParser.ParsedPod pod = parser.parsePod(podJson);
        return parser.detectFaultModes(pod);
    }

    /**
     * Collect semantic Kubernetes evidence mapped to RCA pattern evidence types.
     * This method produces evidence types like container_crash_loop_backoff, 
     * pod_restart_count_increased, pod_not_ready, deployment_metadata — matching
     * the types expected by the pod_crash_loop diagnostic pattern.
     *
     * Use this for live kubectl evidence collection. Use collectEvidence() for 
     * generic K8s evidence collection.
     */
    public List<Evidence> collectSemanticEvidence(IncidentTask incident) throws IOException {
        List<Evidence> evidence = new ArrayList<>();
        String ns = incident.namespace();
        Map<String, String> labels = Map.of("app", incident.service());

        // 1. Collect Pod status → semantic evidence (multiple evidence items)
        String podJson = reader.listResources("pods", ns, labels);
        KubernetesJsonParser.ParsedPod pod = parser.parsePod(podJson);
        if (pod != null) {
            evidence.addAll(mapper.mapPodToSemanticEvidence(pod, incident.id()));
        }

        // 2. Collect Deployment status → deployment_metadata evidence
        String deployJson = reader.readResource("deployments", incident.service(), ns, null);
        KubernetesJsonParser.ParsedDeployment deployment = parser.parseDeployment(deployJson);
        if (deployment != null) {
            evidence.add(mapper.mapDeploymentToMetadataEvidence(deployment, incident.id()));
        }

        // 3. Collect Events as additional context
        String eventsJson = reader.listResources("events", ns, labels);
        List<KubernetesJsonParser.ParsedEvent> events = parser.parseEvents(eventsJson);
        evidence.addAll(mapper.mapEventsToEvidence(events, incident.id()));

        return evidence;
    }

    /**
     * Quick health check — is the K8s reader functional?
     */
    public boolean isHealthy() {
        return reader.isAvailable();
    }

    public String readerName() {
        return reader.readerName();
    }
}
