package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification test using Scenario E data files.
 * Loads actual JSON from classpath, runs full Step B chain:
 *   Alert → HypothesisEngine → VerificationEngine → results
 */
class ScenarioEVerificationTest {

    private EvidenceLoader loader;
    private HypothesisEngine hypothesisEngine;
    private VerificationEngine verificationEngine;
    private PatternRegistry registry;

    private IncidentTask incident;
    private List<Evidence> evidence;
    private List<Hypothesis> hypotheses;
    private Map<String, VerificationResult> results;

    @BeforeEach
    void setUp() throws Exception {
        loader = new EvidenceLoader();
        hypothesisEngine = new HypothesisEngine();
        verificationEngine = new VerificationEngine();
        registry = BuiltinPatterns.defaultRegistry();

        // Load from classpath (copied in Step A)
        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json")) {
            assertThat(alertIs).as("Alert JSON must exist on classpath").isNotNull();
            assertThat(evidenceIs).as("Evidence JSON must exist on classpath").isNotNull();

            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        // Run the Step B chain
        hypotheses = hypothesisEngine.generate(incident, registry.all());

        Map<String, DiagnosticPattern> patternMap = registry.all().stream()
                .collect(Collectors.toMap(DiagnosticPattern::id, p -> p));

        results = verificationEngine.verifyAll(hypotheses, patternMap, evidence);
    }

    @Test
    void shouldGenerateFourHypotheses() {
        assertThat(hypotheses).hasSize(4);
    }

    @Test
    void deploymentRegression_shouldBeWellSupported() {
        VerificationResult result = results.get("hyp_deployment_regression");
        assertThat(result).as("deployment_regression result must exist").isNotNull();

        // Supporting: deploy_event, error_rate_spike, dependency_timeout_logs, retry_timeout_config_change
        assertThat(result.supportingEvidenceIds()).hasSizeGreaterThanOrEqualTo(3);

        // Counter: historical_timeout_logs_present, downstream_latency_spike
        assertThat(result.counterEvidenceIds()).hasSizeGreaterThanOrEqualTo(2);

        // Should have contradictions
        assertThat(result.contradictions()).isNotEmpty();
    }

    @Test
    void downstreamDependencyLatency_shouldBeWellSupported() {
        VerificationResult result = results.get("hyp_downstream_dependency_latency");
        assertThat(result).as("downstream_dependency_latency result must exist").isNotNull();

        // Supporting: dependency_timeout_logs, downstream_latency_spike, service_dependency_match
        assertThat(result.supportingEvidenceIds()).hasSizeGreaterThanOrEqualTo(3);

        // Counter: downstream_5xx_absent, deploy_event_near_alert_window
        assertThat(result.counterEvidenceIds()).hasSizeGreaterThanOrEqualTo(2);

        // Should have contradictions about competing explanations
        assertThat(result.contradictions()).isNotEmpty();
    }

    @Test
    void podOomKilled_shouldBeWeakOrUnsupported() {
        VerificationResult result = results.get("hyp_pod_oom_killed");
        assertThat(result).as("pod_oom_killed result must exist").isNotNull();

        // No OOM-related evidence in Scenario E data
        assertThat(result.supportingEvidenceIds()).isEmpty();
        assertThat(result.contradictions()).isNotEmpty();
    }

    @Test
    void deploymentRegression_shouldHaveSpecificContradictions() {
        VerificationResult result = results.get("hyp_deployment_regression");

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("existed before the deployment"));

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("dependency latency"));
    }

    @Test
    void downstreamDependencyLatency_shouldHaveSpecificContradictions() {
        VerificationResult result = results.get("hyp_downstream_dependency_latency");

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("5xx"));

        assertThat(result.contradictions())
                .anyMatch(c -> c.contains("deployment"));
    }

    @Test
    void allResults_shouldHaveNonEmptyExplanations() {
        assertThat(results.values())
                .allMatch(r -> r.explanation() != null && !r.explanation().isBlank());
    }

    @Test
    void evidenceShouldLoadCorrectly() {
        assertThat(evidence).hasSize(8);
        assertThat(incident.id()).isEqualTo("inc_20260428_1008");
        assertThat(incident.service()).isEqualTo("order-service");
    }
}
