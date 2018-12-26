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

import one.nio.util.Base64;
import one.nio.util.ByteArrayBuilder;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HttpProxy implements Proxy {
    public static final int[] ALL_PORTS = null;

    private final String proxyHost;
    private final int proxyPort;
    private byte[] authHeader;
    private int[] connectPorts = {443};

    public HttpProxy(String proxyHost, int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public HttpProxy withAuth(String user, String password) {
        if (user == null || password == null) {
            this.authHeader = null;
        } else {
            this.authHeader = new ByteArrayBuilder()
                    .append("Proxy-Authorization: Basic ")
                    .append(Base64.encode((user + ':' + password).getBytes(StandardCharsets.UTF_8)))
                    .append('\r').append('\n')
                    .toBytes();
        }
        return this;
    }

    public HttpProxy withConnectPorts(int... connectPorts) {
        if (connectPorts != null) {
            connectPorts = connectPorts.clone();
            Arrays.sort(connectPorts);
        }
        this.connectPorts = connectPorts;
        return this;
    }

    @Override
    public void connect(Socket socket, String host, int port) throws IOException {
        socket.connect(proxyHost, proxyPort);

        // Do not use CONNECT command for non-HTTPS ports
        if (connectPorts != null && Arrays.binarySearch(connectPorts, port) < 0) {
            return;
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(400)
                .append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append(':').append(port).append('\r').append('\n');
        if (authHeader != null) {
            builder.append(authHeader);
        }
        builder.append('\r').append('\n');
        socket.writeFully(builder.buffer(), 0, builder.length());

        int statusCode = readResponse(socket, builder.buffer());
        if (statusCode != 200) {
            socket.close();
            throw new ConnectException("HttpProxy error " + statusCode);
        }
    }

    private int readResponse(Socket socket, byte[] buf) throws IOException {
        int pos = 0;
        do {
            pos += socket.read(buf, pos, buf.length - pos);
            if (pos == buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
        }
        while (!(pos >= 16 && buf[pos - 4] == '\r' && buf[pos - 3] == '\n' && buf[pos - 2] == '\r' && buf[pos - 1] == '\n'));

        // Extract "200" from "HTTP/1.1 200 OK"
        return buf[9] * 100 + buf[10] * 10 + buf[11] - ('0' * 111);
    }
}
