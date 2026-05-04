package ai.sreagent.prometheus.mapper;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.prometheus.parser.PrometheusQueryResult;
import ai.sreagent.prometheus.parser.PrometheusSample;
import ai.sreagent.prometheus.query.PrometheusQueryType;

import java.time.Instant;
import java.util.*;

/**
 * Maps Prometheus query results to semantic Evidence objects.
 * Applies threshold-based classification to determine evidence types.
 */
public class PrometheusEvidenceMapper {

    // Thresholds — configurable in future iterations
    private static final double ERROR_RATE_THRESHOLD = 0.05;       // 5% error rate
    private static final double LATENCY_P95_THRESHOLD = 1.0;       // 1 second
    private static final double LATENCY_P99_THRESHOLD = 2.0;       // 2 seconds
    private static final double DOWNSTREAM_LATENCY_THRESHOLD = 1.0; // 1 second
    private static final double MEMORY_USAGE_HIGH_BYTES = 1_073_741_824.0; // 1 GiB
    private static final double CPU_USAGE_THRESHOLD = 0.8;         // 80% of a core
    private static final double RESTART_RATE_THRESHOLD = 0;         // any restart is notable
    private static final double REQUEST_RATE_DROP_FACTOR = 0.5;    // 50% drop (needs baseline)

    /**
     * Map a Prometheus query result to a list of Evidence objects.
     * Returns empty list if no thresholds are exceeded.
     * Returns metric_no_signal evidence if result is empty.
     */
    public List<Evidence> map(PrometheusQueryType queryType,
                              PrometheusQueryResult result,
                              String promql,
                              String incidentId,
                              String service,
                              String namespace,
                              Instant startTime,
                              Instant endTime) {
        if (result.isEmpty()) {
            return List.of(buildNoSignalEvidence(queryType, promql, incidentId, service, namespace, startTime, endTime));
        }

        List<Evidence> evidence = new ArrayList<>();
        for (PrometheusSample sample : result.samples()) {
            Optional<Evidence> e = mapSample(queryType, sample, promql, incidentId, service, namespace, startTime, endTime);
            e.ifPresent(evidence::add);
        }
        return evidence;
    }

    private Optional<Evidence> mapSample(PrometheusQueryType queryType,
                                         PrometheusSample sample,
                                         String promql,
                                         String incidentId,
                                         String service,
                                         String namespace,
                                         Instant startTime,
                                         Instant endTime) {
        return switch (queryType) {
            case ERROR_RATE -> mapErrorRate(sample, promql, incidentId, service, namespace, startTime, endTime);
            case LATENCY_P95 -> mapLatencyP95(sample, promql, incidentId, service, namespace, startTime, endTime);
            case LATENCY_P99 -> mapLatencyP99(sample, promql, incidentId, service, namespace, startTime, endTime);
            case DOWNSTREAM_LATENCY_P95 -> mapDownstreamLatency(sample, promql, incidentId, service, namespace, startTime, endTime);
            case MEMORY_USAGE -> mapMemoryUsage(sample, promql, incidentId, service, namespace, startTime, endTime);
            case CPU_USAGE -> mapCpuUsage(sample, promql, incidentId, service, namespace, startTime, endTime);
            case RESTART_RATE -> mapRestartRate(sample, promql, incidentId, service, namespace, startTime, endTime);
            case REQUEST_RATE -> Optional.empty(); // request rate needs baseline comparison, skip for MVP
        };
    }

