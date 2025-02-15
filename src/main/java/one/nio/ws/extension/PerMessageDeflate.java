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

package one.nio.ws.extension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import one.nio.ws.exception.HandshakeException;
import one.nio.ws.frame.Frame;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class PerMessageDeflate implements Extension {
    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";

    // according to rfc7692 4 octets of 0x00 0x00 0xff 0xff must be at the tail end of the payload of the message
    // https://datatracker.ietf.org/doc/html/rfc7692#section-7.2.2
    private static final byte[] EOM_BYTES = new byte[] {0, 0, -1, -1};
    // the deflate extension requires the RSV1 bit, so RSV1 is 4 (0b100)
    private static final int RSV_BITMASK = 0b100;

    private static final int INPUT_BUFFER_SIZE = Integer.getInteger("one.nio.ws.permessage-deflate.INPUT_BUFFER_SIZE", 2048);
    private static final int OUTPUT_BUFFER_SIZE = Integer.getInteger("one.nio.ws.permessage-deflate.OUTPUT_BUFFER_SIZE", 2048);

    public static final String NAME = "permessage-deflate";

    private final boolean clientContextTakeover;
    private final boolean serverContextTakeover;

    private final Inflater inflater = new Inflater(true);
    private final byte[] inputBuffer = new byte[INPUT_BUFFER_SIZE];
    private boolean skipDecompression = false;

    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final byte[] outputBuffer = new byte[OUTPUT_BUFFER_SIZE];

    public static PerMessageDeflate negotiate(Map<String, String> parameters) {
        boolean clientContextTakeover = true;
        boolean serverContextTakeover = true;

        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            final String name = parameter.getKey();

            if (SERVER_NO_CONTEXT_TAKEOVER.equals(name)) {
                if (serverContextTakeover) {
                    serverContextTakeover = false;
                } else {
                    throw new HandshakeException("Duplicate definition of the server_no_context_takeover extension parameter");
                }
            }
            if (CLIENT_NO_CONTEXT_TAKEOVER.equals(name)) {
                if (clientContextTakeover) {
                    clientContextTakeover = false;
                } else {
                    throw new HandshakeException("Duplicate definition of the client_no_context_takeover extension parameter");
                }
            }
        }

        return new PerMessageDeflate(clientContextTakeover, serverContextTakeover);
    }

    private PerMessageDeflate(boolean clientContextTakeover, boolean serverContextTakeover) {
        this.clientContextTakeover = clientContextTakeover;
        this.serverContextTakeover = serverContextTakeover;
    }

    @Override
    public void appendResponseHeaderValue(StringBuilder builder) {
        builder.append(NAME);

        if (!clientContextTakeover) {
            builder.append("; ").append(CLIENT_NO_CONTEXT_TAKEOVER);
        }

        if (!serverContextTakeover) {
            builder.append("; ").append(SERVER_NO_CONTEXT_TAKEOVER);
        }
    }

    @Override
    public void transformInput(Frame frame) throws IOException {
        if (frame.isControl()) {
            // Control frames are never compressed and may appear in the middle of fragmented frames.
            // Pass them straight through.
            return;
        }

        if (!frame.getOpcode().isContinuation()) {
            // First frame in new message
            skipDecompression = (frame.getRsv() & RSV_BITMASK) == 0;
        }

        if (skipDecompression) {
            // Pass uncompressed frames straight through.
            return;
        }

        frame.setPayload(decompress(frame.isFin(), frame.getPayload()));
        frame.setRsv(frame.getRsv() & ~RSV_BITMASK);
    }

    @Override
    public void transformOutput(Frame frame) throws IOException {
        if (frame.isControl()) {
            // Control frames are never compressed and may appear in the middle of fragmented frames
            // Pass them straight through
            return;
        }

        if (frame.getPayloadLength() == 0) {
            // Zero length messages can't be compressed so pass them straight through
            return;
        }

        frame.setPayload(compress(frame.getPayload()));
        frame.setRsv(frame.getRsv() + RSV_BITMASK);
    }

    @Override
    public void close() {
        inflater.end();
        deflater.end();
    }

    private byte[] decompress(boolean fin, byte[] payload) throws IOException {
        boolean usedEomBytes = false;

        if (payload == null || payload.length == 0) {
            return payload;
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            inflater.setInput(payload);
            while (true) {
                int uncompressedBytes;

                try {
                    uncompressedBytes = inflater.inflate(inputBuffer);
                } catch (DataFormatException e) {
                    throw new IOException("Failed to decompress WebSocket frame", e);
                }

                if (uncompressedBytes > 0) {
                    out.write(inputBuffer, 0, uncompressedBytes);
                } else {
                    if (inflater.needsInput() && !usedEomBytes) {
                        if (fin) {
                            inflater.setInput(EOM_BYTES);
                            usedEomBytes = true;
                        } else {
                            break;
                        }
                    } else {
                        if (fin && !clientContextTakeover) {
                            inflater.reset();
                        }
                        break;
                    }
                }
            }

            return out.toByteArray();
        }
    }

    private byte[] compress(byte[] payload) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            deflater.setInput(payload, 0, payload.length);

            int compressedLength;
            do {
                compressedLength = deflater.deflate(outputBuffer, 0, outputBuffer.length, Deflater.SYNC_FLUSH);
                out.write(outputBuffer, 0, compressedLength);
            } while (compressedLength > 0);

            byte[] result = out.toByteArray();

            // https://tools.ietf.org/html/rfc7692#section-7.2.1 states that if the final fragment's compressed
            // payload ends with 0x00 0x00 0xff 0xff, they should be removed.
            // To simulate removal, we just pass 4 bytes less to the new payload
            // if the frame is final and outputBytes ends with 0x00 0x00 0xff 0xff.
            if (endsWithTail(result)) {
                result = Arrays.copyOf(result, result.length - EOM_BYTES.length);
            }

            if (!serverContextTakeover) {
                deflater.reset();
            }

            return result;
        }
    }

    private boolean endsWithTail(byte[] payload){
        if(payload.length < 4) {
            return false;
        }

        int length = payload.length;

        for (int i = 0; i < EOM_BYTES.length; i++) {
            if (EOM_BYTES[i] != payload[length - EOM_BYTES.length + i]) {
                return false;
            }
        }

        return true;
    }
}
