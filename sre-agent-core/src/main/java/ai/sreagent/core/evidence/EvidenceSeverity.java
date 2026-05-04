package ai.sreagent.core.evidence;

/**
 * Severity classification for evidence.
 * Derived from strength when not explicitly specified:
 *   strength >= 0.85 → CRITICAL
 *   strength >= 0.60 → WARNING
 *   else             → INFO
 */
public enum EvidenceSeverity {
    INFO,
    WARNING,
    CRITICAL,
    UNKNOWN;

    /**
     * Derive severity from evidence strength.
     */
    public static EvidenceSeverity fromStrength(double strength) {
        if (strength >= 0.85) return CRITICAL;
        if (strength >= 0.60) return WARNING;
        return INFO;
    }
}
