package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.k8s.*;
import ai.sreagent.loki.LokiEvidenceProvider;
import ai.sreagent.loki.LokiEvidenceRequest;
import ai.sreagent.loki.LokiEvidenceResult;
import ai.sreagent.loki.client.FixtureLokiQueryClient;
import ai.sreagent.loki.client.HttpLokiQueryClient;
import ai.sreagent.loki.client.LokiClientConfig;
import ai.sreagent.loki.query.LokiQueryType;
import ai.sreagent.prometheus.PrometheusEvidenceProvider;
import ai.sreagent.prometheus.PrometheusEvidenceRequest;
import ai.sreagent.prometheus.PrometheusEvidenceResult;
import ai.sreagent.prometheus.client.FixturePrometheusQueryClient;
import ai.sreagent.prometheus.client.HttpPrometheusQueryClient;
import ai.sreagent.prometheus.client.PrometheusClientConfig;
import ai.sreagent.prometheus.query.PrometheusQueryType;
import ai.sreagent.trace.TraceEvidenceProvider;
import ai.sreagent.trace.TraceEvidenceRequest;
import ai.sreagent.trace.TraceEvidenceResult;
import ai.sreagent.trace.client.FixtureTraceQueryClient;
import ai.sreagent.trace.client.HttpTraceQueryClient;
import ai.sreagent.trace.client.TraceClientConfig;
import ai.sreagent.trace.query.TraceQueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Collects live evidence from Prometheus, Loki, Jaeger, and Kubernetes backends.
 * Each source is independent — a failure in one does not block others.
 *
 * Mode behavior:
 * - simulate mode (forceFixture=true): uses fixture clients exclusively.
 * - live mode (forceFixture=false): uses HTTP clients / Kubernetes API only.
 *   If an endpoint is unreachable, the source is marked as failed
 *   with NO fallback to fixture data.
 */
