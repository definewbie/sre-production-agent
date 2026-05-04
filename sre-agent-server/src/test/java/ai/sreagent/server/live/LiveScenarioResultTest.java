
package ai.sreagent.server.live;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveScenarioResultTest {

    @Test
    void running_shouldCreateRunningStatus() {
        LiveScenarioResult result = LiveScenarioResult.running("sc-1", "Test Scenario");
        assertThat(result.scenarioId()).isEqualTo("sc-1");
        assertThat(result.scenarioName()).isEqualTo("Test Scenario");
        assertThat(result.status()).isEqualTo(LiveScenarioResult.LiveScenarioStatus.RUNNING);
        assertThat(result.phase()).isEqualTo("collecting");
        assertThat(result.baseRca()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void failed_shouldCreateFailedStatus() {
        LiveScenarioResult result = LiveScenarioResult.failed("sc-2", "Failed Scenario", "something broke");
        assertThat(result.scenarioId()).isEqualTo("sc-2");
        assertThat(result.status()).isEqualTo(LiveScenarioResult.LiveScenarioStatus.FAILED);
        assertThat(result.phase()).isEqualTo("failed");
        assertThat(result.errorMessage()).isEqualTo("something broke");
        assertThat(result.durationMs()).isEqualTo(0);
    }
}
