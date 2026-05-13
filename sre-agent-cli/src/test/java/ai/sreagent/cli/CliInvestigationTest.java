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
        assertThat(report).contains("竞争假设");
    }

    @Test
    void reportContainsScores() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("0.25");
        assertThat(report).contains("0.23");
    }

    @Test
    void reportContainsScoreGap() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("0.02");
    }

    @Test
    void reportContainsBothHypotheses() throws Exception {
        runInvestigate();

        String report = Files.readString(outputFile.toPath());
        assertThat(report).contains("近期部署引入回归缺陷");
        assertThat(report).contains("下游依赖延迟导致超时");
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
