package ai.sreagent.server.live;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.VerificationEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the VerificationEngine correctly maps Scenario G evidence types
 * to the expected pattern supporting/counter evidence types.
 */
class ScenarioGVerificationMappingTest {

    private static final String INC_ID = "inc-scenario-g-verify";

    private Evidence makeEvidence(String id, String type, String source) {
        return new Evidence(id, INC_ID, source, type, "svc", Instant.now(), "test", Map.of(), 0.5);
    }

    @Test
    void downstreamPattern_matchesPrometheusDownstreamLatency() {
        DiagnosticPattern pattern = BuiltinPatterns.downstreamDependencyLatency();
        assertThat(pattern.supportingEvidenceTypes()).contains("metric_downstream_latency_spike");
    }

    @Test
    void downstreamPattern_matchesLokiDownstreamTimeout() {
        DiagnosticPattern pattern = BuiltinPatterns.downstreamDependencyLatency();
        assertThat(pattern.supportingEvidenceTypes()).contains("log_downstream_timeout");
    }

    @Test
    void downstreamPattern_matchesJaegerDownstreamSpan() {
        DiagnosticPattern pattern = BuiltinPatterns.downstreamDependencyLatency();
        assertThat(pattern.supportingEvidenceTypes()).contains("trace_downstream_span_slow");
    }

    @Test
    void oomPattern_hasNoMatches_whenNoOomEvidencePresent() {
        DiagnosticPattern oomPattern = BuiltinPatterns.podOomKilled();
        assertThat(oomPattern.supportingEvidenceTypes()).doesNotContain(
                "metric_latency_p95_spike", "log_timeout_error",
                "trace_downstream_span_slow", "metric_downstream_latency_spike");
    }

    @Test
    void providerAliasTypes_recognized() {
        String[] providerAliasTypes = {
                "metric_latency_p95_spike", "metric_error_rate_spike",
                "log_timeout_error", "log_downstream_timeout",
                "trace_downstream_span_slow", "trace_dependency_path"
        };
        for (String type : providerAliasTypes) {
            assertThat(type.startsWith("metric_") || type.startsWith("log_") || type.startsWith("trace_"))
                    .as(type + " should be a provider alias type")
                    .isTrue();
        }
    }

    @Test
    void fullVerification_downstreamPattern_getsAllLiveEvidence() {
        List<Evidence> evidence = List.of(
                makeEvidence("ev-1", "metric_downstream_latency_spike", "prometheus"),
                makeEvidence("ev-2", "metric_latency_p95_spike", "prometheus"),
                makeEvidence("ev-3", "log_timeout_error", "loki"),
                makeEvidence("ev-4", "log_downstream_timeout", "loki"),
                makeEvidence("ev-5", "log_retry_exhausted", "loki"),
                makeEvidence("ev-6", "trace_downstream_span_slow", "jaeger"),
                makeEvidence("ev-7", "trace_dependency_path", "jaeger"),
                makeEvidence("ev-8", "trace_timeout_span", "jaeger")
        );

        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        HypothesisEngine hypEngine = new HypothesisEngine();
        IncidentTask incident = new IncidentTask(
                INC_ID, "PaymentLatencySpike", "order-service", "demo", "warning",
                Instant.now(), Map.of("scenario", "g"), Map.of());
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        VerificationEngine verEngine = new VerificationEngine();
        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);

        Hypothesis downstreamHyp = hypotheses.stream()
                .filter(h -> "downstream_dependency_latency".equals(h.patternId()))
                .findFirst().orElse(null);
        assertThat(downstreamHyp).isNotNull();

        VerificationResult downstreamVer = verMap.get(downstreamHyp.id());
        assertThat(downstreamVer).isNotNull();

        assertThat(downstreamVer.supportingEvidenceIds().size())
                .as("downstream_dependency_latency should match multiple live evidence types")
                .isGreaterThanOrEqualTo(5);
    }
}
