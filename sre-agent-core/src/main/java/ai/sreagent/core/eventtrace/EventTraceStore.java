package ai.sreagent.core.eventtrace;

import ai.sreagent.core.domain.EventTraceEntry;
import java.util.List;

/**
 * Stores the audit trail of an investigation.
 * Every step of the RCA workflow must be recorded here.
 */
public interface EventTraceStore {

    void append(EventTraceEntry entry);

    List<EventTraceEntry> getByIncidentId(String incidentId);
}
