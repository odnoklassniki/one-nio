package one.nio.util;

// Fast-to-throw unchecked exception that can be used to interrupt loops
public class BreakException extends RuntimeException {
    public static final BreakException INSTANCE = new BreakException();

    public BreakException() {
        super(null, null, false, false);
    }

    public BreakException(String message) {
        super(message, null, false, false);
    }

    public BreakException(String message, Throwable cause) {
        super(message, cause, false, false);
    }

    public BreakException(Throwable cause) {
        super(null, cause, false, false);
    }
}
