package ai.sreagent.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI test for Scenario F (K8s CrashLoopBackOff).
 * Verifies the investigate command works with K8s alert/evidence.
 */
class CliScenarioFTest {

    @TempDir
    Path tempDir;

    private File alertFile;
    private File evidenceFile;
    private File outputFile;

    @BeforeEach
    void setUp() throws Exception {
        alertFile = tempDir.resolve("k8s_alert.json").toFile();
        evidenceFile = tempDir.resolve("k8s_evidence.json").toFile();
        outputFile = tempDir.resolve("k8s_report.md").toFile();

        try (var alertIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_alert.json");
             var evidenceIs = getClass().getResourceAsStream("/scenarios/k8s_crashloop_evidence.json")) {
            assertThat(alertIs).as("k8s_crashloop_alert.json must be on classpath").isNotNull();
            assertThat(evidenceIs).as("k8s_crashloop_evidence.json must be on classpath").isNotNull();
            Files.writeString(alertFile.toPath(), new String(alertIs.readAllBytes()));
            Files.writeString(evidenceFile.toPath(), new String(evidenceIs.readAllBytes()));
        }
    }

    @Test
    void investigateScenarioFReturnsSuccess() {
        int exitCode = runInvestigate();
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void reportContainsPodCrashLoop() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("容器崩溃循环");
    }

    @Test
    void reportContainsProbableRootCause() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("可能根因");
    }

    @Test
    void reportContainsCrashLoopBackOff() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).containsIgnoringCase("crash");
    }

    @Test
    void reportFileIsGenerated() {
        runInvestigate();

        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
    }

    private int runInvestigate() {
        InvestigateCommand cmd = new InvestigateCommand();
        CommandLine cmdLine = new CommandLine(cmd);
        String[] args = {
                "--alert", alertFile.getAbsolutePath(),
                "--evidence", evidenceFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath()
        };
        return cmdLine.execute(args);
    }
}
