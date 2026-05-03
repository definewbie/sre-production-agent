package ai.sreagent.core.verification;

import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the pod_crash_loop diagnostic pattern.
 * Verifies pattern registration, evidence requirements, and confidence weights.
 */
class PodCrashLoopPatternTest {

    @Test
    void registryContainsPodCrashLoop() {
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Optional<DiagnosticPattern> pattern = registry.all().stream()
                .filter(p -> p.id().equals("pod_crash_loop"))
                .findFirst();
        assertThat(pattern).isPresent();
    }

    @Test
    void podCrashLoop_hasRequiredSupportingTypes() {
        DiagnosticPattern pattern = BuiltinPatterns.podCrashLoop();
        assertThat(pattern.supportingEvidenceTypes()).containsExactlyInAnyOrder(
                "container_crash_loop_backoff",
                "pod_restart_count_increased",
                "pod_not_ready",
                "deployment_metadata"
        );
    }

    @Test
    void podCrashLoop_hasCounterEvidenceTypes() {
        DiagnosticPattern pattern = BuiltinPatterns.podCrashLoop();
        assertThat(pattern.counterEvidenceTypes()).containsExactlyInAnyOrder(
                "no_restart_observed",
                "pod_ready",
                "container_running_normal"
        );
    }

    @Test
    void podCrashLoop_hasConfidenceWeights() {
        DiagnosticPattern pattern = BuiltinPatterns.podCrashLoop();
        assertThat(pattern.confidenceWeights()).containsEntry("container_crash_loop_backoff", 0.30);
        assertThat(pattern.confidenceWeights()).containsEntry("pod_restart_count_increased", 0.20);
        assertThat(pattern.confidenceWeights()).containsEntry("pod_not_ready", 0.15);
        assertThat(pattern.confidenceWeights()).containsEntry("deployment_metadata", 0.05);
    }

    @Test
    void podCrashLoop_hasBaseScore() {
        DiagnosticPattern pattern = BuiltinPatterns.podCrashLoop();
        assertThat(pattern.baseScore()).isEqualTo(0.25);
    }

    @Test
    void podCrashLoop_descriptionIsSet() {
        DiagnosticPattern pattern = BuiltinPatterns.podCrashLoop();
        assertThat(pattern.description()).isNotBlank();
        assertThat(pattern.description()).contains("crash loop");
    }
}
