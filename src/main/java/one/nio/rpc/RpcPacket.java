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

package one.nio.rpc;

import one.nio.net.Socket;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RpcPacket {
    private static final Logger log = LoggerFactory.getLogger(RpcPacket.class);

    private static final int WARN_PACKET_SIZE = 4 * 1024 * 1024;
    private static final int ERROR_PACKET_SIZE = 128 * 1024 * 1024;

    static final int STREAM_HEADER = 0xEDAEDA03;
    static final byte[] STREAM_HEADER_ARRAY = {
            (byte) (STREAM_HEADER >>> 24),
            (byte) (STREAM_HEADER >>> 16),
            (byte) (STREAM_HEADER >>> 8),
            (byte) STREAM_HEADER
    };

    static final int HTTP_GET = 'G' << 24 | 'E' << 16 | 'T' << 8 | ' ';
    static final int HTTP_POST = 'P' << 24 | 'O' << 16 | 'S' << 8 | 'T';
    static final int HTTP_HEAD = 'H' << 24 | 'E' << 16 | 'A' << 8 | 'D';

    static boolean isHttpHeader(int header) {
        return header == HTTP_GET || header == HTTP_POST || header == HTTP_HEAD;
    }

    static int getSize(byte[] buffer) {
        return buffer[0] << 24 | (buffer[1] & 0xff) << 16 | (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff);
    }

    static void checkWriteSize(int size) throws IOException {
        if (size >= ERROR_PACKET_SIZE) {
            throw new IOException("RPC packet is too large: " + size);
        }
    }

    static void checkReadSize(int size, Socket socket) throws IOException {
        if (size <= 0 || size >= ERROR_PACKET_SIZE) {
            throw new IOException("Invalid RPC packet from " + socket.getRemoteAddress());
        } else if (size >= WARN_PACKET_SIZE) {
            log.warn("RPC packet from {} is too large: {}", socket.getRemoteAddress(), size);
        }
    }
}
