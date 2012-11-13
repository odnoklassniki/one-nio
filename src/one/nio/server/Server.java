package one.nio.server;

import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.InetAddress;

public class Server implements Thread.UncaughtExceptionHandler {
    private static final Log log = LogFactory.getLog(Server.class);

    private volatile boolean running;

    protected AcceptorThread acceptor;
    protected SelectorThread[] selectors;
    protected WorkerPool workers;
    protected CleanupThread cleanup;

    public Server(ConnectionString conn) throws IOException {
        InetAddress address = InetAddress.getByName(conn.getHost());
        int port = conn.getPort();
        int backlog = conn.getIntParam("backlog", 128);
        int buffers = conn.getIntParam("buffers", 0);
        int selectorCount = conn.getIntParam("selectors", Runtime.getRuntime().availableProcessors());
        int minWorkers = conn.getIntParam("minWorkers", 16);
        int maxWorkers = conn.getIntParam("maxWorkers", 1000);
        long queueTime = conn.getLongParam("queueTime", 0);
        int keepAlive = conn.getIntParam("keepalive", 0);

        this.acceptor = new AcceptorThread(this, address, port, backlog, buffers);
        this.selectors = new SelectorThread[selectorCount];
        this.workers = new WorkerPool(this, minWorkers, maxWorkers, queueTime);

        for (int i = 0; i < selectorCount; i++) {
            this.selectors[i] = new SelectorThread(this, i);
        }

        if (keepAlive > 0) {
            this.cleanup = new CleanupThread(this, keepAlive);
        }
    }

    public boolean reconfigure(ConnectionString conn) {
        if (acceptor.port != conn.getPort()) {
            return false;
        }

        workers.setCorePoolSize(conn.getIntParam("minWorkers", 16));
        workers.setMaximumPoolSize(conn.getIntParam("maxWorkers", 1000));
        workers.setQueueTime(conn.getLongParam("queueTime", 0));
        return true;
    }

    public void start() {
        running = true;
        for (SelectorThread selector : selectors) {
            selector.start();
        }
        acceptor.start();
        if (cleanup != null) {
            cleanup.start();
        }
    }

    public void stop() {
        running = false;
        if (cleanup != null) {
            cleanup.shutdown();
            cleanup = null;
        }
        if (acceptor != null) {
            acceptor.shutdown();
            acceptor = null;
        }
        if (selectors != null) {
            for (SelectorThread selector : selectors) {
                selector.shutdown();
            }
            selectors = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    public Session createSession(Socket socket) {
        return new Session(socket);
    }

    public final boolean isRunning() {
        return running;
    }

    public final int getPoolSize() {
        return workers.getPoolSize();
    }

    public final int getActiveCount() {
        return workers.getActiveCount();
    }

    public final int getConnections() {
        int result = 0;
        for (SelectorThread selector : selectors) {
            result += selector.selector.size();
        }
        return result;
    }

    public final void asyncExecute(Runnable command) {
        workers.execute(command);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.fatal("Fatal error in " + t, e);
    }
}
