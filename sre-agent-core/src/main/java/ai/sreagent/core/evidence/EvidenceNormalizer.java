package ai.sreagent.core.evidence;

import ai.sreagent.core.domain.Evidence;

import java.time.Instant;
import java.util.*;

/**
 * Normalizes Evidence objects into taxonomy-enriched NormalizedEvidence.
 * Does not modify original Evidence objects.
 */
public final class EvidenceNormalizer {

    private EvidenceNormalizer() {}

    /**
     * Normalize a single Evidence object.
     */
    public static NormalizedEvidence normalize(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        String evidenceType = evidence.evidenceType();
        EvidenceTaxonomyRegistry.TaxonomyEntry entry = EvidenceTaxonomyRegistry.lookup(evidenceType);

        EvidenceCategory category = entry != null ? entry.category() : EvidenceCategory.UNKNOWN;
        EvidenceSignal signal = entry != null ? entry.signal() : EvidenceSignal.UNKNOWN;
        EvidenceSourceKind sourceKind = EvidenceTaxonomyRegistry.getSourceKind(evidenceType, evidence.source());
        EvidenceCausalRole causalRole = entry != null ? entry.causalRole() : EvidenceCausalRole.UNKNOWN;
        EvidenceSeverity severity = EvidenceSeverity.fromStrength(evidence.strength());

        String entity = inferEntity(evidence);

        return new NormalizedEvidence(
            evidenceType,
            evidenceType,
            category,
            signal,
            sourceKind,
            severity,
            causalRole,
            entity,
            evidence.service(),
            extractNamespace(evidence),
            evidence.strength(),
            evidence.timestamp(),
            evidence.content(),
            evidence.attributes() != null ? new LinkedHashMap<>(evidence.attributes()) : new LinkedHashMap<>()
        );
    }

    /**
     * Normalize a list of Evidence objects.
     */
    public static List<NormalizedEvidence> normalizeAll(List<Evidence> evidenceList) {
        if (evidenceList == null) return List.of();
        return evidenceList.stream()
            .map(EvidenceNormalizer::normalize)
            .toList();
    }

    /**
     * Infer entity from evidence attributes or service name.
     */
    private static String inferEntity(Evidence evidence) {
        Map<String, Object> attrs = evidence.attributes();
        if (attrs != null) {
            Object pod = attrs.get("podName");
            if (pod != null) return pod.toString();
            Object dep = attrs.get("deployment");
            if (dep != null) return dep.toString();
        }
        return evidence.service() != null ? evidence.service() : "unknown";
    }

    /**
     * Extract namespace from attributes or return null.
     */
    private static String extractNamespace(Evidence evidence) {
        Map<String, Object> attrs = evidence.attributes();
        if (attrs != null) {
            Object ns = attrs.get("namespace");
            if (ns != null) return ns.toString();
        }
        return null;
    }
}
