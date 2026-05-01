package ai.sreagent.core;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.evidence.StaticEvidenceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceLoaderTest {

    private EvidenceLoader loader;

    @BeforeEach
    void setUp() {
        loader = new EvidenceLoader();
    }

    @Test
    @DisplayName("Load alert JSON from classpath into IncidentTask")
    void loadAlertFromClasspath() throws Exception {
        InputStream is = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
        assertThat(is).isNotNull();
        IncidentTask task = loader.loadAlert(is);

        assertThat(task.id()).isEqualTo("inc_20260428_1008");
        assertThat(task.alertName()).isEqualTo("HighErrorRate");
        assertThat(task.service()).isEqualTo("order-service");
        assertThat(task.namespace()).isEqualTo("demo");
        assertThat(task.severity()).isEqualTo("warning");
        assertThat(task.startedAt()).isNotNull();
        assertThat(task.labels()).containsEntry("team", "order-platform");
        assertThat(task.annotations()).containsKey("description");
    }

    @Test
    @DisplayName("Load evidence JSON from classpath into List<Evidence>")
    void loadEvidenceFromClasspath() throws Exception {
        InputStream is = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json");
        assertThat(is).isNotNull();
        List<Evidence> evidenceList = loader.loadEvidence(is);

        assertThat(evidenceList).hasSize(8);

        // Verify first evidence
        Evidence first = evidenceList.getFirst();
        assertThat(first.id()).isEqualTo("ev_001");
        assertThat(first.source()).isEqualTo("deploy");
        assertThat(first.evidenceType()).isEqualTo("deploy_event_near_alert_window");
        assertThat(first.strength()).isBetween(0.0, 1.0);

        // Verify evidence covers required sources
        assertThat(evidenceList).anyMatch(e -> "deploy".equals(e.source()));
        assertThat(evidenceList).anyMatch(e -> "metric".equals(e.source()));
        assertThat(evidenceList).anyMatch(e -> "log".equals(e.source()));
        assertThat(evidenceList).anyMatch(e -> "git".equals(e.source()));
        assertThat(evidenceList).anyMatch(e -> "topology".equals(e.source()));
    }

    @Test
    @DisplayName("StaticEvidenceProvider loads Scenario E from classpath")
    void staticProviderLoadsScenarioE() throws Exception {
        StaticEvidenceProvider provider = new StaticEvidenceProvider(loader);

        IncidentTask alert = provider.loadScenarioEAlert();
        assertThat(alert.alertName()).isEqualTo("HighErrorRate");

        List<Evidence> evidence = provider.loadScenarioEEvidence();
        assertThat(evidence).hasSize(8);
    }
}
