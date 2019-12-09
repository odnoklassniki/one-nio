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

import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.mgt.Management;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class Server implements ServerMXBean {
    private static final Log log = LogFactory.getLog(Server.class);

    private final AtomicLong requestsProcessed = new AtomicLong();
    private final AtomicLong requestsRejected = new AtomicLong();

    private volatile SelectorStats selectorStats;
    private volatile QueueStats queueStats;

    protected final int port;
    protected final AcceptorThread[] acceptors;
    protected final CountDownLatch startSync;
    protected volatile SelectorThread[] selectors;
    protected final WorkerPool workers;
    protected boolean useWorkers;
    protected final CleanupThread cleanup;

    public Server(ServerConfig config) throws IOException {
        if (config.acceptors == null || config.acceptors.length == 0) {
            throw new IllegalArgumentException("No configured acceptors");
        }

        this.port = config.acceptors[0].port;
        this.acceptors = new AcceptorThread[config.acceptors.length];
        for (int i = 0; i < acceptors.length; i++) {
            acceptors[i] = new AcceptorThread(this, config.acceptors[i]);
        }
        this.startSync = new CountDownLatch(acceptors.length);

        int processors = Runtime.getRuntime().availableProcessors();
        SelectorThread[] selectors = new SelectorThread[config.selectors != 0 ? config.selectors : processors];
        for (int i = 0; i < selectors.length; i++) {
            selectors[i] = new SelectorThread(i, config.affinity ? i % processors : -1);
            selectors[i].setPriority(config.threadPriority);
        }
        this.selectors = selectors;

        this.useWorkers = config.maxWorkers > 0;
        this.workers = new WorkerPool(config.minWorkers, useWorkers ? config.maxWorkers : 2, config.queueTime, config.threadPriority);

        this.cleanup = new CleanupThread(selectors, config.keepAlive);

        this.selectorStats = new SelectorStats();
        this.queueStats = new QueueStats();
    }

    public synchronized void reconfigure(ServerConfig config) throws IOException {
        workers.setCorePoolSize(config.minWorkers);

        useWorkers = config.maxWorkers > 0;
        workers.setMaximumPoolSize(useWorkers ? config.maxWorkers : 2);

        workers.setQueueTime(config.queueTime);

        for (AcceptorConfig ac : config.acceptors) {
            for (AcceptorThread acceptor : acceptors) {
                if (acceptor.port == ac.port && acceptor.address.equals(ac.address)) {
                    acceptor.reconfigure(ac);
                    break;
                }
            }
        }

        int processors = Runtime.getRuntime().availableProcessors();
        SelectorThread[] selectors = this.selectors;
        if (config.selectors > selectors.length) {
            SelectorThread[] newSelectors = Arrays.copyOf(selectors, config.selectors);
            for (int i = selectors.length; i < config.selectors; i++) {
                newSelectors[i] = new SelectorThread(i, config.affinity ? i % processors : -1);
                newSelectors[i].setPriority(config.threadPriority);
                newSelectors[i].start();
            }
            this.selectors = newSelectors;
        }

        cleanup.update(this.selectors, config.keepAlive);
    }

    public synchronized void start() {
        SelectorThread[] selectors = this.selectors;
        for (SelectorThread selector : selectors) {
            selector.start();
        }

        for (AcceptorThread acceptor : this.acceptors) {
            acceptor.start();
        }

        // Wait until all AcceptorThreads are listening for incoming connections
        try {
            startSync.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cleanup.start();

        Management.registerMXBean(this, "one.nio.server:type=Server,port=" + port);
    }

    public synchronized void stop() {
        Management.unregisterMXBean("one.nio.server:type=Server,port=" + port);

        cleanup.shutdown();

        for (AcceptorThread acceptor : this.acceptors) {
            acceptor.shutdown();
        }

        SelectorThread[] selectors = this.selectors;
        for (SelectorThread selector : selectors) {
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

    protected Session createSession(Socket socket) throws RejectedSessionException {
        return new Session(socket);
    }

    protected void register(Session session) {
        getSmallestSelector().register(session);
    }

    private Selector getSmallestSelector() {
        SelectorThread[] selectors = this.selectors;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        Selector a = selectors[r.nextInt(selectors.length)].selector;
        Selector b = selectors[r.nextInt(selectors.length)].selector;
        return a.size() < b.size() ? a : b;
    }

    public final long incRequestsProcessed() {
        return requestsProcessed.incrementAndGet();
    }

    public final long incRequestsRejected() {
        return requestsRejected.incrementAndGet();
    }

    @Override
    public int getConnections() {
        int result = 0;
        SelectorThread[] selectors = this.selectors;
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
        long result = 0;
        for (AcceptorThread acceptor : acceptors) {
            result += acceptor.acceptedSessions;
        }
        return result;
    }

    @Override
    public long getRejectedSessions() {
        long result = 0;
        for (AcceptorThread acceptor : acceptors) {
            result += acceptor.rejectedSessions;
        }
        return result;
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
        return requestsProcessed.get();
    }

    @Override
    public long getRequestsRejected() {
        return requestsRejected.get();
    }

    @Override
    public synchronized void reset() {
        for (AcceptorThread acceptor : acceptors) {
            acceptor.acceptedSessions = 0;
            acceptor.rejectedSessions = 0;
        }

        SelectorThread[] selectors = this.selectors;
        for (SelectorThread selector : selectors) {
            selector.operations = 0;
            selector.sessions = 0;
            selector.maxReady = 0;
        }

        requestsProcessed.set(0);
        requestsRejected.set(0);
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

        SelectorThread[] selectors = this.selectors;
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