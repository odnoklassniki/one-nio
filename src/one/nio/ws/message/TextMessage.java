package one.nio.ws.message;

import java.nio.charset.StandardCharsets;

import one.nio.ws.frame.Opcode;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class TextMessage extends Message<String> {

    public TextMessage(String payload) {
        super(Opcode.TEXT, payload);
    }

    public TextMessage(CharSequence payload) {
        this(payload.toString());
    }

    public TextMessage(byte[] payload) {
        this(new String(payload, StandardCharsets.UTF_8));
    }

    @Override
    public byte[] payload() {
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "TextMessage<" + payload + ">";
    }
}
