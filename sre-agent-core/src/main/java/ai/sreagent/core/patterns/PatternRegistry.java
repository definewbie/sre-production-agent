package ai.sreagent.core.patterns;

import ai.sreagent.core.domain.DiagnosticPattern;

import java.util.*;

/**
 * Registry of diagnostic patterns.
 * Patterns define known failure modes and how to match evidence against them.
 */
public class PatternRegistry {

    private final Map<String, DiagnosticPattern> patterns = new LinkedHashMap<>();

    public void register(DiagnosticPattern pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(pattern.id(), "pattern id must not be null");
        patterns.put(pattern.id(), pattern);
    }

    public Optional<DiagnosticPattern> get(String patternId) {
        return Optional.ofNullable(patterns.get(patternId));
    }

    public List<DiagnosticPattern> all() {
        return List.copyOf(patterns.values());
    }

    public Set<String> patternIds() {
        return Set.copyOf(patterns.keySet());
    }

    public int size() {
        return patterns.size();
    }
}
