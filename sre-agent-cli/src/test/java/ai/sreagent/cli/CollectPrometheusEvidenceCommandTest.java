package ai.sreagent.cli;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CollectPrometheusEvidenceCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectFixtureEvidence() {
        String outputPath = tempDir.resolve("prometheus_evidence.json").toString();

        int exitCode = new CommandLine(new Main()).execute(
                "collect-prometheus-evidence",
                "--service", "payment-service",
                "--namespace", "demo",
                "--query-type", "LATENCY_P95",
                "--output", outputPath,
                "--reader", "fixture"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(Path.of(outputPath))).isTrue();

        try {
            String content = Files.readString(Path.of(outputPath));
            assertThat(content).contains("metric_latency_p95_spike");
            assertThat(content).contains("prometheus");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldCollectMultipleQueryTypes() {
        String outputPath = tempDir.resolve("prometheus_multi.json").toString();

        int exitCode = new CommandLine(new Main()).execute(
                "collect-prometheus-evidence",
                "--service", "payment-service",
                "--namespace", "demo",
                "--query-type", "ERROR_RATE,LATENCY_P95",
                "--output", outputPath,
                "--reader", "fixture"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(Path.of(outputPath))).isTrue();
    }

    @Test
    void shouldFailWhenHttpWithoutUrl() {
        String outputPath = tempDir.resolve("should_not_exist.json").toString();

        int exitCode = new CommandLine(new Main()).execute(
                "collect-prometheus-evidence",
                "--service", "payment-service",
                "--namespace", "demo",
                "--query-type", "LATENCY_P95",
                "--output", outputPath,
                "--reader", "http"
        );

        // Should fail because no prometheus-url provided
        assertThat(exitCode).isNotEqualTo(0);
    }

    @Test
    void shouldUseDefaultQueryTypes() {
        String outputPath = tempDir.resolve("prometheus_defaults.json").toString();

        int exitCode = new CommandLine(new Main()).execute(
                "collect-prometheus-evidence",
                "--service", "payment-service",
                "--namespace", "demo",
                "--output", outputPath,
                "--reader", "fixture"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(Path.of(outputPath))).isTrue();
    }
}
