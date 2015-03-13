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

package one.nio.rpc;

import one.nio.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

class RpcPacket {
    private static final Log log = LogFactory.getLog(RpcPacket.class);

    private static final int WARN_PACKET_SIZE = 4 * 1024 * 1024;
    private static final int ERROR_PACKET_SIZE = 128 * 1024 * 1024;

    static int getSize(byte[] buffer, Socket socket) throws IOException {
        int size = buffer[0] << 24 | (buffer[1] & 0xff) << 16 | (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff);

        if (size < 0 || size >= ERROR_PACKET_SIZE) {
            throw new IOException("Invalid RPC packet from " + socket.getRemoteAddress());
        } else if (size >= WARN_PACKET_SIZE) {
            log.warn("RPC packet from " + socket.getRemoteAddress() + " is too large: " + size);
        }

        return size;
    }
}
