package ai.sreagent.alertmanager.relevance;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;

import java.util.Set;

/**
 * Classifies Alertmanager alerts by relevance to the current SRE Agent service scope.
 *
 * Rules (evaluated in order):
 * 1. Watchdog → WATCHDOG_ALERT
 * 2. Known platform alert names → PLATFORM_ALERT
 * 3. Known platform namespaces/jobs → PLATFORM_ALERT (unless explicitly a demo service)
 * 4. Demo namespace or demo service match → SERVICE_ALERT
 * 5. Everything else → UNSUPPORTED_ALERT
 */
public class AlertRelevanceClassifier {

    private static final Set<String> DEMO_SERVICES = Set.of(
            "order-service", "payment-service", "inventory-service"
    );

    private static final Set<String> DEMO_NAMESPACES = Set.of("demo");

    private static final Set<String> PLATFORM_NAMESPACES = Set.of(
            "kube-system", "monitoring", "observability"
    );

    private static final Set<String> PLATFORM_JOBS = Set.of(
            "prometheus", "alertmanager", "kubelet", "node-exporter",
            "etcd", "kube-state-metrics", "kube-proxy"
    );

    private static final Set<String> PLATFORM_ALERT_PREFIXES = Set.of(
            "Node", "Kube", "etcd"
    );

    private static final Set<String> PLATFORM_ALERT_CONTAINS = Set.of(
            "Clock", "Node", "Etcd"
    );

    /**
     * Classify a single alert.
     *
     * @param alert parsed alert from Alertmanager
     * @return classification result with relevance, eligibility, and reason
     */
    public ClassifiedAlert classify(AlertmanagerAlert alert) {
        if (alert == null) {
            return new ClassifiedAlert(null, AlertRelevance.UNSUPPORTED_ALERT,
                    false, "Alert is null");
        }

        String alertName = alert.alertName();
        String service = alert.service();
        String namespace = alert.namespace();
        String job = alert.labels().getOrDefault("job", "");

        // 1. Watchdog check
        if ("Watchdog".equals(alertName)) {
            return new ClassifiedAlert(alert, AlertRelevance.WATCHDOG_ALERT, false,
                    "Watchdog 是告警链路自检告警，不应触发业务 RCA");
        }

        // 2. Platform alert name check
        for (String prefix : PLATFORM_ALERT_PREFIXES) {
            if (alertName.startsWith(prefix)) {
                return new ClassifiedAlert(alert, AlertRelevance.PLATFORM_ALERT, false,
                    "平台告警（" + alertName + "），不属于当前业务服务范围");
            }
        }

        // 3. Platform alert name contains check
        for (String keyword : PLATFORM_ALERT_CONTAINS) {
            if (alertName.contains(keyword)) {
                return new ClassifiedAlert(alert, AlertRelevance.PLATFORM_ALERT, false,
                    "平台告警（" + alertName + "），不属于当前业务服务范围");
            }
        }

        // 4. Demo service or namespace match → SERVICE_ALERT
        if (isDemoService(service)) {
            return new ClassifiedAlert(alert, AlertRelevance.SERVICE_ALERT, true, null);
        }

        if (DEMO_NAMESPACES.contains(namespace) && !isPlatformJob(job)) {
            // In demo namespace but check it's not a platform job
            return new ClassifiedAlert(alert, AlertRelevance.SERVICE_ALERT, true, null);
        }

        // 5. Platform namespace check
        if (PLATFORM_NAMESPACES.contains(namespace)) {
            return new ClassifiedAlert(alert, AlertRelevance.PLATFORM_ALERT, false,
                    "平台命名空间（" + namespace + "），不属于当前业务服务范围");
        }

        // 6. Platform job check
        if (isPlatformJob(job)) {
            return new ClassifiedAlert(alert, AlertRelevance.PLATFORM_ALERT, false,
                    "平台组件（" + job + "），不属于当前业务服务范围");
        }

        // 7. TargetDown special: check if target maps to demo service
        if ("TargetDown".equals(alertName)) {
            // TargetDown with demo-related labels → SERVICE_ALERT
            if (isDemoService(service) || DEMO_NAMESPACES.contains(namespace)) {
                return new ClassifiedAlert(alert, AlertRelevance.SERVICE_ALERT, true, null);
            }
            // TargetDown for platform targets
            return new ClassifiedAlert(alert, AlertRelevance.PLATFORM_ALERT, false,
                    "TargetDown 目标（" + service + "）不属于当前业务服务范围");
        }

        // 8. Default: unsupported
        return new ClassifiedAlert(alert, AlertRelevance.UNSUPPORTED_ALERT, false,
                "告警（" + alertName + " / " + service + "）无法映射到当前支持的业务服务");
    }

    private boolean isDemoService(String service) {
        return DEMO_SERVICES.contains(service);
    }

    private boolean isPlatformJob(String job) {
        if (job == null || job.isEmpty()) return false;
        // Match exact or contains (e.g., "prometheus-kube-prometheus" contains "prometheus")
        for (String pj : PLATFORM_JOBS) {
            if (job.equals(pj) || job.contains(pj)) return true;
        }
        return false;
    }

    /**
     * Classification result for a single alert.
     */
    public record ClassifiedAlert(
            AlertmanagerAlert alert,
            AlertRelevance relevance,
            boolean rcaEligible,
            String ineligibleReason
    ) {}
}
