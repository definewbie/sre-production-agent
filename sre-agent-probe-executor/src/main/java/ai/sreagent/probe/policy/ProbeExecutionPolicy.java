package ai.sreagent.probe.policy;

import ai.sreagent.probe.ProbeExecutionMode;
import ai.sreagent.probe.ProbeExecutionPlan;

/**
 * Policy gate for probe execution.
 * Enforces: no LIVE mode, canAffectDecision=false, max probe count.
 */
public class ProbeExecutionPolicy {

    private static final int DEFAULT_MAX_PROBES = 10;
    private final boolean liveEnabled;
    private final int maxProbes;

    public ProbeExecutionPolicy() {
        this(false, DEFAULT_MAX_PROBES);
    }

    public ProbeExecutionPolicy(boolean liveEnabled, int maxProbes) {
        this.liveEnabled = liveEnabled;
        this.maxProbes = maxProbes;
    }

    public boolean allows(ProbeExecutionPlan plan) {
        // LIVE mode disabled by default
        if (plan.mode() == ProbeExecutionMode.LIVE && !liveEnabled) {
            return false;
        }
        // canAffectDecision must be false (enforced by record constructor too)
        if (plan.canAffectDecision()) {
            return false;
        }
        // Max probe count
        if (plan.probeIntents().size() > maxProbes) {
            return false;
        }
        return true;
    }

    public int maxProbes() {
        return maxProbes;
    }

    public boolean liveEnabled() {
        return liveEnabled;
    }
}
