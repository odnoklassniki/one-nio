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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import one.nio.server.AcceptorConfig;
import one.nio.server.Server;

class DefaultAcceptorGroup {
    private final Server server;
    private final String address;
    private final int port;

    private volatile AcceptorThread[] acceptors;

    CountDownLatch syncLatch;

    public DefaultAcceptorGroup(Server server, AcceptorConfig ac) throws IOException {
        this.server = server;
        this.address = ac.address;
        this.port = ac.port;

        AcceptorThread[] acceptors = new AcceptorThread[ac.threads];
        for (int threadId = 0; threadId < ac.threads; threadId++) {
            acceptors[threadId] = new AcceptorThread(server, ac, this, threadId);
        }
        this.acceptors = acceptors;
    }

    public void reconfigure(AcceptorConfig ac) throws IOException {
        if (!isSameAddressPort(ac)) {
            throw new IllegalArgumentException("Acceptor config has different address:port");
        }
        AcceptorThread[] oldAcceptors = this.acceptors;
        if (ac.threads < oldAcceptors.length) {
            for (int i = 0; i < oldAcceptors.length; i++) {
                if (i < ac.threads) {
                    oldAcceptors[i].reconfigure(ac);
                } else {
                    oldAcceptors[i].shutdown();
                }
            }
            this.acceptors = Arrays.copyOf(oldAcceptors, ac.threads);
        } else {
            AcceptorThread[] newAcceptors = Arrays.copyOf(oldAcceptors, ac.threads);
            for (int i = 0; i < newAcceptors.length; i++) {
                if (newAcceptors[i] != null) {
                    newAcceptors[i].reconfigure(ac);
                } else {
                    newAcceptors[i] = new AcceptorThread(server, ac, this, i);
                    newAcceptors[i].start();
                }
            }
            this.acceptors = newAcceptors;
        }
    }

    public boolean isSameAddressPort(AcceptorConfig ac) {
        return ac.address.equals(address) && ac.port == port;
    }

    public void start() {
        AcceptorThread[] acceptors = this.acceptors;
        this.syncLatch = new CountDownLatch(acceptors.length);
        for (AcceptorThread acceptor : acceptors) {
            acceptor.start();
        }
    }

    public void syncStart() throws InterruptedException {
        if (this.syncLatch != null) {
            this.syncLatch.await();
        }
    }

    public void shutdown() {
        for (AcceptorThread acceptor : acceptors) {
            acceptor.shutdown();
        }
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int size() {
        AcceptorThread[] acceptors = this.acceptors;
        return acceptors == null ? 0 : acceptors.length;
    }

    public long getAcceptedSessions() {
        long sum = 0;
        for (AcceptorThread acceptor : acceptors) {
            sum += acceptor.acceptedSessions;
        }
        return sum;
    }

    public long getRejectedSessions() {
        long sum = 0;
        for (AcceptorThread acceptor : acceptors) {
            sum += acceptor.rejectedSessions;
        }
        return sum;
    }

    public void resetCounters() {
        for (AcceptorThread acceptor : acceptors) {
            acceptor.acceptedSessions = 0;
            acceptor.rejectedSessions = 0;
        }
    }

    @Override
    public String toString() {
        return "DefaultAcceptorGroup{" +
                "address='" + address + '\'' +
                ", port=" + port +
                ", size=" + size() +
                '}';
    }
}