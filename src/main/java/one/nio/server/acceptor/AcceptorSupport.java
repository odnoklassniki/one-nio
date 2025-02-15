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

package one.nio.server.acceptor;

import java.io.IOException;

import one.nio.net.Socket;
import one.nio.net.SslContext;
import one.nio.server.AcceptorConfig;

public class AcceptorSupport {
    private AcceptorSupport() {}

    public static Socket createServerSocket(AcceptorConfig config) throws IOException {
        Socket serverSocket = Socket.createServerSocket();
        if (config.ssl != null) {
            SslContext sslContext = SslContext.create();
            sslContext.configure(config.ssl);
            serverSocket = serverSocket.sslWrap(sslContext);
        }
        if (config.recvBuf != 0) serverSocket.setRecvBuffer(config.recvBuf);
        if (config.sendBuf != 0) serverSocket.setSendBuffer(config.sendBuf);
        if (config.tos != 0) serverSocket.setTos(config.tos);
        if (config.notsentLowat != 0) serverSocket.setNotsentLowat(config.notsentLowat);
        if (config.deferAccept) serverSocket.setDeferAccept(true);

        serverSocket.setKeepAlive(config.keepAlive);
        serverSocket.setNoDelay(config.noDelay);
        serverSocket.setTcpFastOpen(config.tcpFastOpen);
        serverSocket.setReuseAddr(true, config.reusePort);
        serverSocket.setThinLinearTimeouts(config.thinLto);
        return serverSocket;
    }

    public static void reconfigureSocket(Socket socket, AcceptorConfig config) throws IOException {
        if (config.recvBuf != 0) socket.setRecvBuffer(config.recvBuf);
        if (config.sendBuf != 0) socket.setSendBuffer(config.sendBuf);
        if (config.tos != 0) socket.setTos(config.tos);
        if (config.notsentLowat != 0) socket.setNotsentLowat(config.notsentLowat);
        socket.setDeferAccept(config.deferAccept);
        socket.setKeepAlive(config.keepAlive);
        socket.setNoDelay(config.noDelay);
        socket.setTcpFastOpen(config.tcpFastOpen);
        socket.setReuseAddr(true, config.reusePort);
        socket.setThinLinearTimeouts(config.thinLto);

        SslContext sslContext = socket.getSslContext();
        if (sslContext != null && config.ssl != null) {
            sslContext.configure(config.ssl);
        }
    }
}