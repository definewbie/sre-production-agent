package ai.sreagent.server.service;

import ai.sreagent.core.workflow.InvestigationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInvestigationStoreTest {

    private InMemoryInvestigationStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryInvestigationStore();
    }

    @Test
    void missingIncidentReturnsEmpty() {
        Optional<InvestigationResult> result = store.findByIncidentId("not-exist");
        assertTrue(result.isEmpty());
    }

    @Test
    void canSaveAndFindByIncidentId() {
        InvestigationResult mock = MockResults.create("inc_001");
        store.save(mock);

        Optional<InvestigationResult> found = store.findByIncidentId("inc_001");
        assertTrue(found.isPresent());
        assertEquals("inc_001", found.get().incidentId());
    }

    @Test
    void multipleIncidentsStoredIndependently() {
        store.save(MockResults.create("inc_a"));
        store.save(MockResults.create("inc_b"));

        assertEquals("inc_a", store.findByIncidentId("inc_a").get().incidentId());
        assertEquals("inc_b", store.findByIncidentId("inc_b").get().incidentId());
    }
}
