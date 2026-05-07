package ai.sreagent.server.incident;

/**
 * Request body for triggering RCA from an alert.
 */
public record IncidentRcaTriggerRequest(
    /** Alert fingerprint to identify the target alert. */
    String fingerprint,
    /** Alternative: identify alert by alertname + service (if fingerprint unknown). */
    String alertName,
    /** Service name (used with alertName for matching). */
    String service
) {
    /**
     * Whether this request uses fingerprint-based matching.
     */
    public boolean hasFingerprint() {
        return fingerprint != null && !fingerprint.isBlank();
    }

    /**
     * Whether this request uses alertName+service matching.
     */
    public boolean hasNameMatch() {
        return alertName != null && !alertName.isBlank();
    }
}
