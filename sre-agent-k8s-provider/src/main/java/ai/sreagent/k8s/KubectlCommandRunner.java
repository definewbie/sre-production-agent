package ai.sreagent.k8s;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for running kubectl commands.
 * Separates process execution from resource reading logic.
 */
public interface KubectlCommandRunner {

    /**
     * Execute a kubectl command and return stdout.
     *
     * @param args kubectl arguments (without "kubectl" itself)
     * @return stdout from the command
     * @throws IOException if execution fails
     */
    String execute(List<String> args) throws IOException;

    /**
     * Check if kubectl is available.
     */
    boolean isKubectlAvailable();
}
