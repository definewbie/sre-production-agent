package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that source failures are correctly reported in LiveEvidenceReport
 * and that no_signal evidence is properly handled.
 */
class LiveEvidenceCollectorFailureTest {

    private Evidence makeEvidence(String id, String type, String source) {
        return new Evidence(id, "inc-1", source, type, "svc", Instant.now(), "test", Map.of(), 0.5);
    }

    @Test
    void failedSource_reportedInSourceReport() {
        LiveEvidenceReport.SourceReport failed = new LiveEvidenceReport.SourceReport(
                "prometheus", false, 0, List.of(), "connection refused");
        assertThat(failed.available()).isFalse();
        assertThat(failed.error()).isEqualTo("connection refused");
        assertThat(failed.evidenceCount()).isEqualTo(0);
    }

    @Test
    void noSignalEvidence_excludedFromEffectiveCount() {
        // Evidence with _no_signal suffix should be identifiable
        Evidence noSignal = makeEvidence("ev-1", "metric_latency_p95_no_signal", "prometheus");
        Evidence real = makeEvidence("ev-2", "metric_latency_p95_spike", "prometheus");

        assertThat(noSignal.evidenceType()).endsWith("_no_signal");
        assertThat(real.evidenceType()).doesNotEndWith("_no_signal");

        // no_signal should not be counted as effective
        List<Evidence> all = List.of(noSignal, real);
        long effectiveCount = all.stream()
                .filter(e -> !e.evidenceType().endsWith("_no_signal"))
                .count();
        assertThat(effectiveCount).isEqualTo(1);
    }

    @Test
    void emptyReport_withWarnings() {
        List<String> warnings = List.of("Prometheus unreachable", "Loki unreachable");
        LiveEvidenceReport report = LiveEvidenceReport.withWarnings(warnings);
        assertThat(report.totalEvidenceCount()).isEqualTo(0);
        assertThat(report.allEvidence()).isEmpty();
        assertThat(report.warnings()).hasSize(2);
    }

    @Test
    void sourceReport_mixedAvailability() {
        Map<String, LiveEvidenceReport.SourceReport> sources = new LinkedHashMap<>();
        sources.put("prometheus", new LiveEvidenceReport.SourceReport("prometheus", false, 0, List.of(), "unreachable"));
        sources.put("loki", new LiveEvidenceReport.SourceReport("loki", true, 5, List.of("log_timeout_error"), null));
        sources.put("jaeger", new LiveEvidenceReport.SourceReport("jaeger", false, 0, List.of(), "timeout"));

        LiveEvidenceReport report = new LiveEvidenceReport(5, List.of(), sources, List.of());

        // Prometheus failed
        assertThat(report.sources().get("prometheus").available()).isFalse();
        assertThat(report.sources().get("prometheus").error()).isNotNull();

        // Loki succeeded
        assertThat(report.sources().get("loki").available()).isTrue();
        assertThat(report.sources().get("loki").evidenceCount()).isEqualTo(5);

        // Jaeger failed
        assertThat(report.sources().get("jaeger").available()).isFalse();
    }
}
