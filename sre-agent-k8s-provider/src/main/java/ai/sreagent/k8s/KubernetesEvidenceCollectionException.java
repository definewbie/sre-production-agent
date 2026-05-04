package ai.sreagent.k8s;

/**
 * Runtime exception for Kubernetes evidence collection failures.
 * Wraps API errors, configuration errors, and cluster connectivity issues.
 */
public class KubernetesEvidenceCollectionException extends RuntimeException {

    public KubernetesEvidenceCollectionException(String message) {
        super(message);
    }

    public KubernetesEvidenceCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
