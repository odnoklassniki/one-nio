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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.mgt.Management;
import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.acceptor.Acceptor;
import one.nio.server.acceptor.AcceptorFactory;

public class Server implements ServerMXBean {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final LongAdder requestsProcessed = new LongAdder();
    private final LongAdder requestsRejected = new LongAdder();

    private volatile SelectorStats selectorStats;
    private volatile QueueStats queueStats;

    protected final int port;

    protected volatile Acceptor acceptor;

    protected volatile SelectorThread[] selectors;
    protected boolean useWorkers;
    protected final WorkerPool workers;
    protected final CleanupThread cleanup;
    protected boolean closeSessions;
    protected boolean pinAcceptors;

    public Server(ServerConfig config) throws IOException {
        this.acceptor = AcceptorFactory.get(config).create(this, config.acceptors);
        this.port = acceptor.getSinglePort();

        int processors = Runtime.getRuntime().availableProcessors();
        SelectorThread[] selectors = new SelectorThread[config.selectors != 0 ? config.selectors : processors];
        for (int i = 0; i < selectors.length; i++) {
            String threadName = config.formatSelectorThreadName(i);
            selectors[i] = new SelectorThread(i, config.affinity ? i % processors : -1, config.schedulingPolicy, threadName);
            selectors[i].setPriority(config.threadPriority);
        }
        this.selectors = selectors;

        this.useWorkers = config.maxWorkers > 0;
        this.workers = new WorkerPool(config.minWorkers, useWorkers ? config.maxWorkers : 2, config.queueTime,
                config.threadPriority, config.schedulingPolicy);

        this.cleanup = new CleanupThread(selectors, config.keepAlive);

        this.closeSessions = config.closeSessions;
        this.pinAcceptors = config.pinAcceptors;

        this.selectorStats = new SelectorStats();
        this.queueStats = new QueueStats();
    }

    public synchronized void reconfigure(ServerConfig config) throws IOException {
        useWorkers = config.maxWorkers > 0;
        if (config.minWorkers > workers.getMaximumPoolSize()) {
            workers.setMaximumPoolSize(useWorkers ? config.maxWorkers : 2);
            workers.setCorePoolSize(config.minWorkers);
        } else {
            workers.setCorePoolSize(config.minWorkers);
            workers.setMaximumPoolSize(useWorkers ? config.maxWorkers : 2);
        }
        workers.setQueueTime(config.queueTime);

        acceptor.reconfigure(config.acceptors);

        int processors = Runtime.getRuntime().availableProcessors();
        SelectorThread[] selectors = this.selectors;
        if (config.selectors > selectors.length) {
            SelectorThread[] newSelectors = Arrays.copyOf(selectors, config.selectors);
            for (int i = selectors.length; i < config.selectors; i++) {
                String threadName = config.formatSelectorThreadName(i);
                newSelectors[i] = new SelectorThread(i, config.affinity ? i % processors : -1, config.schedulingPolicy, threadName);
                newSelectors[i].setPriority(config.threadPriority);
                newSelectors[i].start();
            }
            this.selectors = newSelectors;
        }

        cleanup.update(this.selectors, config.keepAlive);
        closeSessions = config.closeSessions;
        pinAcceptors = config.pinAcceptors;
    }

