/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.ws.frame;

import java.io.IOException;
import java.util.Arrays;

import one.nio.net.Session;
import one.nio.ws.exception.CannotAcceptException;
import one.nio.ws.exception.ProtocolException;
import one.nio.ws.exception.TooBigException;
import one.nio.ws.exception.WebSocketException;

/**
 * Websocket frame reader
 * https://datatracker.ietf.org/doc/html/rfc6455#section-5.2
 *
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class FrameReader {
    // The smallest valid full WebSocket message is 2 bytes,
    // such as this close message sent from the server with no payload: 138, 0.
    // Yet the longest possible header is 14 bytes
    // which would represent a message sent from the client to the server with a payload greater then 64KB.
    private static final int HEADER_LENGTH = 14;
    private static final int FIRST_HEADER_LENGTH = 2;
    private static final int MASK_LENGTH = 4;

    private final Session session;
    private final byte[] header;
    private final int maxFramePayloadLength = Integer.getInteger("one.nio.ws.MAX_FRAME_PAYLOAD_LENGTH", 128 * 1024);

    private Frame frame;
    private int ptr;

    public FrameReader(Session session) {
        this.session = session;
        this.header = new byte[HEADER_LENGTH];
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
            result |= ((b[i] & 0xFF) << shift);
            shift += 8;
        }
        return result;
    }
}
