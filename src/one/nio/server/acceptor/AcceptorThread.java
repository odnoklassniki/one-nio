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

package one.nio.server.acceptor;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.RejectedSessionException;
import one.nio.server.Server;

final class AcceptorThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(AcceptorThread.class);

    final DefaultAcceptorGroup group;
    final int num;
    final String address;
    final int port;
    final int backlog;
    final Server server;
    final Socket serverSocket;

    volatile long acceptedSessions;
    volatile long rejectedSessions;

    AcceptorThread(Server server, AcceptorConfig config, DefaultAcceptorGroup group, int num) throws IOException {
        super("NIO Acceptor " + config.address + ":" + config.port + " #" + num);
        this.group = group;
        this.num = num;
        this.address = config.address;
        this.port = config.port;
        this.backlog = config.backlog;
        this.server = server;

        Socket serverSocket = AcceptorSupport.createServerSocket(config);
        serverSocket.bind(address, port, backlog);
        this.serverSocket = serverSocket;
    }

    void reconfigure(AcceptorConfig config) throws IOException {
        AcceptorSupport.reconfigureSocket(serverSocket, config);
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
            log.error("Cannot start listening at {}", port, e);
            return;
        } finally {
            group.syncLatch.countDown();
        }

        while (serverSocket.isOpen()) {
            Socket socket = null;
            try {
                socket = serverSocket.acceptNonBlocking();
                Session session = server.createSession(socket);
                server.register(session, num, group.size());
                acceptedSessions++;
            } catch (RejectedSessionException e) {
                log.debug("Rejected session from {}", socket.getRemoteAddress(), e);
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
