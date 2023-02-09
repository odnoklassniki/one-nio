package one.nio.ws.exception;

import one.nio.ws.message.CloseMessage;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class ProtocolException extends WebSocketException {

    public ProtocolException(String message) {
        super(CloseMessage.PROTOCOL_ERROR, message);
    }
}
