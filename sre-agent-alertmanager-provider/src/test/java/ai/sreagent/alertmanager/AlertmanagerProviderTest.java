package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.client.FixtureAlertmanagerClient;
import ai.sreagent.alertmanager.mapper.AlertmanagerEvidenceTypes;
import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertmanagerProviderTest {

    private FixtureAlertmanagerClient fixtureClient;
    private AlertmanagerProvider provider;

    private static final String INCIDENT_ID = "inc-provider-001";
    private static final String SERVICE = "order-service";
    private static final String NAMESPACE = "demo";

    @BeforeEach
    void setUp() {
        fixtureClient = new FixtureAlertmanagerClient();
        provider = new AlertmanagerProvider(fixtureClient);
    }

    private AlertmanagerRequest.Builder defaultRequestBuilder() {
        return AlertmanagerRequest.builder()
                .incidentId(INCIDENT_ID)
                .startTime(Instant.parse("2026-04-28T10:00:00Z"))
                .endTime(Instant.parse("2026-04-28T11:00:00Z"))
                .labelMatchers(Map.of("service", SERVICE, "namespace", NAMESPACE));
    }

    // ------------------------------------------------------------------ //
    //  Provider returns incidents and evidence
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("provider with fixture client")
    class FixtureClientProvider {

        @Test
        @DisplayName("provider with FixtureAlertmanagerClient returns incidents and evidence")
        void shouldReturnIncidentsAndEvidence() {
            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            assertThat(result.incidents()).isNotEmpty();
            assertThat(result.evidence()).isNotEmpty();
        }

        @Test
        @DisplayName("incidents contain correct alert metadata")
        void incidentsContainCorrectMetadata() {
            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            IncidentTask first = result.incidents().getFirst();
            assertThat(first.alertName()).isEqualTo("HighErrorRate");
            assertThat(first.service()).isEqualTo(SERVICE);
            assertThat(first.namespace()).isEqualTo(NAMESPACE);
        }

        @Test
        @DisplayName("evidence types include alert_firing")
        void evidenceIncludesAlertFiring() {
            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            List<String> types = result.evidence().stream()
                    .map(Evidence::evidenceType)
                    .toList();
            assertThat(types).contains(AlertmanagerEvidenceTypes.ALERT_FIRING);
        }
    }

    // ------------------------------------------------------------------ //
    //  Source
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("provider source")
    class ProviderSource {

        @Test
        @DisplayName("evidence source is alertmanager")
        void evidenceSourceShouldBeAlertmanager() {
            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            assertThat(result.evidence()).isNotEmpty();
            assertThat(result.evidence()).allSatisfy(e ->
                    assertThat(e.source()).isEqualTo("alertmanager"));
        }
    }

    // ------------------------------------------------------------------ //
    //  No live Alertmanager required
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("no live Alertmanager required")
    class NoLiveAlertmanager {

        @Test
        @DisplayName("fixture client does not require a live Alertmanager")
        void doesNotRequireLiveAlertmanager() {
            // This test passes as long as collect() works without network
            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            assertThat(result.rawSummary()).containsEntry("reader", "fixture");
        }
    }

    // ------------------------------------------------------------------ //
    //  Empty fixture
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("empty fixture")
    class EmptyFixture {

        @Test
        @DisplayName("empty fixture returns alert_no_signal evidence")
        void shouldReturnNoSignalEvidenceForEmptyFixture() {
            fixtureClient.setFixtureName("empty_alerts.json");

            AlertmanagerRequest request = defaultRequestBuilder().build();
            AlertmanagerResult result = provider.collect(request);

            assertThat(result.incidents()).isEmpty();
            assertThat(result.evidence()).hasSize(1);

            Evidence e = result.evidence().getFirst();
            assertThat(e.evidenceType()).isEqualTo(AlertmanagerEvidenceTypes.ALERT_NO_SIGNAL);
            assertThat(e.strength()).isEqualTo(0.0);
        }
    }

    // ------------------------------------------------------------------ //
    //  Health and client name
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("health and client name")
    class HealthAndClientName {

        @Test
        @DisplayName("provider is healthy (isHealthy returns true)")
        void shouldReturnHealthy() {
            assertThat(provider.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("clientName returns 'fixture'")
        void shouldReturnFixtureClientName() {
            assertThat(provider.clientName()).isEqualTo("fixture");
        }
    }
}
