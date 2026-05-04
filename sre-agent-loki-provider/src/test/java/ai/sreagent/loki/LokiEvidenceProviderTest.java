package ai.sreagent.loki;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.loki.client.FixtureLokiQueryClient;
import ai.sreagent.loki.mapper.LokiEvidenceTypes;
import ai.sreagent.loki.query.LokiQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LokiEvidenceProviderTest {

    private FixtureLokiQueryClient fixtureClient;
    private LokiEvidenceProvider provider;

    private static final String INCIDENT_ID = "inc-provider-001";
    private static final String SERVICE = "order-service";
    private static final String NAMESPACE = "production";
    private static final Instant END = Instant.parse("2025-06-01T12:00:00Z");
    private static final Instant START = Instant.parse("2025-06-01T11:30:00Z");

    @BeforeEach
    void setUp() {
        fixtureClient = new FixtureLokiQueryClient();
        provider = new LokiEvidenceProvider(fixtureClient);
    }

    @Test
    void collect_timeoutError_returnsEvidenceWithCorrectSourceAndType() {
        LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                .incidentId(INCIDENT_ID)
                .service(SERVICE)
                .namespace(NAMESPACE)
                .startTime(START)
                .endTime(END)
                .queryTypes(List.of(LokiQueryType.TIMEOUT_ERROR))
                .build();

        LokiEvidenceResult result = provider.collect(request);

        assertThat(result.incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(result.evidence()).isNotEmpty();

        Evidence e = result.evidence().get(0);
        assertThat(e.source()).isEqualTo("loki");
        assertThat(e.evidenceType()).isEqualTo(LokiEvidenceTypes.LOG_TIMEOUT_ERROR);
    }

    @Test
    void collect_exceptionLogs_returnsLogExceptionSpike() {
        LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                .incidentId(INCIDENT_ID)
                .service(SERVICE)
                .namespace(NAMESPACE)
                .startTime(START)
                .endTime(END)
                .queryTypes(List.of(LokiQueryType.EXCEPTION_LOGS))
                .build();

        LokiEvidenceResult result = provider.collect(request);

        assertThat(result.evidence()).isNotEmpty();

        Evidence e = result.evidence().get(0);
        assertThat(e.source()).isEqualTo("loki");
        assertThat(e.evidenceType()).isEqualTo(LokiEvidenceTypes.LOG_EXCEPTION_SPIKE);
    }

    @Test
    void collect_emptyQueryTypes_fallsBackToDefaults_andReturnsEvidence() {
        LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                .incidentId(INCIDENT_ID)
                .service(SERVICE)
                .namespace(NAMESPACE)
                .startTime(START)
                .endTime(END)
                .queryTypes(List.of())   // empty → triggers default set of 4 query types
                .build();

        LokiEvidenceResult result = provider.collect(request);

        // Default set is TIMEOUT_ERROR, EXCEPTION_LOGS, CRASH_LOGS, HTTP_5XX_LOGS
        assertThat(result.evidence()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(result.rawSummary()).containsEntry("queryCount", 4);
    }

    @Test
    void collect_multipleTypes_returnsTwoOrMoreEvidence() {
        LokiEvidenceRequest request = LokiEvidenceRequest.builder()
                .incidentId(INCIDENT_ID)
                .service(SERVICE)
                .namespace(NAMESPACE)
                .startTime(START)
                .endTime(END)
                .queryTypes(List.of(LokiQueryType.TIMEOUT_ERROR, LokiQueryType.CRASH_LOGS))
                .build();

        LokiEvidenceResult result = provider.collect(request);

        assertThat(result.evidence()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.rawSummary()).containsEntry("queryCount", 2);

        List<String> types = result.evidence().stream()
                .map(Evidence::evidenceType)
                .toList();
        assertThat(types).contains(LokiEvidenceTypes.LOG_TIMEOUT_ERROR, LokiEvidenceTypes.LOG_CRASH_LOOP);
    }

    @Test
    void isHealthy_returnsTrue() {
        assertThat(provider.isHealthy()).isTrue();
    }

    @Test
    void clientName_returnsFixture() {
        assertThat(provider.clientName()).isEqualTo("fixture");
    }
}
