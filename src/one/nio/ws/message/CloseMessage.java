package one.nio.ws.message;

import one.nio.ws.frame.Opcode;

import static one.nio.util.JavaInternals.byteArrayOffset;
import static one.nio.util.JavaInternals.unsafe;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class CloseMessage extends Message<Short> {
    public static final short NORMAL = 1000;
    public static final short GOING_AWAY = 1001;
    public static final short PROTOCOL_ERROR = 1002;
    public static final short CANNOT_ACCEPT = 1003;
    public static final short RESERVED = 1004;
    public static final short NO_STATUS_CODE = 1005;
    public static final short CLOSED_ABNORMALLY = 1006;
    public static final short NOT_CONSISTENT = 1007;
    public static final short VIOLATED_POLICY = 1008;
    public static final short TOO_BIG = 1009;
    public static final short NO_EXTENSION = 1010;
    public static final short UNEXPECTED_CONDITION = 1011;
    public static final short SERVICE_RESTART = 1012;
    public static final short TRY_AGAIN_LATER = 1013;
    public static final short TLS_HANDSHAKE_FAILURE = 1015;

    public CloseMessage(byte[] payload) {
        this(payload.length == 0 ? null : Short.reverseBytes(unsafe.getShort(payload, byteArrayOffset)));
    }

    public CloseMessage(Short code) {
        super(Opcode.CLOSE, code);
    }

    @Override
    public byte[] payload() {
        byte code = payload.byteValue();
        return new byte[]{
                (byte) (code >>> 8), code
        };
    }

    @Override
    public String toString() {
        return "CloseMessage<" + payload + ">";
    }
}

