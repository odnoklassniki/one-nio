package one.nio.server;

import one.nio.net.Selector;
import one.nio.net.Session;

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

    SelectorThread(Server server, int num) throws IOException {
        super("NIO Selector #" + num);
        setUncaughtExceptionHandler(server);
        this.server = server;
        this.selector = Selector.create();
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
        final byte[] buffer = new byte[BUFFER_SIZE];

        while (server.isRunning()) {
            for (Iterator<Session> selectedSessions = selector.select(); selectedSessions.hasNext(); ) {
                Session session = selectedSessions.next();
                try {
                    session.process(buffer);
                } catch (SocketException e) {
                    if (server.isRunning() && log.isDebugEnabled()) {
                        log.debug("Connection closed: " + session.clientIp());
                    }
                    session.close();
                } catch (Exception e) {
                    if (server.isRunning()) {
                        log.error("Cannot process session", e);
                    }
                    session.close();
                }
            }
        }
    }
}
