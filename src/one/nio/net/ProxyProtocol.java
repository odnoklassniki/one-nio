/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.net;

import one.nio.util.Utf8;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * PROXY protocol handler for the server side.
 * See http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt
 */
public class ProxyProtocol {

    private static final byte[] PROXY_PROTOCOL_START = Utf8.toBytes("PROXY TCP");

    // PROXY TCP6 ffff:f...f:ffff ffff:f...f:ffff 65535 65535\r\n
    private static final int MAX_PROXY_PROTOCOL_HEADER = 104;
    // PROXY TCP6 :: :: 1 1\r\n
    private static final int MIN_PROXY_PROTOCOL_HEADER = 22;

    public static InetSocketAddress parse(Socket socket, byte[] buffer) throws IOException {
        // Look at raw (non-TLS) data in the socket buffer without removing it.
        // In practice, the whole header should fit into one TCP segment.
        Socket rawSocket = socket.sslUnwrap();
        int bytesRead = rawSocket.read(buffer, 0, MAX_PROXY_PROTOCOL_HEADER, Socket.MSG_PEEK);

        // Check if data starts with "PROXY TCPx ", otherwise treat it as a normal request
        int lineEnd = Utf8.indexOf((byte) '\n', buffer, 0, bytesRead) + 1;
        if (lineEnd < MIN_PROXY_PROTOCOL_HEADER || !Utf8.startsWith(PROXY_PROTOCOL_START, buffer, 0) || buffer[10] != ' ') {
            return null;
        }

        byte[] addr;
        int dstIpStart;
        if (buffer[9] == '4') {
            dstIpStart = parseIPv4(buffer, 11, addr = new byte[4]);
        } else {
            dstIpStart = parseIPv6(buffer, 11, addr = new byte[16]);
        }

        int dstIpEnd = Utf8.indexOf((byte) ' ', buffer, dstIpStart, lineEnd - dstIpStart);
        if (dstIpEnd <= dstIpStart) {
            return null;
        }

        int port = parsePort(buffer, dstIpEnd + 1);

        // Remove the header from the socket buffer.
        // We might have read more data than the proxy header.
        rawSocket.read(buffer, 0, lineEnd, Socket.MSG_TRUNC);

        return new InetSocketAddress(InetAddress.getByAddress(addr), port);
    }

    private static int parseIPv4(byte[] buffer, int offset, byte[] addr) {
        int pos = 0;
        int val = 0;

        while (true) {
            byte b = buffer[offset++];
            if (b >= '0' && b <= '9') {
                val = val * 10 + (b - '0');
            } else if (b == (pos < 3 ? '.' : ' ') && val >= 0 && val <= 255) {
                addr[pos++] = (byte) val;
                if (b == ' ') break;
                val = 0;
            } else {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
        }

        return offset;
    }

    private static int parseIPv6(byte[] buffer, int offset, byte[] addr) {
        int pos = 0;
        int val = 0;

        while (true) {
            byte b = buffer[offset++];
            if (b >= '0' && b <= '9') {
                val = val * 16 + (b - '0');
            } else if (b >= 'a' && b <= 'f') {
                val = val * 16 + (b - 'a');
            } else if (b >= 'A' && b <= 'F') {
                val = val * 16 + (b - 'A');
            } else if (b == (pos < 14 ? ':' : ' ') && val >= 0 && val <= 0xffff) {
                addr[pos++] = (byte) (val >>> 8);
                addr[pos++] = (byte) val;
                if (b == ' ') break;
                val = 0;
            } else {
                throw new IllegalArgumentException("Invalid IPv6 address");
            }
        }

        return offset;
    }

    private static int parsePort(byte[] buffer, int offset) {
        int port = 0;

        while (true) {
            byte b = buffer[offset++];
            if (b >= '0' && b <= '9') {
                port = port * 10 + (b - '0');
            } else if (b == ' ' && port >= 0 && port <= 65535) {
                return port;
            } else {
                throw new IllegalArgumentException("Invalid port");
            }
        }
    }
}
