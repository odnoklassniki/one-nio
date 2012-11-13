package one.nio.mem;

public class OutOfMemoryException extends RuntimeException {

    public OutOfMemoryException() {
    }

    public OutOfMemoryException(String message) {
        super(message);
    }

    public OutOfMemoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public OutOfMemoryException(Throwable cause) {
        super(cause);
    }
}
