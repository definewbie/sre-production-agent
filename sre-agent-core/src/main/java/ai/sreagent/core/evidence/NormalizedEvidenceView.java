package ai.sreagent.core.evidence;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper for querying normalized evidence by signal, category, or causal role.
 */
public final class NormalizedEvidenceView {

    private final List<NormalizedEvidence> evidence;

    public NormalizedEvidenceView(List<NormalizedEvidence> evidence) {
        this.evidence = evidence != null ? evidence : List.of();
    }

    /**
     * Check if any evidence has the given signal.
     */
    public boolean hasSignal(EvidenceSignal signal) {
        return evidence.stream().anyMatch(e -> e.signal() == signal);
    }

    /**
     * Check if any evidence has the given category.
     */
    public boolean hasCategory(EvidenceCategory category) {
        return evidence.stream().anyMatch(e -> e.category() == category);
    }

    /**
     * Get all evidence matching the given signal.
     */
    public List<NormalizedEvidence> bySignal(EvidenceSignal signal) {
        return evidence.stream()
            .filter(e -> e.signal() == signal)
            .collect(Collectors.toList());
    }

    /**
     * Get all evidence matching the given category.
     */
    public List<NormalizedEvidence> byCategory(EvidenceCategory category) {
        return evidence.stream()
            .filter(e -> e.category() == category)
            .collect(Collectors.toList());
    }

    /**
     * Get all evidence matching the given causal role.
     */
    public List<NormalizedEvidence> byCausalRole(EvidenceCausalRole role) {
        return evidence.stream()
            .filter(e -> e.causalRole() == role)
            .collect(Collectors.toList());
    }

    /**
     * Get all cause candidate evidence.
     */
    public List<NormalizedEvidence> causeCandidates() {
        return byCausalRole(EvidenceCausalRole.CAUSE_CANDIDATE);
    }

    /**
     * Get all symptom evidence.
     */
    public List<NormalizedEvidence> symptoms() {
        return byCausalRole(EvidenceCausalRole.SYMPTOM);
    }

    /**
     * Get all counter-signal evidence.
     */
    public List<NormalizedEvidence> counterSignals() {
        return byCausalRole(EvidenceCausalRole.COUNTER_SIGNAL);
    }

    /**
     * Get all topology context evidence.
     */
    public List<NormalizedEvidence> topologyContext() {
        return byCausalRole(EvidenceCausalRole.TOPOLOGY_CONTEXT);
    }

    /**
     * Get the underlying evidence list.
     */
    public List<NormalizedEvidence> all() {
        return evidence;
    }

    /**
     * Get count of evidence items.
     */
    public int size() {
        return evidence.size();
    }
}