public class LiveEvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(LiveEvidenceCollector.class);

    private final String prometheusUrl;
    private final String lokiUrl;
    private final String jaegerUrl;
    private final boolean forceFixture;

    // Kubernetes configuration — live mode uses Java client or kubectl;
    // simulate mode uses fixture reader.
    private final KubernetesResourceReader kubernetesReaderOverride;

    public LiveEvidenceCollector(String prometheusUrl, String lokiUrl, String jaegerUrl,
                                  boolean forceFixture) {
        this(prometheusUrl, lokiUrl, jaegerUrl, forceFixture, null);
    }

    public LiveEvidenceCollector(String prometheusUrl, String lokiUrl, String jaegerUrl,
                                  boolean forceFixture,
                                  KubernetesResourceReader kubernetesReaderOverride) {
        this.prometheusUrl = prometheusUrl;
        this.lokiUrl = lokiUrl;
        this.jaegerUrl = jaegerUrl;
        this.forceFixture = forceFixture;
        this.kubernetesReaderOverride = kubernetesReaderOverride;
    }

    /**
     * Collect evidence from all configured sources.
     * Uses Instant.now() as the time anchor — suitable for real-time queries.
     */
    public LiveEvidenceReport collect(String service, String namespace, Duration lookback) {
        return collect(service, namespace, lookback, null);
    }

    /**
     * Collect evidence from all configured sources, anchored at a specific time.
     *
     * When anchorTime is non-null, the query window is [anchorTime - lookback, anchorTime].
     * This ensures evidence queries cover the period when the fault was actually active,
     * rather than the current time (which may be well after the fault has ended).
     *
     * When anchorTime is null (e.g. from the legacy collect() signature), falls back
     * to Instant.now() for backward compatibility.
     *
     * @param anchorTime the moment when the incident started / fault was injected;
     *                   null to use Instant.now()
     */
    public LiveEvidenceReport collect(String service, String namespace, Duration lookback, Instant anchorTime) {
        List<Evidence> allEvidence = new ArrayList<>();
        Map<String, LiveEvidenceReport.SourceReport> sourceReports = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        Instant now = Instant.now();
        Instant endTime;
        if (anchorTime != null) {
            Instant buffered = anchorTime.plusSeconds(30);
            endTime = buffered.isBefore(now) ? buffered : now;  // 30s buffer, capped at now
        } else {
            endTime = now;
        }
        Instant startTime = endTime.minus(lookback);

        collectPrometheus(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);
        collectLoki(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);
        collectJaeger(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);
        collectKubernetes(service, namespace, allEvidence, sourceReports, warnings);

        return new LiveEvidenceReport(allEvidence.size(), List.copyOf(allEvidence),
                Map.copyOf(sourceReports), List.copyOf(warnings));
    }

    private void collectPrometheus(String service, String namespace, Instant start, Instant end,
                                    Duration lookback, List<Evidence> allEvidence,
                                    Map<String, LiveEvidenceReport.SourceReport> reports,
                                    List<String> warnings) {
        try {
            List<PrometheusQueryType> queryTypes = List.of(
                    PrometheusQueryType.ERROR_RATE,
                    PrometheusQueryType.LATENCY_P95,
                    PrometheusQueryType.DOWNSTREAM_LATENCY_P95,
                    PrometheusQueryType.REQUEST_RATE,
                    PrometheusQueryType.MEMORY_USAGE,
                    PrometheusQueryType.CPU_USAGE,
                    PrometheusQueryType.RESTART_RATE
            );

            PrometheusEvidenceProvider provider;
            String readerName;

            if (forceFixture) {
                provider = new PrometheusEvidenceProvider(new FixturePrometheusQueryClient());
                readerName = "fixture";
            } else {
                if (prometheusUrl == null || prometheusUrl.isBlank()) {
                    reports.put("prometheus", new LiveEvidenceReport.SourceReport(
                            "prometheus", false, 0, List.of(), "No Prometheus URL configured"));
                    warnings.add("Prometheus: No URL configured");
                    return;
                }
                var config = new PrometheusClientConfig(prometheusUrl, Duration.ofSeconds(10), Map.of());
                var client = new HttpPrometheusQueryClient(config);
                if (!client.isAvailable()) {
                    reports.put("prometheus", new LiveEvidenceReport.SourceReport(
                            "prometheus", false, 0, List.of(),
                            "Prometheus at " + prometheusUrl + " unreachable (live mode, no fixture fallback)"));
                    warnings.add("Prometheus at " + prometheusUrl + " unreachable (live mode)");
                    return;
                }
                provider = new PrometheusEvidenceProvider(client);
                readerName = "http";
            }

            PrometheusEvidenceRequest request = PrometheusEvidenceRequest.builder()
                    .incidentId("live-" + System.currentTimeMillis())
                    .service(service).namespace(namespace)
                    .startTime(start).endTime(end)
                    .lookback(lookback)
                    .queryTypes(queryTypes).build();

            PrometheusEvidenceResult result = provider.collect(request);
            List<Evidence> promEvidence = result.evidence();
            allEvidence.addAll(promEvidence);

            long effectiveCount = promEvidence.stream()
                    .filter(e -> !e.evidenceType().endsWith("_no_signal"))
                    .count();

            reports.put("prometheus", new LiveEvidenceReport.SourceReport(
                    "prometheus", true, promEvidence.size(),
                    promEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Prometheus ({}) collected {} evidence items ({} effective)",
                    readerName, promEvidence.size(), effectiveCount);

        } catch (Exception e) {
            log.warn("Prometheus collection failed: {}", e.getMessage());
            warnings.add("Prometheus: " + e.getMessage());
            reports.put("prometheus", new LiveEvidenceReport.SourceReport(
                    "prometheus", false, 0, List.of(), e.getMessage()));
        }
    }

    private void collectLoki(String service, String namespace, Instant start, Instant end,
                              Duration lookback, List<Evidence> allEvidence,
                              Map<String, LiveEvidenceReport.SourceReport> reports,
                              List<String> warnings) {
        try {
            List<LokiQueryType> queryTypes = List.of(
                    LokiQueryType.TIMEOUT_ERROR,
                    LokiQueryType.DOWNSTREAM_TIMEOUT,
                    LokiQueryType.EXCEPTION_LOGS,
                    LokiQueryType.HTTP_5XX_LOGS
            );

            LokiEvidenceProvider provider;
            String readerName;

            if (forceFixture) {
                provider = new LokiEvidenceProvider(new FixtureLokiQueryClient());
                readerName = "fixture";
            } else {
                if (lokiUrl == null || lokiUrl.isBlank()) {
                    reports.put("loki", new LiveEvidenceReport.SourceReport(
                            "loki", false, 0, List.of(), "No Loki URL configured"));
                    warnings.add("Loki: No URL configured");
                    return;
                }
                var config = new LokiClientConfig(lokiUrl, Duration.ofSeconds(10), Map.of());
                var client = new HttpLokiQueryClient(config);
                if (!client.isAvailable()) {
                    reports.put("loki", new LiveEvidenceReport.SourceReport(
                            "loki", false, 0, List.of(),
                            "Loki at " + lokiUrl + " unreachable (live mode, no fixture fallback)"));
                    warnings.add("Loki at " + lokiUrl + " unreachable (live mode)");
                    return;
                }
                provider = new LokiEvidenceProvider(client);
                readerName = "http";
            }

            LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                    .incidentId("live-" + System.currentTimeMillis())
                    .service(service).namespace(namespace)
                    .startTime(start).endTime(end)
                    .lookback(lookback)
                    .queryTypes(queryTypes).build();

            LokiEvidenceResult result = provider.collect(request);
            List<Evidence> lokiEvidence = result.evidence();
            allEvidence.addAll(lokiEvidence);

            long effectiveCount = lokiEvidence.stream()
                    .filter(e -> !e.evidenceType().endsWith("_no_signal"))
                    .count();

            reports.put("loki", new LiveEvidenceReport.SourceReport(
                    "loki", true, lokiEvidence.size(),
                    lokiEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Loki ({}) collected {} evidence items ({} effective)",
                    readerName, lokiEvidence.size(), effectiveCount);

        } catch (Exception e) {
            log.warn("Loki collection failed: {}", e.getMessage());
            warnings.add("Loki: " + e.getMessage());
            reports.put("loki", new LiveEvidenceReport.SourceReport(
                    "loki", false, 0, List.of(), e.getMessage()));
        }
    }

    private void collectJaeger(String service, String namespace, Instant start, Instant end,
                                Duration lookback, List<Evidence> allEvidence,
                                Map<String, LiveEvidenceReport.SourceReport> reports,
                                List<String> warnings) {
        try {
            List<TraceQueryType> queryTypes = List.of(
                    TraceQueryType.DOWNSTREAM_SLOW_SPAN,
                    TraceQueryType.ERROR_SPAN,
                    TraceQueryType.ROOT_SPAN_SLOW,
                    TraceQueryType.TIMEOUT_SPAN
            );

            TraceEvidenceProvider provider;
            String readerName;

            if (forceFixture) {
                provider = new TraceEvidenceProvider(new FixtureTraceQueryClient());
                readerName = "fixture";
            } else {
                if (jaegerUrl == null || jaegerUrl.isBlank()) {
                    reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                            "jaeger", false, 0, List.of(), "No Jaeger URL configured"));
                    warnings.add("Jaeger: No URL configured");
                    return;
                }
                var config = new TraceClientConfig(jaegerUrl, "jaeger", Duration.ofSeconds(10), Map.of());
                var client = new HttpTraceQueryClient(config);
                if (!client.isAvailable()) {
                    reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                            "jaeger", false, 0, List.of(),
                            "Jaeger at " + jaegerUrl + " unreachable (live mode, no fixture fallback)"));
                    warnings.add("Jaeger at " + jaegerUrl + " unreachable (live mode)");
                    return;
                }
                provider = new TraceEvidenceProvider(client);
                readerName = "http";
            }

            TraceEvidenceRequest request = TraceEvidenceRequest.builder()
                    .incidentId("live-" + System.currentTimeMillis())
                    .service(service).namespace(namespace)
                    .startTime(start).endTime(end)
                    .lookback(lookback)
                    .queryTypes(queryTypes).build();

            TraceEvidenceResult result = provider.collect(request);
            List<Evidence> traceEvidence = result.evidence();
            allEvidence.addAll(traceEvidence);

            long effectiveCount = traceEvidence.stream()
                    .filter(e -> !e.evidenceType().endsWith("_no_signal"))
                    .count();

            reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                    "jaeger", true, traceEvidence.size(),
                    traceEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Jaeger ({}) collected {} evidence items ({} effective)",
                    readerName, traceEvidence.size(), effectiveCount);

        } catch (Exception e) {
            log.warn("Jaeger collection failed: {}", e.getMessage());
            warnings.add("Jaeger: " + e.getMessage());
            reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                    "jaeger", false, 0, List.of(), e.getMessage()));
        }
    }

    /**
     * Collect Kubernetes runtime evidence: pod status, deployment metadata, events.
     * This provides runtime context that helps exclude false hypotheses like
     * pod_oom_killed and pod_crash_loop when pods are actually healthy.
     */
    private void collectKubernetes(String service, String namespace,
                                    List<Evidence> allEvidence,
                                    Map<String, LiveEvidenceReport.SourceReport> reports,
                                    List<String> warnings) {
        try {
            KubernetesResourceReader reader;
            String readerName;

            if (forceFixture) {
                reader = kubernetesReaderOverride != null
                        ? kubernetesReaderOverride
                        : new FixtureKubernetesResourceReader();
                readerName = reader.readerName();
            } else {
                // Live mode: use override if provided, otherwise try Java client then kubectl
                if (kubernetesReaderOverride != null) {
                    reader = kubernetesReaderOverride;
                } else {
                    reader = createLiveKubernetesReader();
                }
                readerName = reader.readerName();

                if (!reader.isAvailable()) {
                    reports.put("kubernetes", new LiveEvidenceReport.SourceReport(
                            "kubernetes", false, 0, List.of(),
                            "Kubernetes cluster unreachable via " + readerName
                                    + " (live mode, no fixture fallback)"));
                    warnings.add("Kubernetes: cluster unreachable via " + readerName);
                    return;
                }
            }

            KubernetesEvidenceProvider k8sProvider = new KubernetesEvidenceProvider(reader);

            // Build a lightweight IncidentTask for the Kubernetes collection
            String incidentId = "live-k8s-" + System.currentTimeMillis();
            IncidentTask k8sIncident = new IncidentTask(
                    incidentId, "K8sRuntimeCheck", service, namespace,
                    "info", Instant.now(), Map.of(), Map.of());

            // Collect semantic evidence (pod readiness, restart count, deployment metadata)
            List<Evidence> k8sEvidence = k8sProvider.collectSemanticEvidence(k8sIncident);
            allEvidence.addAll(k8sEvidence);

            long effectiveCount = k8sEvidence.stream()
                    .filter(e -> !e.evidenceType().endsWith("_no_signal"))
                    .count();

            reports.put("kubernetes", new LiveEvidenceReport.SourceReport(
                    "kubernetes", true, k8sEvidence.size(),
                    k8sEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Kubernetes ({}) collected {} evidence items ({} effective)",
                    readerName, k8sEvidence.size(), effectiveCount);

        } catch (Exception e) {
            log.warn("Kubernetes collection failed: {}", e.getMessage());
            warnings.add("Kubernetes: " + e.getMessage());
            reports.put("kubernetes", new LiveEvidenceReport.SourceReport(
                    "kubernetes", false, 0, List.of(), e.getMessage()));
        }
    }

    /**
     * Create a live Kubernetes reader. Tries Java client first (production),
     * falls back to kubectl (local development / kind).
     * This method chooses the reader implementation — it does NOT fall back to fixture.
     */
    private KubernetesResourceReader createLiveKubernetesReader() {
        // Try Java client first (works with kubeconfig or in-cluster)
        try {
            KubernetesClientConfig config = KubernetesClientConfig.defaults();
            JavaClientKubernetesResourceReader javaReader = new JavaClientKubernetesResourceReader(config);
            if (javaReader.isAvailable()) {
                log.info("Using Java Kubernetes client for live evidence collection");
                return javaReader;
            }
        } catch (UnsupportedClassVersionError e) {
            log.debug("Java Kubernetes client requires preview features: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Java Kubernetes client not available: {}", e.getMessage());
        }

        // Fall back to kubectl (for local kind / minikube development)
        try {
            KubectlKubernetesResourceReader kubectlReader = new KubectlKubernetesResourceReader();
            if (kubectlReader.isAvailable()) {
                log.info("Using kubectl for live Kubernetes evidence collection");
                return kubectlReader;
            }
        } catch (Exception e) {
            log.debug("kubectl not available: {}", e.getMessage());
        }

        // Neither available — return a reader that reports unavailable
        // This is NOT a fixture fallback — it's a "no cluster access" signal
        return new UnavailableKubernetesResourceReader();
    }

    /**
     * Sentinel KubernetesResourceReader that always reports as unavailable.
     * Used when no real Kubernetes access is possible (live mode).
     */
    private static class UnavailableKubernetesResourceReader implements KubernetesResourceReader {
        @Override
        public String readResource(String resourceType, String name, String namespace, java.util.Map<String, String> labels) {
            throw new UnsupportedOperationException("No Kubernetes cluster access available");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String readerName() {
            return "unavailable";
        }
    }
}