    private Optional<Evidence> mapErrorRate(PrometheusSample sample, String promql, String incidentId,
                                            String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < ERROR_RATE_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_ERROR_RATE_SPIKE,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates error rate for " + service + " exceeded threshold ("
                        + formatPercent(value) + " >= " + formatPercent(ERROR_RATE_THRESHOLD) + ").",
                promql, value, ERROR_RATE_THRESHOLD, "ratio",
                sample.labels(), queryStrength(value, ERROR_RATE_THRESHOLD, 0.5)));
    }

    private Optional<Evidence> mapLatencyP95(PrometheusSample sample, String promql, String incidentId,
                                             String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < LATENCY_P95_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_LATENCY_P95_SPIKE,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates p95 latency for " + service + " exceeded threshold ("
                        + formatSeconds(value) + " >= " + formatSeconds(LATENCY_P95_THRESHOLD) + ").",
                promql, value, LATENCY_P95_THRESHOLD, "seconds",
                sample.labels(), queryStrength(value, LATENCY_P95_THRESHOLD, 2.0)));
    }

    private Optional<Evidence> mapLatencyP99(PrometheusSample sample, String promql, String incidentId,
                                             String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < LATENCY_P99_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_LATENCY_P99_SPIKE,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates p99 latency for " + service + " exceeded threshold ("
                        + formatSeconds(value) + " >= " + formatSeconds(LATENCY_P99_THRESHOLD) + ").",
                promql, value, LATENCY_P99_THRESHOLD, "seconds",
                sample.labels(), queryStrength(value, LATENCY_P99_THRESHOLD, 4.0)));
    }

    private Optional<Evidence> mapDownstreamLatency(PrometheusSample sample, String promql, String incidentId,
                                                    String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < DOWNSTREAM_LATENCY_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_DOWNSTREAM_LATENCY_SPIKE,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates downstream p95 latency for " + service + " exceeded threshold ("
                        + formatSeconds(value) + " >= " + formatSeconds(DOWNSTREAM_LATENCY_THRESHOLD) + ").",
                promql, value, DOWNSTREAM_LATENCY_THRESHOLD, "seconds",
                sample.labels(), queryStrength(value, DOWNSTREAM_LATENCY_THRESHOLD, 2.0)));
    }

    private Optional<Evidence> mapMemoryUsage(PrometheusSample sample, String promql, String incidentId,
                                              String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < MEMORY_USAGE_HIGH_BYTES) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_MEMORY_USAGE_HIGH,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates memory usage for " + service + " is high ("
                        + formatBytes(value) + " >= " + formatBytes(MEMORY_USAGE_HIGH_BYTES) + ").",
                promql, value, MEMORY_USAGE_HIGH_BYTES, "bytes",
                sample.labels(), queryStrength(value, MEMORY_USAGE_HIGH_BYTES, 2_147_483_648.0)));
    }

    private Optional<Evidence> mapCpuUsage(PrometheusSample sample, String promql, String incidentId,
                                           String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value < CPU_USAGE_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_CPU_USAGE_HIGH,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates CPU usage for " + service + " is high ("
                        + String.format("%.2f cores", value) + " >= " + String.format("%.2f cores", CPU_USAGE_THRESHOLD) + ").",
                promql, value, CPU_USAGE_THRESHOLD, "cores",
                sample.labels(), queryStrength(value, CPU_USAGE_THRESHOLD, 1.6)));
    }

    private Optional<Evidence> mapRestartRate(PrometheusSample sample, String promql, String incidentId,
                                              String service, String namespace, Instant startTime, Instant endTime) {
        double value = sample.value();
        if (Double.isNaN(value) || value <= RESTART_RATE_THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(buildEvidence(
                PrometheusEvidenceTypes.METRIC_RESTART_RATE_INCREASED,
                incidentId, service, namespace, sample.timestamp(), startTime, endTime,
                "Prometheus indicates restart count for " + service + " increased ("
                        + String.format("%.0f restarts", value) + " in last 10m).",
                promql, value, RESTART_RATE_THRESHOLD, "count",
                sample.labels(), Math.min(1.0, 0.5 + value * 0.1)));
    }

    private Evidence buildNoSignalEvidence(PrometheusQueryType queryType, String promql, String incidentId,
                                           String service, String namespace, Instant startTime, Instant endTime) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("queryType", queryType.getKey());
        attrs.put("promql", promql != null ? promql : "");
        attrs.put("service", service != null ? service : "unknown");
        attrs.put("namespace", namespace != null ? namespace : "default");
        if (startTime != null) attrs.put("startTime", startTime.toString());
        if (endTime != null) attrs.put("endTime", endTime.toString());

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                PrometheusEvidenceTypes.SOURCE,
                PrometheusEvidenceTypes.METRIC_NO_SIGNAL,
                service,
                Instant.now(),
                "Prometheus returned no data for " + queryType.getKey() + " query on " + service + ".",
                attrs,
                0.1
        );
    }

    private Evidence buildEvidence(String evidenceType, String incidentId, String service, String namespace,
                                   Instant sampleTimestamp, Instant startTime, Instant endTime,
                                   String content, String promql, double value, double threshold,
                                   String unit, Map<String, String> seriesLabels, double strength) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("promql", promql != null ? promql : "");
        attrs.put("value", value);
        attrs.put("threshold", threshold);
        attrs.put("unit", unit);
        attrs.put("service", service != null ? service : "unknown");
        attrs.put("namespace", namespace != null ? namespace : "default");
        if (startTime != null) attrs.put("startTime", startTime.toString());
        if (endTime != null) attrs.put("endTime", endTime.toString());
        if (sampleTimestamp != null) attrs.put("sampleTimestamp", sampleTimestamp.toString());
        attrs.put("seriesLabels", seriesLabels);

        return new Evidence(
                UUID.randomUUID().toString(),
                incidentId,
                PrometheusEvidenceTypes.SOURCE,
                evidenceType,
                service,
                sampleTimestamp != null ? sampleTimestamp : Instant.now(),
                content,
                attrs,
                Math.round(strength * 100.0) / 100.0
        );
    }

    /**
     * Calculate evidence strength based on how far value exceeds threshold.
     * Returns value in [0.3, 1.0] range.
     */
    private double queryStrength(double value, double threshold, double maxExpected) {
        if (threshold <= 0) return 0.7;
        double ratio = value / threshold;
        // Linear interpolation from 0.3 (at threshold) to 1.0 (at maxExpected/threshold)
        double normalized = (ratio - 1.0) / ((maxExpected / threshold) - 1.0);
        return Math.max(0.3, Math.min(1.0, 0.3 + normalized * 0.7));
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private String formatSeconds(double value) {
        if (value >= 1.0) {
            return String.format("%.2fs", value);
        }
        return String.format("%.0fms", value * 1000);
    }

    private String formatBytes(double value) {
        if (value >= 1_073_741_824.0) {
            return String.format("%.2f GiB", value / 1_073_741_824.0);
        }
        if (value >= 1_048_576.0) {
            return String.format("%.2f MiB", value / 1_048_576.0);
        }
        return String.format("%.0f bytes", value);
    }
}
