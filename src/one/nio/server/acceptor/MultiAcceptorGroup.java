/*
 * Copyright 2024 LLC VK
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
import java.util.Arrays;

import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;

public class MultiAcceptorGroup {
    private final MultiAcceptorThread thread;
    private final String address;
    private final int port;

    private volatile MultiAcceptSession[] sessions;

    MultiAcceptorGroup(MultiAcceptorThread thread, AcceptorConfig config) throws IOException {
        this.thread = thread;
        this.address = config.address;
        this.port = config.port;

        MultiAcceptSession[] sessions = new MultiAcceptSession[config.threads];
        for (int sessionIdx = 0; sessionIdx < config.threads; sessionIdx++) {
            sessions[sessionIdx] = createMultiAcceptSession(config, sessionIdx);
        }
        this.sessions = sessions;
    }

    boolean isSameAddressPort(AcceptorConfig config) {
        return this.address.equals(config.address) && this.port == config.port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    void start() throws IOException {
        for (MultiAcceptSession session : sessions) {
            thread.register(session);
        }
    }

    void close() {
        for (MultiAcceptSession session : sessions) {
            session.close();
        }
    }

    int size() {
        MultiAcceptSession[] sessions = this.sessions;
        return sessions == null ? 0 : sessions.length;
    }

    void reconfigure(AcceptorConfig newConfig) throws IOException {
        if (!isSameAddressPort(newConfig)) {
            throw new IllegalArgumentException("Acceptor config has different address:port");
        }
        MultiAcceptSession[] oldSessions = this.sessions;
        MultiAcceptSession[] newSessions = Arrays.copyOf(oldSessions, newConfig.threads);
        if (oldSessions.length > newConfig.threads) {
            for (int sessionIdx = 0; sessionIdx < oldSessions.length; sessionIdx++) {
                if (sessionIdx < newSessions.length) {
                    oldSessions[sessionIdx].reconfigure(newConfig);
                } else {
                    oldSessions[sessionIdx].close();
                }
            }
        } else {
            for (int sessionIdx = 0; sessionIdx < newSessions.length; sessionIdx++) {
                MultiAcceptSession session = newSessions[sessionIdx];
                if (session != null) {
                    session.reconfigure(newConfig);
                } else {
                    MultiAcceptSession acceptSession = createMultiAcceptSession(newConfig, sessionIdx);
                    thread.register(acceptSession);
                }
            }
        }
        this.sessions = newSessions;
    }

    @Override
    public String toString() {
        return address + ':' + port + 'x' + size();
    }

    private MultiAcceptSession createMultiAcceptSession(AcceptorConfig config, int sessionIdx) throws IOException {
        Socket serverSocket = AcceptorSupport.createServerSocket(config);
        serverSocket.setBlocking(false);
        serverSocket.bind(config.address, config.port, config.backlog);
        return new MultiAcceptSession(serverSocket, config.backlog, this, sessionIdx);
    }
}