package ai.sreagent.probe;

/**
 * Executes a probe plan, routing each probe to the appropriate
 * provider and collecting evidence.
 */
public interface ProbeExecutor {
    ProbeExecutionResult execute(ProbeExecutionPlan plan);
}
