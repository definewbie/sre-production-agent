package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Confidence score for a single hypothesis after verification.
 * The score is computed deterministically from supporting/counter/missing evidence.
 *
 * <p>V.2-RCA-1A.3 adds temporal alignment fields. The backward-compatible
 * 9-arg constructor sets temporal defaults (score=0, confidence=UNKNOWN).</p>
 */
public record ConfidenceResult(
    @JsonProperty("hypothesis_id") String hypothesisId,
    double score,
    String level,
    @JsonProperty("supporting_factors") List<String> supportingFactors,
    @JsonProperty("counter_factors") List<String> counterFactors,
    @JsonProperty("missing_factors") List<String> missingFactors,
    List<String> contradictions,
    String decision,
    @JsonProperty("calibration_notes") String calibrationNotes,
    // ── V.2-RCA-1A.3: temporal alignment fields ──
    @JsonProperty("temporal_alignment_score") double temporalAlignmentScore,
    @JsonProperty("temporal_confidence") String temporalConfidence,
    @JsonProperty("candidate_first_seen") Instant candidateFirstSeen,
    @JsonProperty("impacted_first_seen") Instant impactedFirstSeen,
    @JsonProperty("temporal_explanation") String temporalExplanation,
    // ── V.2-RCA-1A.4: topology edge ──
    @JsonProperty("topology_edge") TopologyEdge topologyEdge,
    @JsonProperty("propagation_path") PropagationPath propagationPath,
    @JsonProperty("topology_causality_score") double topologyCausalityScore,
    @JsonProperty("diagnostic_quality") String diagnosticQuality,
    @JsonProperty("provider_blindness") List<String> providerBlindness
) {
    /** Backward-compatible constructor — temporal and topology fields default to absent. */
    public ConfidenceResult(
            String hypothesisId, double score, String level,
            List<String> supportingFactors, List<String> counterFactors,
            List<String> missingFactors, List<String> contradictions,
            String decision, String calibrationNotes
    ) {
        this(hypothesisId, score, level, supportingFactors, counterFactors,
                missingFactors, contradictions, decision, calibrationNotes,
                0.0, "UNKNOWN", null, null, "",
                TopologyEdge.NONE, PropagationPath.NONE, 0.0, "FULL", List.of());
    }
}
