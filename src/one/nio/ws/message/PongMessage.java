package one.nio.ws.message;

import one.nio.http.Response;
import one.nio.ws.frame.Opcode;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class PongMessage extends BinaryMessage {
    public static final PongMessage EMPTY = new PongMessage(Response.EMPTY);

    public PongMessage(byte[] payload) {
        super(Opcode.PING, payload);
    }
}

