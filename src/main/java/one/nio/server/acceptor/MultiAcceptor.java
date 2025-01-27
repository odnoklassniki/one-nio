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

class MultiAcceptor implements Acceptor {
    private static final Logger log = LoggerFactory.getLogger(MultiAcceptor.class);

    private final MultiAcceptorThread thread;

    private volatile MultiAcceptorGroup[] acceptorGroups;

    MultiAcceptor(Server server, AcceptorConfig... configs) throws IOException {
        this.thread = new MultiAcceptorThread(server);

        MultiAcceptorGroup[] newGroups = new MultiAcceptorGroup[configs.length];
        for (int configIdx = 0; configIdx < configs.length; configIdx++) {
            AcceptorConfig config = configs[configIdx];
            validateConfig(config);
            newGroups[configIdx] = new MultiAcceptorGroup(thread, config);
        }

        setAcceptorGroups(newGroups);
    }

    @Override
    public void reconfigure(AcceptorConfig... configs) throws IOException {
        MultiAcceptorGroup[] oldGroups = this.acceptorGroups.clone();
        MultiAcceptorGroup[] newGroups = new MultiAcceptorGroup[configs.length];
        for (int configIdx = 0; configIdx < configs.length; configIdx++) {
            AcceptorConfig newConfig = configs[configIdx];
            validateConfig(newConfig);
            MultiAcceptorGroup oldGroup = configIdx < oldGroups.length ? oldGroups[configIdx] : null;
            if (oldGroup != null && oldGroup.isSameAddressPort(newConfig)) {
                log.info("Reconfiguring acceptor group: {}", oldGroup);
                oldGroup.reconfigure(newConfig);
                newGroups[configIdx] = oldGroup;
                oldGroups[configIdx] = null;
            } else {
                MultiAcceptorGroup newGroup = new MultiAcceptorGroup(thread, newConfig);
                log.info("New acceptor group: {}", newGroup);
                newGroups[configIdx] = newGroup;
                newGroup.start();
            }
        }

        for (MultiAcceptorGroup oldGroup : oldGroups) {
            if (oldGroup != null) {
                oldGroup.close();
            }
        }

        setAcceptorGroups(newGroups);
    }

    @Override
    public void start() {
        thread.start();
        for (MultiAcceptorGroup group : acceptorGroups) {
            try {
                group.start();
            } catch (IOException e) {
                log.error("Cannot start listening at {}", group, e);
            }
        }
    }

    @Override
    public void syncStart() {
        // not needed, this is a single thread
    }

    @Override
    public void shutdown() {
        thread.shutdown();
    }

    @Override
    public long getAcceptedSessions() {
        return thread.acceptedSessions;
    }

    @Override
    public long getRejectedSessions() {
        return thread.rejectedSessions;
    }

    @Override
    public void resetCounters() {
        thread.acceptedSessions = 0;
        thread.rejectedSessions = 0;
    }

    @Override
    public int getSinglePort() {
        return acceptorGroups[0].getPort();
    }

    private void validateConfig(AcceptorConfig newConfig) {
        if (newConfig.threads <= 0) {
            throw new IllegalArgumentException("Cannot create acceptor with 0 ports");
        }
        if (newConfig.threads > 1 && !newConfig.reusePort) {
            throw new IllegalArgumentException("Cannot create multiport acceptor without reusePort");
        }
    }

    private void setAcceptorGroups(MultiAcceptorGroup[] newGroups) {
        this.acceptorGroups = newGroups;
        thread.setName("NIO MultiAcceptor " + Arrays.toString(newGroups));
    }
}