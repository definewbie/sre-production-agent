package ai.sreagent.alertmanager.relevance;

/**
 * Classification of an alert's relevance to the current SRE Agent service scope.
 *
 * Only SERVICE_ALERTs are eligible to trigger business RCA.
 * All other categories are informational and displayed separately.
 */
public enum AlertRelevance {
    /** Alert directly related to a demo/business service — eligible for RCA */
    SERVICE_ALERT,
    /** Platform/infrastructure alert (node, etcd, kubelet, prometheus, etc.) */
    PLATFORM_ALERT,
    /** Alerting pipeline health-check alert (e.g. Watchdog) */
    WATCHDOG_ALERT,
    /** Alert that cannot be mapped to any known service or supported namespace */
    UNSUPPORTED_ALERT,
    /** Alert explicitly excluded by ignore rules or silence */
    IGNORED_ALERT;

    /** Whether this relevance level allows triggering RCA. */
    public boolean isRcaEligible() {
        return this == SERVICE_ALERT;
    }
}
