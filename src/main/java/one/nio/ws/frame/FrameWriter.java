/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.net.Session;
import one.nio.net.Socket;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class FrameWriter {
    private final Session session;

    public FrameWriter(Session session) {
        this.session = session;
    }

    public void write(Frame frame) throws IOException {
        final byte[] payload = frame.getPayload();
        final byte[] header = serializeHeader(frame.getRsv(), frame.getOpcode(), payload);
        session.write(header, 0, header.length, Socket.MSG_MORE);
        session.write(payload, 0, payload.length);
    }

    private byte[] serializeHeader(int rsv, Opcode opcode, byte[] payload) {
        int len = payload.length < 126 ? 2 : payload.length < 65536 ? 4 : 10;
        byte[] header = new byte[len];
        header[0] = (byte) (0x80 | (rsv << 4) | opcode.value);
        // Next write the mask && length
        if (payload.length < 126) {
            header[1] = (byte) (payload.length);
        } else if (payload.length < 65536) {
            header[1] = (byte) 126;
            header[2] = (byte) (payload.length >>> 8);
            header[3] = (byte) (payload.length & 0xFF);
        } else {
            // Will never be more than 2^31-1
            header[1] = (byte) 127;
            header[6] = (byte) (payload.length >>> 24);
            header[7] = (byte) (payload.length >>> 16);
            header[8] = (byte) (payload.length >>> 8);
            header[9] = (byte) payload.length;
        }
        return header;
    }
}
