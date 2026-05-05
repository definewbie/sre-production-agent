package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.k8s.KubernetesResourceReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Kubernetes source is properly integrated into LiveEvidenceCollector.
 * Covers: live mode failure, simulate mode fixture, source report, evidence types.
 */
class LiveEvidenceCollectorKubernetesTest {

    /**
     * Stub reader that simulates an unavailable Kubernetes cluster.
     * isAvailable() returns false — used to test live mode failure handling.
     */
    static class UnavailableStubReader implements KubernetesResourceReader {
        @Override
        public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
            throw new IOException("Kubernetes cluster not accessible");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String readerName() {
            return "unavailable-stub";
        }
    }

    /**
     * Stub reader that simulates a healthy Kubernetes cluster.
     * Returns realistic pod and deployment JSON.
     */
    static class HealthyStubReader implements KubernetesResourceReader {
        @Override
        public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
            if ("pods".equals(resourceType)) {
                return """
                {
                  "items": [{
                    "metadata": {"name": "order-service-abc123", "namespace": "demo"},
                    "status": {
                      "phase": "Running",
                      "containerStatuses": [{"name": "order-service", "restartCount": 0, "ready": true}]
                    }
                  }]
                }
                """;
            }
            if ("deployments".equals(resourceType)) {
                return """
                {
                  "metadata": {"name": "order-service", "namespace": "demo"},
                  "spec": {"replicas": 3},
                  "status": {"readyReplicas": 3, "availableReplicas": 3, "updatedReplicas": 3}
                }
                """;
            }
            if ("events".equals(resourceType)) {
                return """
                {"items": []}
                """;
            }
            return "{\"items\": []}";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String readerName() {
            return "healthy-stub";
        }
    }

    /**
     * Stub reader that simulates fixture behavior (returns data, isAvailable=true).
     * Used instead of real FixtureKubernetesResourceReader to avoid classpath dependency
     * on k8s-provider test resources.
     */
    static class SimulateFixtureStubReader implements KubernetesResourceReader {
        @Override
        public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
            if ("pods".equals(resourceType)) {
                return """
                {
                  "items": [{
                    "metadata": {"name": "order-service-fix", "namespace": "demo"},
                    "status": {
                      "phase": "Running",
                      "containerStatuses": [{"name": "order-service", "restartCount": 0, "ready": true}]
                    }
                  }]
                }
                """;
            }
            if ("deployments".equals(resourceType)) {
                return """
                {
                  "metadata": {"name": "order-service", "namespace": "demo"},
                  "spec": {"replicas": 3},
                  "status": {"readyReplicas": 3, "availableReplicas": 3, "updatedReplicas": 3}
                }
                """;
            }
            return "{\"items\": []}";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String readerName() {
            return "simulate-fixture-stub";
        }
    }

    // --- Test 1: live mode + Kubernetes unavailable → source failed, no fixture fallback ---

    @Test
    void liveMode_kubernetesUnavailable_noFixtureFallback() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999", "http://localhost:19998", "http://localhost:19997",
                false, new UnavailableStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        // Kubernetes must be in source report
        LiveEvidenceReport.SourceReport k8sReport = report.sources().get("kubernetes");
        assertThat(k8sReport).as("kubernetes source report must exist").isNotNull();
        assertThat(k8sReport.available()).as("kubernetes should be unavailable").isFalse();
        assertThat(k8sReport.error()).as("kubernetes should have error message").isNotNull();
        assertThat(k8sReport.evidenceCount()).isEqualTo(0);

        // No Kubernetes evidence in the list
        boolean hasK8sEvidence = report.allEvidence().stream()
                .anyMatch(e -> "kubernetes".equals(e.source()));
        assertThat(hasK8sEvidence).as("No kubernetes evidence should be present when unavailable").isFalse();
    }

    // --- Test 2: simulate mode + fixture reader → Kubernetes evidence collected ---

    @Test
    void simulateMode_kubernetesFixture_evidenceCollected() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                null, null, null, true, new SimulateFixtureStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        // Fixture reader should produce Kubernetes evidence
        LiveEvidenceReport.SourceReport k8sReport = report.sources().get("kubernetes");
        assertThat(k8sReport).as("kubernetes source report must exist").isNotNull();
        assertThat(k8sReport.available()).as("kubernetes fixture should be available").isTrue();
        assertThat(k8sReport.evidenceCount()).as("kubernetes fixture should produce evidence").isGreaterThan(0);
    }

    // --- Test 3: live mode + healthy Kubernetes → evidence with correct types ---

    @Test
    void liveMode_kubernetesHealthy_evidenceHasCorrectTypes() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999", "http://localhost:19998", "http://localhost:19997",
                false, new HealthyStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        LiveEvidenceReport.SourceReport k8sReport = report.sources().get("kubernetes");
        assertThat(k8sReport).isNotNull();
        assertThat(k8sReport.available()).isTrue();
        assertThat(k8sReport.evidenceCount()).isGreaterThan(0);

        // Kubernetes evidence should contain deployment_metadata
        boolean hasDeploymentMetadata = report.allEvidence().stream()
                .anyMatch(e -> "kubernetes".equals(e.source())
                        && "deployment_metadata".equals(e.evidenceType()));
        assertThat(hasDeploymentMetadata).as("Should have deployment_metadata from Kubernetes").isTrue();
    }

    // --- Test 4: Kubernetes source failure produces warning ---

    @Test
    void kubernetesSourceFailure_producesWarning() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999", "http://localhost:19998", "http://localhost:19997",
                false, new UnavailableStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        boolean hasK8sWarning = report.warnings().stream()
                .anyMatch(w -> w.contains("Kubernetes"));
        assertThat(hasK8sWarning).as("Should have a Kubernetes warning when unavailable").isTrue();
    }

    // --- Test 5: Kubernetes evidence is not _no_signal ---

    @Test
    void kubernetesEvidence_notNoSignal() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999", "http://localhost:19998", "http://localhost:19997",
                false, new HealthyStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        List<Evidence> k8sEvidence = report.allEvidence().stream()
                .filter(e -> "kubernetes".equals(e.source()))
                .toList();

        for (Evidence e : k8sEvidence) {
            assertThat(e.evidenceType())
                    .as("Kubernetes evidence type should not end with _no_signal")
                    .doesNotEndWith("_no_signal");
        }
    }

    // --- Test 6: four sources reported (prometheus, loki, jaeger, kubernetes) ---

    @Test
    void allFourSources_reported() {
        LiveEvidenceCollector collector = new LiveEvidenceCollector(
                "http://localhost:19999", "http://localhost:19998", "http://localhost:19997",
                false, new HealthyStubReader());

        LiveEvidenceReport report = collector.collect("order-service", "demo", Duration.ofMinutes(5));

        assertThat(report.sources()).containsKey("prometheus");
        assertThat(report.sources()).containsKey("loki");
        assertThat(report.sources()).containsKey("jaeger");
        assertThat(report.sources()).containsKey("kubernetes");
        assertThat(report.sources()).as("Should have exactly 4 source reports").hasSize(4);
    }
}
