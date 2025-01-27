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
import java.util.Iterator;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.Server;

class MultiAcceptorThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(MultiAcceptorThread.class);

    private static final int MAX_ACCEPTED_PER_SOCKET = 128;

    private final Server server;
    private final Selector selector;

    volatile long acceptedSessions;
    volatile long rejectedSessions;

    MultiAcceptorThread(Server server) throws IOException {
        this.server = server;
        this.selector = Selector.create();
    }

    void register(MultiAcceptSession session) throws IOException {
        session.listen(selector);
    }

    @Override
    public void run() {
        Socket clientSocket = null;
        try {
            while (!Thread.currentThread().isInterrupted() && selector.isOpen()) {
                Iterator<Session> it = selector.select();
                while (it.hasNext()) {
                    MultiAcceptSession as = (MultiAcceptSession) it.next();
                    int accepted = 0;
                    while (accepted < MAX_ACCEPTED_PER_SOCKET && (clientSocket = as.socket().acceptNonBlocking()) != null) {
                        try {
                            Session clientSession = server.createSession(clientSocket);
                            server.register(clientSession, as.idx, as.group.size());
                            clientSocket = null;
                            acceptedSessions++;
                            accepted++;
                        } catch (RejectedExecutionException e) {
                            log.debug("Rejected session from {}", clientSocket.getRemoteAddress(), e);
                            rejectedSessions++;
                            clientSocket.close();
                            clientSocket = null;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (selector.isOpen()) {
                log.error("Cannot accept incoming connection", t);
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    void shutdown() {
        selector.close();
        interrupt();
        try {
            join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}