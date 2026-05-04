package ai.sreagent.probe;

/**
 * Exception thrown when probe routing or execution fails.
 */
public class ProbeRoutingException extends RuntimeException {

    public ProbeRoutingException(String message) {
        super(message);
    }

    public ProbeRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
