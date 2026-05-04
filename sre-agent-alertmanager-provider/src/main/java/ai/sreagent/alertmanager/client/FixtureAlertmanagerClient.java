package ai.sreagent.alertmanager.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Fixture-based Alertmanager client for deterministic tests.
 * Returns pre-defined JSON fixtures by scenario.
 * No network. No live Alertmanager.
 */
public class FixtureAlertmanagerClient implements AlertmanagerClient {

    private static final String FIXTURE_BASE = "fixtures/alertmanager/";

    private String fixtureName = "firing_high_error_rate.json";
    private boolean explicitFixture = false;

    /**
     * Set which fixture to load. Called by tests or provider before executing queries.
     */
    public void setFixtureName(String fixtureName) {
        if (fixtureName != null) {
            this.fixtureName = fixtureName;
            this.explicitFixture = true;
        }
    }

    /**
     * Returns true if fixture was explicitly set (e.g. by a test).
     */
    public boolean hasExplicitFixture() {
        return explicitFixture;
    }

    @Override
    public String getAlerts(Map<String, String> labelMatchers, boolean includeResolved) {
        // Try requested fixture first
        String content = loadResource(FIXTURE_BASE + fixtureName);
        if (content != null) return content;

        // Fallback: if includeResolved, try resolved fixture
        if (includeResolved) {
            content = loadResource(FIXTURE_BASE + "resolved_high_error_rate.json");
            if (content != null) return content;
        }

        // Final fallback: empty alerts
        return loadResource(FIXTURE_BASE + "empty_alerts.json");
    }

    private String loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String clientName() {
        return "fixture";
    }
}
