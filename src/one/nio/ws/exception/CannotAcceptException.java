package one.nio.ws.exception;

import one.nio.ws.message.CloseMessage;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class CannotAcceptException extends WebSocketException {

    public CannotAcceptException(String message) {
        super(CloseMessage.CANNOT_ACCEPT, message);
    }
}
