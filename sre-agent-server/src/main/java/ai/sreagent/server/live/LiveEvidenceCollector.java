
package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
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
 * Collects live evidence from Prometheus, Loki, and Jaeger backends.
 * Each source is independent — a failure in one does not block others.
 * Falls back to fixture clients when live endpoints are unavailable.
 */
public class LiveEvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(LiveEvidenceCollector.class);

    private final String prometheusUrl;
    private final String lokiUrl;
    private final String jaegerUrl;
    private final boolean forceFixture;

    public LiveEvidenceCollector(String prometheusUrl, String lokiUrl, String jaegerUrl,
                                  boolean forceFixture) {
        this.prometheusUrl = prometheusUrl;
        this.lokiUrl = lokiUrl;
        this.jaegerUrl = jaegerUrl;
        this.forceFixture = forceFixture;
    }

    /**
     * Collect evidence from all configured sources.
     */
    public LiveEvidenceReport collect(String service, String namespace, Duration lookback) {
        List<Evidence> allEvidence = new ArrayList<>();
        Map<String, LiveEvidenceReport.SourceReport> sourceReports = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(lookback);

        collectPrometheus(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);
        collectLoki(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);
        collectJaeger(service, namespace, startTime, endTime, lookback, allEvidence, sourceReports, warnings);

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

            if (!forceFixture && prometheusUrl != null && !prometheusUrl.isBlank()) {
                var config = new PrometheusClientConfig(prometheusUrl, Duration.ofSeconds(10), Map.of());
                var client = new HttpPrometheusQueryClient(config);
                if (client.isAvailable()) {
                    provider = new PrometheusEvidenceProvider(client);
                    readerName = "http";
                } else {
                    provider = new PrometheusEvidenceProvider(new FixturePrometheusQueryClient());
                    readerName = "fixture";
                    warnings.add("Prometheus at " + prometheusUrl + " unreachable, using fixture");
                }
            } else {
                provider = new PrometheusEvidenceProvider(new FixturePrometheusQueryClient());
                readerName = "fixture";
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

            reports.put("prometheus", new LiveEvidenceReport.SourceReport(
                    "prometheus", true, promEvidence.size(),
                    promEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Prometheus ({}) collected {} evidence items", readerName, promEvidence.size());

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

            if (!forceFixture && lokiUrl != null && !lokiUrl.isBlank()) {
                var config = new LokiClientConfig(lokiUrl, Duration.ofSeconds(10), Map.of());
                var client = new HttpLokiQueryClient(config);
                if (client.isAvailable()) {
                    provider = new LokiEvidenceProvider(client);
                    readerName = "http";
                } else {
                    provider = new LokiEvidenceProvider(new FixtureLokiQueryClient());
                    readerName = "fixture";
                    warnings.add("Loki at " + lokiUrl + " unreachable, using fixture");
                }
            } else {
                provider = new LokiEvidenceProvider(new FixtureLokiQueryClient());
                readerName = "fixture";
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

            reports.put("loki", new LiveEvidenceReport.SourceReport(
                    "loki", true, lokiEvidence.size(),
                    lokiEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Loki ({}) collected {} evidence items", readerName, lokiEvidence.size());

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

            if (!forceFixture && jaegerUrl != null && !jaegerUrl.isBlank()) {
                var config = new TraceClientConfig(jaegerUrl, "jaeger", Duration.ofSeconds(10), Map.of());
                var client = new HttpTraceQueryClient(config);
                if (client.isAvailable()) {
                    provider = new TraceEvidenceProvider(client);
                    readerName = "http";
                } else {
                    provider = new TraceEvidenceProvider(new FixtureTraceQueryClient());
                    readerName = "fixture";
                    warnings.add("Jaeger at " + jaegerUrl + " unreachable, using fixture");
                }
            } else {
                provider = new TraceEvidenceProvider(new FixtureTraceQueryClient());
                readerName = "fixture";
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

            reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                    "jaeger", true, traceEvidence.size(),
                    traceEvidence.stream().map(Evidence::evidenceType).distinct().toList(), null));
            log.info("Jaeger ({}) collected {} evidence items", readerName, traceEvidence.size());

        } catch (Exception e) {
            log.warn("Jaeger collection failed: {}", e.getMessage());
            warnings.add("Jaeger: " + e.getMessage());
            reports.put("jaeger", new LiveEvidenceReport.SourceReport(
                    "jaeger", false, 0, List.of(), e.getMessage()));
        }
    }
}
