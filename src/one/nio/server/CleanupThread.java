package one.nio.server;

import one.nio.net.Session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

final class CleanupThread extends Thread {
    private static final Log log = LogFactory.getLog(CleanupThread.class);

    final Server server;
    final long keepAlive;

    CleanupThread(Server server, int keepAlive) {
        super("NIO Cleanup");
        setUncaughtExceptionHandler(server);
        this.server = server;
        this.keepAlive = keepAlive * 1000L;
    }

    void shutdown() {
        interrupt();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    @Override
    public void run() {
        for (;;) {
            try {
                Thread.sleep(keepAlive / 2);
            } catch (InterruptedException e) {
                break;
            }

            SelectorThread[] selectors = server.selectors;
            if (!server.isRunning()) {
                break;
            }

            long cleanTime = System.currentTimeMillis();
            long idle = cleanTime - keepAlive;
            long timeout = cleanTime - keepAlive * 8;
            int idleCount = 0;
            int timeoutCount = 0;

            for (SelectorThread selector : selectors) {
                for (Session session : selector.selector) {
                    long lastAccessTime = session.lastAccessTime();
                    if (lastAccessTime > 0 && lastAccessTime < idle) {
                        if (!session.isActive()) {
                            session.close();
                            idleCount++;
                        } else if (lastAccessTime < timeout) {
                            session.close();
                            timeoutCount++;
                        }
                    }
                }
            }

            if (log.isInfoEnabled()) {
                log.info(idleCount + " idle + " + timeoutCount + " timed out sessions closed in " +
                        (System.currentTimeMillis() - cleanTime) + " ms");
            }
        }
    }
}
