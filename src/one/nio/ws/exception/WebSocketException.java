package one.nio.ws.exception;

import java.io.IOException;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketException extends IOException {
    private final short code;

    public WebSocketException(short code, String message) {
        super(message);
        this.code = code;
    }

    public short code() {
        return code;
    }
}
