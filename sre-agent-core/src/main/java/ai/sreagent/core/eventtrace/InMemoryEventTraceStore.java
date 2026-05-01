package ai.sreagent.core.eventtrace;

import ai.sreagent.core.domain.EventTraceEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of EventTraceStore.
 * Suitable for single-run CLI investigations and testing.
 */
public class InMemoryEventTraceStore implements EventTraceStore {

    private final List<EventTraceEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(EventTraceEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<EventTraceEntry> getByIncidentId(String incidentId) {
        return entries.stream()
                .filter(e -> e.incidentId().equals(incidentId))
                .collect(Collectors.toList());
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
