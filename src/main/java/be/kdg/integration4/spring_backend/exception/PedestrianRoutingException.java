package be.kdg.integration4.spring_backend.exception;

public class PedestrianRoutingException extends RuntimeException {
    public PedestrianRoutingException(String message) {
        super(message);
    }

    public PedestrianRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
