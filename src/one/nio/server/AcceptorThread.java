/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.server;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.net.SslContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

final class AcceptorThread extends Thread {
    private static final Log log = LogFactory.getLog(AcceptorThread.class);

    final String address;
    final int port;
    final int backlog;
    final Server server;
    final Socket serverSocket;

    volatile long acceptedSessions;
    volatile long rejectedSessions;

    AcceptorThread(Server server, AcceptorConfig config) throws IOException {
        super("NIO Acceptor " + config.address + ":" + config.port);
        this.address = config.address;
        this.port = config.port;
        this.backlog = config.backlog;
        this.server = server;

        Socket serverSocket = Socket.createServerSocket();
        if (config.ssl != null) {
            SslContext sslContext = SslContext.create();
            sslContext.configure(config.ssl);
            serverSocket = serverSocket.sslWrap(sslContext);
        }
        this.serverSocket = serverSocket;

        if (config.recvBuf != 0) serverSocket.setRecvBuffer(config.recvBuf);
        if (config.sendBuf != 0) serverSocket.setSendBuffer(config.sendBuf);
        if (config.tos != 0) serverSocket.setTos(config.tos);
        if (config.deferAccept) serverSocket.setDeferAccept(true);

        serverSocket.setNoDelay(config.noDelay);
        serverSocket.setTcpFastOpen(config.tcpFastOpen);
        serverSocket.setReuseAddr(true, config.reusePort);
        serverSocket.bind(address, port, backlog);
    }

    void reconfigure(AcceptorConfig config) throws IOException {
        if (config.recvBuf != 0) {
            serverSocket.setRecvBuffer(config.recvBuf);
        }
        if (config.sendBuf != 0) {
            serverSocket.setSendBuffer(config.sendBuf);
        }
        if (config.tos != 0) {
            serverSocket.setTos(config.tos);
        }
        serverSocket.setDeferAccept(config.deferAccept);
        serverSocket.setNoDelay(config.noDelay);
        serverSocket.setTcpFastOpen(config.tcpFastOpen);
        serverSocket.setReuseAddr(true, config.reusePort);

        SslContext sslContext = serverSocket.getSslContext();
        if (sslContext != null && config.ssl != null) {
            sslContext.configure(config.ssl);
        }
    }

    void shutdown() {
        serverSocket.close();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    @Override
    public void run() {
        try {
            serverSocket.listen(backlog);
        } catch (IOException e) {
            log.error("Cannot start listening at " + port);
            return;
        }

        while (serverSocket.isOpen()) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                socket.setBlocking(false);
                Session session = server.createSession(socket);
                server.register(session);
                acceptedSessions++;
            } catch (RejectedSessionException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Rejected session from " + socket.getRemoteAddress(), e);
                }
                rejectedSessions++;
                socket.close();
            } catch (Throwable e) {
                if (serverSocket.isOpen()) {
                    log.error("Cannot accept incoming connection", e);
                }
                if (socket != null) socket.close();
            }
        }
    }
}
