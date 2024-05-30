package one.nio.ws.message;

import one.nio.ws.frame.Opcode;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public abstract class Message<T> {
    protected final Opcode opcode;
    protected final T payload;

    protected Message(Opcode opcode, T payload) {
        this.opcode = opcode;
        this.payload = payload;
    }

    public Opcode opcode() {
        return opcode;
    }

    public abstract byte[] payload();
}
