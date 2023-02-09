package one.nio.ws.frame;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public enum Opcode {
    CONTINUATION(0x00),
    TEXT(0x01),
    BINARY(0x02),
    CLOSE(0x08),
    PING(0x09),
    PONG(0x0A);

    private static final Opcode[] VALUES;
    static {
        VALUES = new Opcode[11];
        for (Opcode opcode : Opcode.values()) {
            if (VALUES[opcode.value] != null) {
                throw new IllegalArgumentException("Opcode " + opcode.value + " already used.");
            }
            VALUES[opcode.value] = opcode;
        }
    }

    public final byte value;

    Opcode(int value) {
        this.value = (byte) value;
    }

    public boolean isControl() {
        return (value & 0x08) > 0;
    }

    public boolean isContinuation() {
        return this == CONTINUATION;
    }

    public static Opcode valueOf(int value) {
        return VALUES[value];
    }
}
