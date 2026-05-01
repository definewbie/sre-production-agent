package ai.sreagent.core.hypothesis;

import ai.sreagent.core.domain.DiagnosticPattern;
import ai.sreagent.core.domain.Hypothesis;
import ai.sreagent.core.domain.IncidentTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates one Hypothesis per DiagnosticPattern for a given incident.
 * Deterministic: same input always produces same output.
 */
public class HypothesisEngine {

    private static final Map<String, HypothesisTemplate> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put("deployment_regression", new HypothesisTemplate(
                "hyp_deployment_regression",
                "Recent deployment introduced a regression",
                "change_regression",
                "Recent deployment or config change caused elevated errors"
        ));
        TEMPLATES.put("downstream_dependency_latency", new HypothesisTemplate(
                "hyp_downstream_dependency_latency",
                "Downstream dependency latency caused timeout errors",
                "dependency_latency",
                "payment-service latency may have caused order-service timeouts"
        ));
        TEMPLATES.put("pod_oom_killed", new HypothesisTemplate(
                "hyp_pod_oom_killed",
                "Pod OOMKilled or resource pressure caused service errors",
                "resource_pressure",
                "Pod memory pressure or OOMKilled events caused instability"
        ));
    }

    /**
     * Generate one hypothesis per registered diagnostic pattern.
     */
    public List<Hypothesis> generate(IncidentTask incident, List<DiagnosticPattern> patterns) {
        return patterns.stream()
                .map(pattern -> buildHypothesis(incident, pattern))
                .toList();
    }

    private Hypothesis buildHypothesis(IncidentTask incident, DiagnosticPattern pattern) {
        HypothesisTemplate tpl = TEMPLATES.getOrDefault(pattern.id(),
                new HypothesisTemplate(
                        "hyp_" + pattern.id(),
                        pattern.description(),
                        "unknown",
                        pattern.description()
                ));
        return new Hypothesis(
                tpl.hypothesisId(),
                incident.id(),
                pattern.id(),
                tpl.title(),
                tpl.rootCauseType(),
                incident.service(),
                tpl.candidateCause()
        );
    }

    private record HypothesisTemplate(
            String hypothesisId,
            String title,
            String rootCauseType,
            String candidateCause
    ) {}
}
