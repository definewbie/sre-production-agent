
package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEvidenceReportTest {

    private Evidence makeEvidence(String id, String source) {
        return new Evidence(id, "inc-1", source, "test_type", "svc", Instant.now(), "test", Map.of(), 0.5);
    }

    @Test
    void report_holdsCorrectCounts() {
        Evidence e1 = makeEvidence("ev-1", "prometheus");
        Evidence e2 = makeEvidence("ev-2", "loki");
        List<Evidence> evidence = List.of(e1, e2);

        Map<String, LiveEvidenceReport.SourceReport> sources = new LinkedHashMap<>();
        sources.put("prometheus", new LiveEvidenceReport.SourceReport("prometheus", true, 1, List.of("metric_test"), null));
        sources.put("loki", new LiveEvidenceReport.SourceReport("loki", true, 1, List.of("log_test"), null));

        LiveEvidenceReport report = new LiveEvidenceReport(2, evidence, sources, List.of());

        assertThat(report.totalEvidenceCount()).isEqualTo(2);
        assertThat(report.allEvidence()).hasSize(2);
        assertThat(report.sources()).hasSize(2);
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void sourceReport_tracksAvailability() {
        LiveEvidenceReport.SourceReport available = new LiveEvidenceReport.SourceReport("prometheus", true, 3, List.of("a", "b"), null);
        assertThat(available.available()).isTrue();
        assertThat(available.evidenceCount()).isEqualTo(3);
        assertThat(available.evidenceTypes()).containsExactly("a", "b");

        LiveEvidenceReport.SourceReport unavailable = new LiveEvidenceReport.SourceReport("loki", false, 0, List.of(), "connection refused");
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.error()).isEqualTo("connection refused");
    }
}
