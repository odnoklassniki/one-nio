/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.mgt.Management;
import one.nio.net.SslContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class Server implements ServerMXBean {
    private static final Log log = LogFactory.getLog(Server.class);

    private final AtomicLong requestsProcessed;
    private final AtomicLong requestsRejected;

    private volatile SelectorStats selectorStats;
    private volatile QueueStats queueStats;

    protected ConnectionString conn;
    protected AcceptorThread[] acceptors;
    protected SelectorThread[] selectors;
    protected WorkerPool workers;
    protected CleanupThread cleanup;
    protected boolean useWorkers;

    public Server(ConnectionString conn) throws IOException {
        this.conn = conn;

        String[] hosts = conn.getHosts();
        int port = conn.getPort();
        SslContext sslContext = getSslContext(conn);
        int processors = Runtime.getRuntime().availableProcessors();

        int backlog = conn.getIntParam("backlog", 128);
        int buffers = conn.getIntParam("buffers", 0);
        int recvBuf = conn.getIntParam("recvBuf", buffers);
        int sendBuf = conn.getIntParam("sendBuf", buffers);
        boolean defer = conn.getBooleanParam("defer", false);
        boolean noDelay = conn.getBooleanParam("noDelay", true);
        int selectorCount = conn.getIntParam("selectors", processors);
        boolean affinity = conn.getBooleanParam("affinity", false);
        int minWorkers = conn.getIntParam("minWorkers", 0);
        int maxWorkers = conn.getIntParam("maxWorkers", 1000);
        long queueTime = conn.getLongParam("queueTime", 0);
        int keepAlive = conn.getIntParam("keepalive", 0);

        this.acceptors = new AcceptorThread[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            InetAddress address = InetAddress.getByName(hosts[i]);
            acceptors[i] = new AcceptorThread(this, address, port, sslContext, backlog, recvBuf, sendBuf, defer, noDelay);
        }

        this.selectors = new SelectorThread[selectorCount];
        for (int i = 0; i < selectorCount; i++) {
            this.selectors[i] = new SelectorThread(i, affinity ? 1L << (i % processors) : 0);
        }

        this.workers = new WorkerPool(minWorkers, maxWorkers, queueTime);
        this.useWorkers = conn.getStringParam("minWorkers") != null || conn.getStringParam("maxWorkers") != null;

        if (keepAlive > 0) {
            this.cleanup = new CleanupThread(this, keepAlive);
        }

        this.selectorStats = new SelectorStats();
        this.queueStats = new QueueStats();
        this.requestsProcessed = new AtomicLong();
        this.requestsRejected = new AtomicLong();

        if (conn.getBooleanParam("jmx", true)) {
            Management.registerMXBean(this, "one.nio.server:type=Server,port=" + port);
        }
    }

    public boolean reconfigure(ConnectionString conn) throws IOException {
        if (conn.getProtocol() == null) {
            if (this.conn.getProtocol() != null) return false;
        } else if (!conn.getProtocol().equals(this.conn.getProtocol())) {
            return false;
        }
        if (conn.getPort() != this.conn.getPort()) {
            return false;
        }

        this.conn = conn;

        workers.setCorePoolSize(conn.getIntParam("minWorkers", 0));
        workers.setMaximumPoolSize(conn.getIntParam("maxWorkers", 1000));
        workers.setQueueTime(conn.getLongParam("queueTime", 0));
        useWorkers = conn.getStringParam("minWorkers") != null || conn.getStringParam("maxWorkers") != null;

        int processors = Runtime.getRuntime().availableProcessors();
        int selectorCount = conn.getIntParam("selectors", processors);
        if (selectorCount > selectors.length) {
            boolean affinity = conn.getBooleanParam("affinity", false);
            SelectorThread[] newSelectors = Arrays.copyOf(selectors, selectorCount);
            for (int i = selectors.length; i < selectorCount; i++) {
                newSelectors[i] = new SelectorThread(i, affinity ? 1L << (i % processors) : 0);
                newSelectors[i].start();
            }
            selectors = newSelectors;
        }

        return true;
    }

    public void start() {
        for (SelectorThread selector : selectors) {
            selector.start();
        }
        for (AcceptorThread acceptor : acceptors) {
            acceptor.start();
        }
        if (cleanup != null) {
            cleanup.start();
        }
    }

    public void stop() {
        if (cleanup != null) {
            cleanup.shutdown();
            cleanup = null;
        }
        if (acceptors != null) {
            for (AcceptorThread acceptor : acceptors) {
                acceptor.shutdown();
            }
            acceptors = null;
        }
        if (selectors != null) {
            for (SelectorThread selector : selectors) {
                selector.shutdown();
            }
            selectors = null;
        }
        if (workers != null) {
            workers.gracefulShutdown(30000L);
            workers = null;
        }
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

    protected SslContext getSslContext(ConnectionString conn) {
        String protocol = conn.getProtocol();
        return "ssl".equals(protocol) || "https".equals(protocol) ? SslContext.getDefault() : null;
    }

    protected Session createSession(Socket socket) throws RejectedSessionException {
        return new Session(socket);
    }

    public final long incRequestsProcessed() {
        return requestsProcessed.incrementAndGet();
    }

    public final long incRequestsRejected() {
        return requestsRejected.incrementAndGet();
    }

    public final ConnectionString getConnectionString() {
        return conn;
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
    public void reset() {
        for (AcceptorThread acceptor : acceptors) {
            acceptor.acceptedSessions = 0;
            acceptor.rejectedSessions = 0;
        }
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
