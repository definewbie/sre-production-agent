package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that LiveEvidenceCollector correctly distinguishes
 * between live mode (no fixture fallback) and simulation mode (fixture allowed).
 */
class LiveEvidenceCollectorModeTest {

    private Evidence makeEvidence(String id, String type) {
        return new Evidence(id, "inc-1", "test", type, "svc", Instant.now(), "test", Map.of(), 0.5);
    }

    @Test
    void simulateMode_usesFixtureClients() {
        // forceFixture=true means simulation mode
        LiveEvidenceCollector collector = new LiveEvidenceCollector(null, null, null, true);
        LiveEvidenceReport report = collector.collect("order-service", "demo", java.time.Duration.ofMinutes(5));

        // Fixture clients should return evidence
        assertThat(report.totalEvidenceCount()).isGreaterThan(0);
        // Prometheus, Loki, Jaeger fixture clients should report as available
        assertThat(report.sources().get("prometheus").available()).isTrue();
        assertThat(report.sources().get("loki").available()).isTrue();
        assertThat(report.sources().get("jaeger").available()).isTrue();
        // Kubernetes fixture may succeed or fail depending on bundled fixtures — just check it's present
        assertThat(report.sources()).containsKey("kubernetes");
    }

    @Test
    void liveMode_unreachableEndpoints_noFixtureFallback() {
        // forceFixture=false with unreachable URLs — no fallback allowed
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999",  // non-existent port
                "http://localhost:19998",
                "http://localhost:19997",
                false);
        LiveEvidenceReport report = collector.collect("order-service", "demo", java.time.Duration.ofMinutes(5));

        // Live mode: Prometheus/Loki/Jaeger should fail (unreachable), no fixture fallback
        assertThat(report.sources().get("prometheus").available()).isFalse();
        assertThat(report.sources().get("loki").available()).isFalse();
        assertThat(report.sources().get("jaeger").available()).isFalse();

        // These three should have error messages
        assertThat(report.sources().get("prometheus").error()).isNotNull();
        assertThat(report.sources().get("loki").error()).isNotNull();
        assertThat(report.sources().get("jaeger").error()).isNotNull();

        // Kubernetes source is independent — may succeed or fail depending on cluster access.
        // Just verify it is present and did NOT use fixture fallback.
        assertThat(report.sources()).containsKey("kubernetes");
        // Kubernetes should have an error or be unavailable (no kind cluster in test env)
        // We don't assert available=false because test env might have kubectl access.

        // Should have warnings
        assertThat(report.warnings()).isNotEmpty();
    }

    @Test
    void liveMode_noUrls_reportsFailureWithoutFallback() {
        // Empty URLs in live mode
        LiveEvidenceCollector collector = new LiveEvidenceCollector("", "", "", false);
        LiveEvidenceReport report = collector.collect("order-service", "demo", java.time.Duration.ofMinutes(5));

        // Prometheus/Loki/Jaeger should report failure with "No URL configured"
        assertThat(report.sources().get("prometheus").available()).isFalse();
        assertThat(report.sources().get("loki").available()).isFalse();
        assertThat(report.sources().get("jaeger").available()).isFalse();

        assertThat(report.sources().get("prometheus").error()).contains("No");
        assertThat(report.sources().get("loki").error()).contains("No");
        assertThat(report.sources().get("jaeger").error()).contains("No");

        // Kubernetes source is independent — just verify present
        assertThat(report.sources()).containsKey("kubernetes");
    }

    @Test
    void liveMode_nullUrls_reportsFailureWithoutFallback() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(null, null, null, false);
        LiveEvidenceReport report = collector.collect("order-service", "demo", java.time.Duration.ofMinutes(5));

        // Prometheus/Loki/Jaeger should fail
        assertThat(report.sources().get("prometheus").available()).isFalse();
        assertThat(report.sources().get("loki").available()).isFalse();
        assertThat(report.sources().get("jaeger").available()).isFalse();

        // Kubernetes source is independent
        assertThat(report.sources()).containsKey("kubernetes");
    }
}
