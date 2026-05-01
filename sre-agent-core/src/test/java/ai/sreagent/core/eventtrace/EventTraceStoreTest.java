package ai.sreagent.core.eventtrace;

import ai.sreagent.core.domain.EventTraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventTraceStoreTest {

    private InMemoryEventTraceStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventTraceStore();
    }

    @Test
    void appendAndListByIncidentId() {
        store.append(entry("inc_001", "INCIDENT_CREATED", Map.of("alert", "HighErrorRate")));
        store.append(entry("inc_001", "EVIDENCE_LOADED", Map.of("count", 8)));
        store.append(entry("inc_002", "INCIDENT_CREATED", Map.of("alert", "OOMKilled")));

        List<EventTraceEntry> result = store.getByIncidentId("inc_001");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).eventType()).isEqualTo("INCIDENT_CREATED");
        assertThat(result.get(1).eventType()).isEqualTo("EVIDENCE_LOADED");
    }

    @Test
    void eventsPreserveInsertionOrder() {
        store.append(entry("inc_001", "INCIDENT_CREATED", Map.of()));
        store.append(entry("inc_001", "EVIDENCE_LOADED", Map.of()));
        store.append(entry("inc_001", "HYPOTHESES_GENERATED", Map.of()));
        store.append(entry("inc_001", "CONFIDENCE_SCORED", Map.of()));

        List<EventTraceEntry> result = store.getByIncidentId("inc_001");
        assertThat(result).hasSize(4);
        assertThat(result.stream().map(EventTraceEntry::eventType).toList())
                .containsExactly("INCIDENT_CREATED", "EVIDENCE_LOADED",
                        "HYPOTHESES_GENERATED", "CONFIDENCE_SCORED");
    }

    @Test
    void unknownIncidentReturnsEmptyList() {
        store.append(entry("inc_001", "INCIDENT_CREATED", Map.of()));
        assertThat(store.getByIncidentId("inc_999")).isEmpty();
    }

    @Test
    void multipleIncidentsIsolated() {
        store.append(entry("inc_001", "INCIDENT_CREATED", Map.of()));
        store.append(entry("inc_002", "INCIDENT_CREATED", Map.of()));
        store.append(entry("inc_001", "EVIDENCE_LOADED", Map.of()));
        store.append(entry("inc_002", "EVIDENCE_LOADED", Map.of()));

        List<EventTraceEntry> inc1 = store.getByIncidentId("inc_001");
        List<EventTraceEntry> inc2 = store.getByIncidentId("inc_002");

        assertThat(inc1).hasSize(2);
        assertThat(inc2).hasSize(2);
        assertThat(inc1.get(1).eventType()).isEqualTo("EVIDENCE_LOADED");
        assertThat(inc2.get(1).eventType()).isEqualTo("EVIDENCE_LOADED");
    }

    @Test
    void payloadPreserved() {
        store.append(entry("inc_001", "CONFIDENCE_SCORED",
                Map.of("hypothesisId", "hyp_dep", "score", 0.64)));

        EventTraceEntry e = store.getByIncidentId("inc_001").get(0);
        assertThat(e.payload()).containsEntry("hypothesisId", "hyp_dep");
        assertThat(e.payload()).containsEntry("score", 0.64);
    }

    private EventTraceEntry entry(String incidentId, String eventType, Map<String, Object> payload) {
        return new EventTraceEntry(
                "evt_" + System.nanoTime(),
                incidentId,
                eventType,
                Instant.now(),
                payload
        );
    }
}
