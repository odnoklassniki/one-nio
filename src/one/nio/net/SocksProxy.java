/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.net.ConnectException;

public class SocksProxy implements Proxy {
    private final String proxyHost;
    private final int proxyPort;
    private String user;
    private String password;

    public SocksProxy(String proxyHost, int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public SocksProxy withAuth(String user, String password) {
        this.user = user;
        this.password = password;
        return this;
    }

    @Override
    public void connect(Socket socket, String host, int port) throws IOException {
        socket.connect(proxyHost, proxyPort);

        ByteArrayBuilder builder = new ByteArrayBuilder(400);
        byte authMethod = user == null || password == null ? (byte) 0 : (byte) 2;

        // Handshake
        builder.append((byte) 5)
                .append((byte) 1)
                .append(authMethod);

        // Auth
        if (authMethod != 0) {
            int userLength = Utf8.length(user);
            int passwordLength = Utf8.length(password);
            builder.append((byte) 1)
                    .append((byte) userLength)
                    .append(user)
                    .append((byte) passwordLength)
                    .append(password);
        }

        // Connect
        builder.append((byte) 5)
                .append((byte) 1)
                .append((byte) 0)
                .append((byte) 3)
                .append((byte) host.length())
                .append(host)
                .append((byte) (port >>> 8))
                .append((byte) port);

        // Send all commands in one packet to avoid extra round trips
        socket.writeFully(builder.buffer(), 0, builder.length());

        readResponse(socket, builder.buffer(), authMethod);
    }

    private void readResponse(Socket socket, byte[] buf, byte authMethod) throws IOException {
        // Handshake response
        socket.readFully(buf, 0, 2);
        if (buf[0] != 5 || buf[1] != authMethod) {
            socket.close();
            throw new ConnectException("SocksProxy handshake error");
        }

        // Auth response
        if (authMethod != 0) {
            socket.readFully(buf, 0, 2);
            if (buf[1] != 0) {
                socket.close();
                throw new ConnectException("SocksProxy auth error");
            }
        }

        // Connect response
        socket.readFully(buf, 0, 10);
        if (buf[1] != 0) {
            socket.close();
            throw new ConnectException(getErrorMessage(buf[1]));
        }

        // Read remaining reply header for IPv6 address
        if (buf[3] == 4) {
            socket.readFully(buf, 10, 12);
        }
    }

    private static String getErrorMessage(byte b) {
        switch (b) {
            case 1:
                return "SocksProxy: general SOCKS server failure";
            case 2:
                return "SocksProxy: connection not allowed by ruleset";
            case 3:
                return "SocksProxy: Network unreachable";
            case 4:
                return "SocksProxy: Host unreachable";
            case 5:
                return "SocksProxy: Connection refused";
            case 6:
                return "SocksProxy: TTL expired";
            case 7:
                return "SocksProxy: Command not supported";
            case 8:
                return "SocksProxy: Address type not supported";
            default:
                return "SocksProxy connect error " + (b & 0xff);
        }
    }
}
