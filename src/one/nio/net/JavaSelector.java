package one.nio.net;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

final class JavaSelector extends Selector {
    private final java.nio.channels.Selector impl;
    private final ConcurrentLinkedQueue<Session> pendingSessions;

    JavaSelector() throws IOException {
        this.impl = java.nio.channels.Selector.open();
        this.pendingSessions = new ConcurrentLinkedQueue<Session>();
    }

    @Override
    public final int size() {
        return impl.keys().size();
    }

    @Override
    public final void close() {
        try {
            impl.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public final void register(Session session) {
        session.selector = this;
        pendingSessions.add(session);
        impl.wakeup();
    }

    @Override
    public final void unregister(Session session) {
        ((JavaSocket) session.socket).ch.keyFor(impl).cancel();
    }

    @Override
    public final void listen(Session session, int events) {
        ((JavaSocket) session.socket).ch.keyFor(impl).interestOps(events);
        impl.wakeup();
    }

    @Override
    public final Iterator<Session> iterator() {
        return iteratorFor(impl.keys());
    }

    @Override
    public final Iterator<Session> select() {
        try {
            do {
                registerPendingSessions();
            } while (impl.select() == 0);
        } catch (Exception e) {
            return iteratorFor(Collections.<SelectionKey>emptySet());
        }

        Set<SelectionKey> selectedKeys = impl.selectedKeys();
        Iterator<Session> result = iteratorFor(selectedKeys);
        selectedKeys.clear();
        return result;
    }

    private void registerPendingSessions() throws ClosedChannelException {
        for (Session session; (session = pendingSessions.poll()) != null; ) {
            ((JavaSocket) session.socket).ch.register(impl, session.eventsToListen, session);
        }
    }

    private static Iterator<Session> iteratorFor(Set<SelectionKey> keys) {
        final Session[] sessions = new Session[keys.size() + 1];
        int i = 0;
        for (SelectionKey key : keys) {
            if (key.isValid()) {
                Session session = (Session) key.attachment();
                session.events = key.readyOps();
                sessions[i++] = session;
            }
        }

        return new Iterator<Session>() {
            private int next = 0;

            @Override
            public final boolean hasNext() {
                return sessions[next] != null;
            }

            @Override
            public final Session next() {
                return sessions[next++];
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
