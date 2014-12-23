package one.nio.server;

import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.os.Proc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.Iterator;

final class SelectorThread extends Thread {
    private static final Log log = LogFactory.getLog(SelectorThread.class);
    private static final int BUFFER_SIZE = 64000;

    final Server server;
    final Selector selector;
    final long affinity;

    long operations;
    long sessions;
    int maxReady;

    SelectorThread(Server server, int num, long affinity) throws IOException {
        super("NIO Selector #" + num);
        setUncaughtExceptionHandler(server);
        this.server = server;
        this.selector = Selector.create();
        this.affinity = affinity;
    }

    void shutdown() {
        selector.close();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    @Override
    public void run() {
        if (affinity != 0 && Proc.IS_SUPPORTED) {
            Proc.sched_setaffinity(0, affinity);
        }

        final byte[] buffer = new byte[BUFFER_SIZE];

        while (server.isRunning()) {
            int ready = 0;
            for (Iterator<Session> selectedSessions = selector.select(); selectedSessions.hasNext(); ready++) {
                Session session = selectedSessions.next();
                try {
                    session.process(buffer);
                } catch (SocketException e) {
                    if (server.isRunning() && log.isDebugEnabled()) {
                        log.debug("Connection closed: " + session.getRemoteHost());
                    }
                    session.close();
                } catch (Throwable e) {
                    if (server.isRunning()) {
                        log.error("Cannot process session from " + session.getRemoteHost(), e);
                    }
                    session.close();
                }
            }

            operations++;
            sessions += ready;
            if (ready > maxReady) {
                maxReady = ready;
            }
        }
    }
}
