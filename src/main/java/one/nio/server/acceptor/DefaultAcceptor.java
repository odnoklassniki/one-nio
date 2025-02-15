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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.server.AcceptorConfig;
import one.nio.server.Server;

public class DefaultAcceptor implements Acceptor {
    private static final Logger log = LoggerFactory.getLogger(DefaultAcceptor.class);

    private final Server server;

    private volatile DefaultAcceptorGroup[] acceptorGroups;

    DefaultAcceptor(Server server, AcceptorConfig... configs) throws IOException {
        this.server = server;

        DefaultAcceptorGroup[] acceptorGroups = new DefaultAcceptorGroup[configs.length];
        for (int configIdx = 0; configIdx < configs.length; configIdx++) {
            AcceptorConfig config = configs[configIdx];
            acceptorGroups[configIdx] = new DefaultAcceptorGroup(server, config);
        }
        this.acceptorGroups = acceptorGroups;
    }

    @Override
    public void reconfigure(AcceptorConfig... configs) throws IOException {
        // Create a copy of the array, since the elements will be nulled out
        // to allow reconfiguring multiple acceptors with the same address:port
        DefaultAcceptorGroup[] oldAcceptorGroups = this.acceptorGroups.clone();
        DefaultAcceptorGroup[] newAcceptorGroups = new DefaultAcceptorGroup[configs.length];
        for (int configIdx = 0; configIdx < configs.length; configIdx++) {
            AcceptorConfig ac = configs[configIdx];
            DefaultAcceptorGroup oldGroup = configIdx < oldAcceptorGroups.length ? oldAcceptorGroups[configIdx] : null;
            if (oldGroup != null && oldGroup.isSameAddressPort(ac)) {
                log.info("Reconfiguring acceptor group: {}", oldGroup);
                oldGroup.reconfigure(ac);
                newAcceptorGroups[configIdx] = oldGroup;
                oldAcceptorGroups[configIdx] = null;
            } else {
                DefaultAcceptorGroup newGroup = new DefaultAcceptorGroup(server, ac);
                log.info("New acceptor group: {}", newGroup);
                newAcceptorGroups[configIdx] = newGroup;
                newGroup.start();
            }
        }

        for (DefaultAcceptorGroup oldGroup : oldAcceptorGroups) {
            if (oldGroup != null) {
                log.info("Stopping acceptor group: {}", oldGroup);
                oldGroup.shutdown();
            }
        }

        this.acceptorGroups = newAcceptorGroups;
    }

    @Override
    public void start() {
        for (DefaultAcceptorGroup acceptorGroup : this.acceptorGroups) {
            acceptorGroup.start();
        }
    }

    @Override
    public void shutdown() {
        for (DefaultAcceptorGroup acceptorGroup : this.acceptorGroups) {
            acceptorGroup.shutdown();
        }
    }

    @Override
    public void syncStart() throws InterruptedException {
        for (DefaultAcceptorGroup acceptorGroup : this.acceptorGroups) {
            acceptorGroup.syncStart();
        }
    }

    @Override
    public long getAcceptedSessions() {
        return Arrays.stream(this.acceptorGroups)
                .mapToLong(DefaultAcceptorGroup::getAcceptedSessions)
                .sum();
    }

    @Override
    public long getRejectedSessions() {
        return Arrays.stream(this.acceptorGroups)
                .mapToLong(DefaultAcceptorGroup::getRejectedSessions)
                .sum();
    }

    @Override
    public void resetCounters() {
        for (DefaultAcceptorGroup acceptorGroup : this.acceptorGroups) {
            acceptorGroup.resetCounters();
        }
    }

    @Override
    public int getSinglePort() {
        return acceptorGroups[0].getPort();
    }
}