    public synchronized void start() {
        for (SelectorThread selector : selectors) {
            selector.start();
        }

        acceptor.start();

        try {
            acceptor.syncStart();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cleanup.start();

        Management.registerMXBean(this, "one.nio.server:type=Server,port=" + port);
    }

    public synchronized void stop() {
        Management.unregisterMXBean("one.nio.server:type=Server,port=" + port);

        cleanup.shutdown();

        acceptor.shutdown();

        for (SelectorThread selector : selectors) {
            if (closeSessions) {
                for (Session session : selector.selector) {
                    session.close();
                }
            }
            selector.shutdown();
        }

        workers.gracefulShutdown();
    }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread("Server Shutdown") {
            @Override
            public void run() {
                log.info("Server is shutting down...");
                Server.this.stop();
                log.info("Server stopped");
            }
        });
    }

    public Session createSession(Socket socket) throws RejectedSessionException {
        return new Session(socket);
    }

    public void register(Session session, int acceptorIndex, int acceptorGroupSize) {
        if (pinAcceptors) {
            getSmallestPinnedSelector(acceptorIndex, acceptorGroupSize).register(session);
            return;
        }
        register(session);
    }

    public void register(Session session) {
        getSmallestSelector().register(session);
    }

    /*
     * If `pinAcceptors` is enabled for the server,  accepted sessions are distributed across the disjunctive set of selectors.
     * When server is configured to have  less `acceptors`(K) less than `selectors`(N), the selectors group for the given acceptor
     * by its indices forms an finite arithmetic sequence starting from the acceptor index with step K.
     * Example: ServerConfig.acceptors = 3, ServerConfig.selectors = 8.
     *   Acceptor #0 -> Selectors #0, #3, #6
     *   Acceptor #1 -> Selectors #1, #4, #7
     *   Acceptor #2 -> Selectors #2, #5
     * Across the selectors' subset, the selector to serve the session is chosen on a random basis.
     * Provided the server is configured to have more `acceptors`(K) than `selectors`(N), the serving selector index is calculated out of acceptor index modulo N.
     * Example: ServerConfig.acceptors = 8, ServerConfig.selectors = 3.
     *   Acceptor #0 -> Selector #0
     *   Acceptor #1 -> Selector #1
     *   Acceptor #2 -> Selector #2
     *   Acceptor #3 -> Selector #0
     *   ...
     *   Acceptor #7 -> Selector #1
     * Base configuration 1: acceptors = 1, selectors = N. The single acceptor balances sessions across all N selectors randomly.
     * Base configuration 2: acceptors = N, selectors = N. Each acceptor has a single designated selector to serve the sessions.
     */
    private Selector getSmallestPinnedSelector(int idx, int total) {
        Selector chosen;
        SelectorThread[] selectors = this.selectors;
        if (total >= selectors.length) {
            chosen = selectors[idx % selectors.length].selector;
        } else {
            int q = selectors.length / total;
            if (q * total + idx < selectors.length) {
                q++;
            }
            if (q == 1) {
                chosen = selectors[idx].selector;
            } else {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Selector a = selectors[r.nextInt(q) * total + idx].selector;
                Selector b = selectors[r.nextInt(q) * total + idx].selector;
                chosen = a.size() < b.size() ? a : b;
            }
        }
        return chosen;
    }

    private Selector getSmallestSelector() {
        SelectorThread[] selectors = this.selectors;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        Selector a = selectors[r.nextInt(selectors.length)].selector;
        Selector b = selectors[r.nextInt(selectors.length)].selector;
        return a.size() < b.size() ? a : b;
    }

    public final void incRequestsProcessed() {
        requestsProcessed.increment();
    }

    public final void incRequestsRejected() {
        requestsRejected.increment();
    }

    @Override
    public int getConnections() {
        int result = 0;
        for (SelectorThread selector : selectors) {
            result += selector.selector.size();
        }
        return result;
    }

    @Override
    public long getKeepAlive() {
        return cleanup.getKeepAlive();
    }

    @Override
    public boolean getWorkersUsed() {
        return useWorkers;
    }

    @Override
    public int getWorkers() {
        return workers.getPoolSize();
    }

    @Override
    public int getWorkersActive() {
        return workers.getActiveCount();
    }

    @Override
    public long getAcceptedSessions() {
        return acceptor.getAcceptedSessions();
    }

    @Override
    public long getRejectedSessions() {
        return acceptor.getRejectedSessions();
    }

    @Override
    public int getSelectorCount() {
        return selectors.length;
    }

    @Override
    public double getSelectorAvgReady() {
        SelectorStats selectorStats = getSelectorStats();
        return selectorStats.operations == 0 ? 0.0 : (double) selectorStats.sessions / selectorStats.operations;
    }

    @Override
    public int getSelectorMaxReady() {
        return getSelectorStats().maxReady;
    }

    @Override
    public long getSelectorOperations() {
        return getSelectorStats().operations;
    }

    @Override
    public long getSelectorSessions() {
        return getSelectorStats().sessions;
    }

    @Override
    public double getQueueAvgLength() {
        QueueStats queueStats = getQueueStats();
        return queueStats.sessions == 0 ? 0.0 : (double) queueStats.totalLength / queueStats.sessions;
    }

    @Override
    public long getQueueAvgBytes() {
        QueueStats queueStats = getQueueStats();
        return queueStats.sessions == 0 ? 0 : queueStats.totalBytes / queueStats.sessions;
    }

    @Override
    public long getQueueMaxLength() {
        return getQueueStats().maxLength;
    }

    @Override
    public long getQueueMaxBytes() {
        return getQueueStats().maxBytes;
    }

    @Override
    public long getRequestsProcessed() {
        return requestsProcessed.sum();
    }

    @Override
    public long getRequestsRejected() {
        return requestsRejected.sum();
    }

    @Override
    public synchronized void reset() {
        acceptor.resetCounters();

        for (SelectorThread selector : selectors) {
            selector.operations = 0;
            selector.sessions = 0;
            selector.maxReady = 0;
        }

        requestsProcessed.reset();
        requestsRejected.reset();
    }

    public final void asyncExecute(Runnable command) {
        workers.execute(command);
    }

    private static final class SelectorStats {
        long expireTime;
        long operations;
        long sessions;
        int maxReady;
    }

    private synchronized SelectorStats getSelectorStats() {
        SelectorStats selectorStats = this.selectorStats;

        long currentTime = System.currentTimeMillis();
        if (currentTime < selectorStats.expireTime) {
            return selectorStats;
        }

        selectorStats = new SelectorStats();
        selectorStats.expireTime = currentTime + 1000;

        for (SelectorThread selector : selectors) {
            selectorStats.operations += selector.operations;
            selectorStats.sessions += selector.sessions;
            selectorStats.maxReady = Math.max(selectorStats.maxReady, selector.maxReady);
        }

        this.selectorStats = selectorStats;
        return selectorStats;
    }

    private static final class QueueStats {
        long expireTime;
        long totalLength;
        long totalBytes;
        long maxLength;
        long maxBytes;
        int sessions;
    }

    private synchronized QueueStats getQueueStats() {
        QueueStats queueStats = this.queueStats;

        long currentTime = System.currentTimeMillis();
        if (currentTime < queueStats.expireTime) {
            return queueStats;
        }

        queueStats = new QueueStats();
        queueStats.expireTime = currentTime + 1000;

        long[] stats = new long[2];
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.getQueueStats(stats);
                queueStats.sessions++;
                queueStats.totalLength += stats[0];
                queueStats.totalBytes += stats[1];
                if (stats[0] > queueStats.maxLength) queueStats.maxLength = stats[0];
                if (stats[1] > queueStats.maxBytes) queueStats.maxBytes = stats[1];
            }
        }

        this.queueStats = queueStats;
        return queueStats;
    }
}