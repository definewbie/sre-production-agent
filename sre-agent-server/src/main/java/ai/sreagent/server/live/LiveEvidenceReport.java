
package ai.sreagent.server.live;

import ai.sreagent.core.domain.Evidence;

import java.util.List;
import java.util.Map;

/**
 * Report of live evidence collected from observability backends.
 * Each source reports independently — failures do not block other sources.
 */
public record LiveEvidenceReport(
    int totalEvidenceCount,
    List<Evidence> allEvidence,
    Map<String, SourceReport> sources,
    List<String> warnings
) {

    /**
     * Report for a single evidence source.
     */
    public record SourceReport(
        String sourceName,
        boolean available,
        int evidenceCount,
        List<String> evidenceTypes,
        String error
    ) {}

    /**
     * Create an empty report.
     */
    public static LiveEvidenceReport empty() {
        return new LiveEvidenceReport(0, List.of(), Map.of(), List.of());
    }

    /**
     * Create a report with warnings only.
     */
    public static LiveEvidenceReport withWarnings(List<String> warnings) {
        return new LiveEvidenceReport(0, List.of(), Map.of(), warnings);
    }
}
