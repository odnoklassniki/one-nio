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

package one.nio.ws.message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import one.nio.net.Session;
import one.nio.ws.exception.TooBigException;
import one.nio.ws.exception.WebSocketException;
import one.nio.ws.extension.Extension;
import one.nio.ws.frame.Frame;
import one.nio.ws.frame.FrameReader;
import one.nio.ws.frame.Opcode;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class MessageReader {
    private final FrameReader reader;
    private final List<Extension> extensions;
    private final int maxMessagePayloadLength = Integer.getInteger("one.nio.ws.MAX_MESSAGE_PAYLOAD_LENGTH", 16 * 1024 * 1024);

    private PayloadBuffer buffer;

    public MessageReader(Session session, List<Extension> extensions) {
        this.reader = new FrameReader(session);
        this.extensions = extensions;
    }

    public Message<?> read() throws IOException {
        final Frame frame = reader.read();
        if (frame == null) {
            // not all frame data was read from socket
            return null;
        }
        if (frame.isControl()) {
            // control messages can not be fragmented
            // and it can be between 2 fragments of another message
            // so handle it separately
            return createMessage(frame.getOpcode(), getPayload(frame));
        }
        if (!frame.isFin()) {
            // not finished fragmented frame
            // append it to buffer and wait for next frames
            appendFrame(frame);
            return null;
        }
        if (buffer != null) {
            // buffer is not null, and this is the frame with fin=true
            // so collect all data from buffer to the resulting message
            appendFrame(frame);
            Message<?> message = createMessage(buffer.getOpcode(), buffer.getPayload());
            buffer = null;
            return message;
        }
        // just a simple message consisting of one fragment
        return createMessage(frame.getOpcode(), getPayload(frame));
    }

    private void appendFrame(Frame frame) throws IOException {
        if (buffer == null) {
            buffer = new PayloadBuffer(frame.getOpcode(), maxMessagePayloadLength);
        }
        buffer.append(getPayload(frame));
    }

    private Message<?> createMessage(Opcode opcode, byte[] payload) {
        switch (opcode) {
            case CLOSE:
                return new CloseMessage(payload);
            case PING:
                return new PingMessage(payload);
            case PONG:
                return new PongMessage(payload);
            case BINARY:
                return new BinaryMessage(payload);
            case TEXT:
                return new TextMessage(new String(payload, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("Unsupported opcode: " + opcode);
    }

    private byte[] getPayload(Frame frame) throws IOException {
        frame.unmask();
        for (Extension extension : extensions) {
            extension.transformInput(frame);
        }
        return frame.getPayload();
    }

    public static class PayloadBuffer {
        private final Opcode opcode;
        private final List<byte[]> chunks;
        private final int maxMessagePayloadLength;
        private int payloadLength;

        public PayloadBuffer(Opcode opcode, int maxMessagePayloadLength) {
            this.opcode = opcode;
            this.chunks = new ArrayList<>();
            this.maxMessagePayloadLength = maxMessagePayloadLength;
        }

        public Opcode getOpcode() {
            return opcode;
        }

        public byte[] getPayload() {
            final byte[] result = new byte[payloadLength];
            int pos = 0;
            for (byte[] chunk : chunks) {
                int length = chunk.length;
                System.arraycopy(chunk,0, result, pos, length);
                pos += length;
            }
            return result;
        }

        public void append(byte[] payload) throws WebSocketException {
            payloadLength += payload.length;
            if (payloadLength > this.maxMessagePayloadLength) {
                throw new TooBigException("payload can not be more than " + maxMessagePayloadLength);
            }
            chunks.add(payload);
        }
    }
}
