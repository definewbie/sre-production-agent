package ai.sreagent.core.evidence;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Provides static evidence and alert data from bundled JSON resources.
 * Used for demo scenarios when no real collectors are connected.
 */
public class StaticEvidenceProvider {

    private static final String SCENARIO_E_ALERT = "/scenarios/competing_hypotheses_alert.json";
    private static final String SCENARIO_E_EVIDENCE = "/scenarios/competing_hypotheses_evidence.json";

    private final EvidenceLoader loader;

    public StaticEvidenceProvider(EvidenceLoader loader) {
        this.loader = loader;
    }

    public IncidentTask loadScenarioEAlert() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCENARIO_E_ALERT)) {
            if (is == null) {
                throw new IOException("Resource not found: " + SCENARIO_E_ALERT);
            }
            return loader.loadAlert(is);
        }
    }

    public List<Evidence> loadScenarioEEvidence() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCENARIO_E_EVIDENCE)) {
            if (is == null) {
                throw new IOException("Resource not found: " + SCENARIO_E_EVIDENCE);
            }
            return loader.loadEvidence(is);
        }
    }
}
