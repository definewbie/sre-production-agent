package ai.sreagent.k8s;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Real kubectl command runner using system process execution.
 * For local development and demo scenarios with kind/minikube.
 */
public class ProcessKubectlCommandRunner implements KubectlCommandRunner {

    private final long timeoutSeconds;

    public ProcessKubectlCommandRunner() {
        this(30);
    }

    public ProcessKubectlCommandRunner(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String execute(List<String> args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add("kubectl");
        pb.command().addAll(args);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("kubectl command timed out after " + timeoutSeconds + "s");
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                throw new IOException("kubectl exited with code " + process.exitValue() + ": " + output);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("kubectl command interrupted", e);
        }
    }

    @Override
    public boolean isKubectlAvailable() {
        try {
            Process process = new ProcessBuilder("kubectl", "version", "--client").start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
