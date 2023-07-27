package one.nio.ws.frame;

import java.io.IOException;
import java.util.Arrays;

import one.nio.net.Session;
import one.nio.ws.exception.CannotAcceptException;
import one.nio.ws.exception.ProtocolException;
import one.nio.ws.exception.TooBigException;
import one.nio.ws.exception.WebSocketException;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class FrameReader {
    private static final int FIRST_HEADER_LENGTH = 2;
    private static final int MASK_LENGTH = 4;

    private final Session session;
    private final byte[] header;
    private final int maxFramePayloadLength;

    private Frame frame;
    private int ptr;

    public FrameReader(Session session, int maxFramePayloadLength) {
        this.session = session;
        this.header = new byte[10];
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    public Frame read() throws IOException {
        Frame frame = this.frame;
        int ptr = this.ptr;

        if (frame == null) {
            ptr += session.read(header, ptr, FIRST_HEADER_LENGTH - ptr);

            if (ptr < FIRST_HEADER_LENGTH) {
                this.ptr = ptr;
                return null;
            }

            frame = createFrame(header);
            ptr = 0;
            this.frame = frame;
        }

        if (frame.getPayload() == null) {
            int payloadLength = frame.getPayloadLength();
            int len = payloadLength == 126 ? 2 : payloadLength == 127 ? 8 : 0;
            if (len > 0) {
                ptr += session.read(header, ptr, len - ptr);
                if (ptr < len) {
                    this.ptr = ptr;
                    return null;
                }
                payloadLength = byteArrayToInt(header, len);
            }
            if (payloadLength < 0) {
                throw new ProtocolException("negative payload length");
            }
            if (payloadLength > maxFramePayloadLength) {
                throw new TooBigException("payload can not be more than " + maxFramePayloadLength);
            }
            frame.setPayload(new byte[payloadLength]);
            ptr = 0;
        }

        if (frame.getMask() == null) {
            ptr += session.read(header, ptr, MASK_LENGTH - ptr);

            if (ptr < MASK_LENGTH) {
                this.ptr = ptr;
                return null;
            }

            frame.setMask(Arrays.copyOf(header, MASK_LENGTH));
            ptr = 0;
        }

        if (ptr < frame.getPayloadLength()) {
            ptr += session.read(frame.getPayload(), ptr, frame.getPayloadLength() - ptr);

            if (ptr < frame.getPayloadLength()) {
                this.ptr = ptr;
                return null;
            }
        }

        this.frame = null;
        this.ptr = 0;

        return frame;
    }

    private Frame createFrame(byte[] header) throws WebSocketException {
        byte b0 = header[0];
        byte b1 = header[1];

        boolean fin = (b0 & 0x80) > 0;
        int rsv = (b0 & 0x70) >>> 4;
        Opcode opcode = Opcode.valueOf(b0 & 0x0F);
        int payloadLength = b1 & 0x7F;

        if ((b1 & 0x80) == 0) {
            throw new ProtocolException("not masked");
        }

        if (opcode == null) {
            throw new CannotAcceptException("invalid opcode (" + (b0 & 0x0F) + ')');
        } else if (opcode.isControl()) {
            if (payloadLength > 125) {
                throw new ProtocolException("control payload too big");
            }

            if (!fin) {
                throw new ProtocolException("control payload can not be fragmented");
            }
        }

        return new Frame(fin, opcode, rsv, payloadLength);
    }

    private int byteArrayToInt(byte[] b, int len) {
        int result = 0;
        int shift = 0;
        for (int i = len - 1; i >= 0; i--) {
            result = result + ((b[i] & 0xFF) << shift);
            shift += 8;
        }
        return result;
    }
}
