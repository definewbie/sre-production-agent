package ai.sreagent.alertmanager.mapper;

/**
 * Alertmanager evidence type constants.
 * These map to generic Evidence.evidenceType strings.
 */
public final class AlertmanagerEvidenceTypes {

    private AlertmanagerEvidenceTypes() {}

    public static final String ALERT_FIRING = "alert_firing";
    public static final String ALERT_RESOLVED = "alert_resolved";
    public static final String ALERT_STILL_FIRING = "alert_still_firing";
    public static final String ALERT_SEVERITY_HIGH = "alert_severity_high";
    public static final String ALERT_GROUPED = "alert_grouped";
    public static final String ALERT_SILENCED = "alert_silenced";
    public static final String ALERT_INHIBITED = "alert_inhibited";
    public static final String ALERT_NEAR_WINDOW = "alert_near_window";
    public static final String ALERT_NO_SIGNAL = "alert_no_signal";

    /** Source identifier for all Alertmanager evidence */
    public static final String SOURCE = "alertmanager";
}
