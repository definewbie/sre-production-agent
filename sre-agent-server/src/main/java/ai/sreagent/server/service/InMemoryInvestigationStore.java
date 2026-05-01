package ai.sreagent.server.service;

import ai.sreagent.core.workflow.InvestigationResult;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for investigation results.
 * No database — sufficient for demo / interview purposes.
 */
@Component
public class InMemoryInvestigationStore {

    private final ConcurrentHashMap<String, InvestigationResult> store = new ConcurrentHashMap<>();

    public void save(InvestigationResult result) {
        store.put(result.incidentId(), result);
    }

    public Optional<InvestigationResult> findByIncidentId(String incidentId) {
        return Optional.ofNullable(store.get(incidentId));
    }

    /**
     * Returns the most recently saved investigation result.
     */
    public Optional<InvestigationResult> findLatest() {
        return store.values().stream()
                .reduce((first, second) -> second);
    }
}
