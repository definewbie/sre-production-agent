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
                "近期部署引入了回归缺陷",
                "change_regression",
                "最近的部署或配置变更导致了错误率升高"
        ));
        TEMPLATES.put("downstream_dependency_latency", new HypothesisTemplate(
                "hyp_downstream_dependency_latency",
                "下游依赖延迟导致超时错误",
                "dependency_latency",
                "payment-service 延迟可能导致了 order-service 超时"
        ));
        TEMPLATES.put("pod_oom_killed", new HypothesisTemplate(
                "hyp_pod_oom_killed",
                "Pod 内存溢出（OOMKilled）导致服务异常",
                "resource_pressure",
                "Pod 内存压力或 OOMKilled 事件导致服务不稳定"
        ));
        TEMPLATES.put("pod_crash_loop", new HypothesisTemplate(
                "hyp_pod_crash_loop",
                "容器崩溃循环导致服务不稳定",
                "kubernetes_crash_loop",
                "容器在 Kubernetes 中反复崩溃或重启"
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
