package ai.sreagent.loki;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.loki.mapper.LokiEvidenceMapper;
import ai.sreagent.loki.mapper.LokiEvidenceTypes;
import ai.sreagent.loki.parser.LokiLogEntry;
import ai.sreagent.loki.parser.LokiQueryResult;
import ai.sreagent.loki.query.LokiQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LokiEvidenceMapperTest {

    private LokiEvidenceMapper mapper;

    private static final String SERVICE = "order-service";
    private static final String INCIDENT_ID = "inc-001";
    private static final String NAMESPACE = "production";
    private static final String LOGQL = "{app=\"order-service\"} |= \"timeout\"";
    private static final Instant START = Instant.parse("2025-05-01T10:00:00Z");
    private static final Instant END = Instant.parse("2025-05-01T10:15:00Z");

    private LokiLogEntry entry1;
    private LokiLogEntry entry2;
    private LokiQueryResult twoEntryResult;

    @BeforeEach
    void setUp() {
        mapper = new LokiEvidenceMapper();

        entry1 = new LokiLogEntry(
                Map.of("app", SERVICE, "namespace", NAMESPACE),
                Instant.parse("2025-05-01T10:05:00Z"),
                "Connection timeout to payment-service"
        );
        entry2 = new LokiLogEntry(
                Map.of("app", SERVICE, "namespace", NAMESPACE),
                Instant.parse("2025-05-01T10:06:00Z"),
                "Retry failed for payment-service"
        );
        twoEntryResult = new LokiQueryResult("streams", List.of(entry1, entry2));
    }

    // --- All 8 query types map to correct evidence type ---

    @Test
    void timeoutError_mapsToLogTimeoutError() {
        assertEvidenceMapping(LokiQueryType.TIMEOUT_ERROR, LokiEvidenceTypes.LOG_TIMEOUT_ERROR, 0.75);
    }

    @Test
    void downstreamTimeout_mapsToLogDownstreamTimeout() {
        assertEvidenceMapping(LokiQueryType.DOWNSTREAM_TIMEOUT, LokiEvidenceTypes.LOG_DOWNSTREAM_TIMEOUT, 0.85);
    }

    @Test
    void exceptionLogs_mapsToLogExceptionSpike() {
        assertEvidenceMapping(LokiQueryType.EXCEPTION_LOGS, LokiEvidenceTypes.LOG_EXCEPTION_SPIKE, 0.70);
    }

    @Test
    void crashLogs_mapsToLogCrashLoop() {
        assertEvidenceMapping(LokiQueryType.CRASH_LOGS, LokiEvidenceTypes.LOG_CRASH_LOOP, 0.90);
    }

    @Test
    void oomLogs_mapsToLogOomMessage() {
        assertEvidenceMapping(LokiQueryType.OOM_LOGS, LokiEvidenceTypes.LOG_OOM_MESSAGE, 0.90);
    }

    @Test
    void dbConnectionTimeout_mapsToLogDbConnectionTimeout() {
        assertEvidenceMapping(LokiQueryType.DB_CONNECTION_TIMEOUT, LokiEvidenceTypes.LOG_DB_CONNECTION_TIMEOUT, 0.85);
    }

    @Test
    void retryExhausted_mapsToLogRetryExhausted() {
        assertEvidenceMapping(LokiQueryType.RETRY_EXHAUSTED, LokiEvidenceTypes.LOG_RETRY_EXHAUSTED, 0.80);
    }

    @Test
    void http5xxLogs_mapsToLogHttp5xx() {
        assertEvidenceMapping(LokiQueryType.HTTP_5XX_LOGS, LokiEvidenceTypes.LOG_HTTP_5XX, 0.75);
    }

    // --- Empty result maps to log_no_signal ---

    @Test
    void emptyResult_mapsToLogNoSignal_withZeroStrength() {
        LokiQueryResult emptyResult = new LokiQueryResult("streams", List.of());

        List<Evidence> evidence = mapper.map(
                LokiQueryType.TIMEOUT_ERROR, emptyResult, LOGQL, INCIDENT_ID, SERVICE, NAMESPACE, START, END
        );

        assertThat(evidence).hasSize(1);
        Evidence e = evidence.get(0);

        assertThat(e.source()).isEqualTo("loki");
        assertThat(e.evidenceType()).isEqualTo(LokiEvidenceTypes.LOG_NO_SIGNAL);
        assertThat(e.strength()).isEqualTo(0.0);
    }

    // --- Helper ---

    private void assertEvidenceMapping(LokiQueryType queryType, String expectedEvidenceType, double expectedStrength) {
        List<Evidence> evidence = mapper.map(
                queryType, twoEntryResult, LOGQL, INCIDENT_ID, SERVICE, NAMESPACE, START, END
        );

        assertThat(evidence).hasSize(1);
        Evidence e = evidence.get(0);

        // Source is always loki
        assertThat(e.source()).isEqualTo("loki");

        // Evidence type matches expected
        assertThat(e.evidenceType()).isEqualTo(expectedEvidenceType);

        // Strength is positive
        assertThat(e.strength()).isGreaterThan(0.0);
        assertThat(e.strength()).isEqualTo(expectedStrength);

        // Content references the service
        assertThat(e.content()).contains(SERVICE);

        // Attributes contain matchCount=2
        assertThat(e.attributes()).containsEntry("matchCount", 2);
    }
}
