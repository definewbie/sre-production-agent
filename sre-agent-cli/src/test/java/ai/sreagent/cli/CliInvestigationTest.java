package ai.sreagent.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CliInvestigationTest {

    @TempDir
    Path tempDir;

    private File alertFile;
    private File evidenceFile;
    private File outputFile;

    @BeforeEach
    void setUp() throws Exception {
        // Copy classpath resources to temp files for CLI file-based loading
        alertFile = tempDir.resolve("alert.json").toFile();
        evidenceFile = tempDir.resolve("evidence.json").toFile();
        outputFile = tempDir.resolve("report.md").toFile();

        try (var alertIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
             var evidenceIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json")) {
            Files.writeString(alertFile.toPath(), new String(alertIs.readAllBytes()));
            Files.writeString(evidenceFile.toPath(), new String(evidenceIs.readAllBytes()));
        }
    }

    @Test
    void investigateReturnsSuccessExitCode() {
        InvestigateCommand cmd = new InvestigateCommand();
        CommandLine cmdLine = new CommandLine(cmd);

        // Inject options via CommandLine parsing
        String[] args = {
                "--alert", alertFile.getAbsolutePath(),
                "--evidence", evidenceFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath()
        };

        int exitCode = cmdLine.execute(args);
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void investigateGeneratesReportFile() {
        runInvestigate();

        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
    }

    @Test
    void reportContainsDecision() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("competing_hypotheses");
    }

    @Test
    void reportContainsScores() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("0.64");
        assertThat(report).contains("0.58");
    }

    @Test
    void reportContainsScoreGap() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("0.06");
    }

    @Test
    void reportContainsBothHypotheses() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("deployment_regression");
        assertThat(report).contains("downstream_dependency_latency");
    }

    private void runInvestigate() {
        InvestigateCommand cmd = new InvestigateCommand();
        CommandLine cmdLine = new CommandLine(cmd);
        String[] args = {
                "--alert", alertFile.getAbsolutePath(),
                "--evidence", evidenceFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath()
        };
        int exitCode = cmdLine.execute(args);
        assertThat(exitCode).isEqualTo(0);
    }
}
