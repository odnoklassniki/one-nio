package one.nio.ws.exception;

import one.nio.ws.message.CloseMessage;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class TooBigException extends WebSocketException {

    public TooBigException(String message) {
        super(CloseMessage.TOO_BIG, message);
    }
}
