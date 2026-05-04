package ai.sreagent.core.evidence;

import java.time.Instant;
import java.util.Map;

/**
 * Taxonomy-enriched representation of Evidence.
 * Adds normalized category, signal, source kind, severity, and causal role
 * without modifying the original Evidence object.
 *
 * @param originalEvidenceType the raw evidence_type string from the provider
 * @param normalizedType       the normalized taxonomy key
 * @param category             observability pillar
 * @param signal               provider-agnostic semantic signal
 * @param sourceKind           which system produced this evidence
 * @param severity             severity derived from strength
 * @param causalRole           causal role in RCA context
 * @param entity               what entity this evidence is about
 * @param service              service name
 * @param namespace            namespace
 * @param strength             original strength
 * @param timestamp            original timestamp
 * @param content              original content
 * @param attributes           original + enriched attributes
 */
public record NormalizedEvidence(
    String originalEvidenceType,
    String normalizedType,
    EvidenceCategory category,
    EvidenceSignal signal,
    EvidenceSourceKind sourceKind,
    EvidenceSeverity severity,
    EvidenceCausalRole causalRole,
    String entity,
    String service,
    String namespace,
    double strength,
    Instant timestamp,
    String content,
    Map<String, Object> attributes
) {}
