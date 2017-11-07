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

package one.nio.net;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

final class JavaSelector extends Selector {
    private static final Log log = LogFactory.getLog(JavaSelector.class);

    private final java.nio.channels.Selector impl;
    private final ConcurrentLinkedQueue<Session> pendingSessions;
    private long lastWakeupTime;

    JavaSelector() throws IOException {
        this.impl = java.nio.channels.Selector.open();
        this.pendingSessions = new ConcurrentLinkedQueue<>();
    }

    @Override
    public final int size() {
        return impl.keys().size();
    }

    @Override
    public boolean isOpen() {
        return impl.isOpen();
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
        SelectionKey key = ((SelectableJavaSocket) session.socket).getSelectableChannel().keyFor(impl);
        if (key != null) {
            key.cancel();
        }
        session.selector = null;
    }

    @Override
    public final void listen(Session session, int events) {
        ((SelectableJavaSocket) session.socket).getSelectableChannel().keyFor(impl).interestOps(events);
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
        } catch (ClosedSelectorException e) {
            return iteratorFor(Collections.<SelectionKey>emptySet());
        } catch (Exception e) {
            log.warn("Unexpected exception while selecting", e);
            return iteratorFor(Collections.<SelectionKey>emptySet());
        }

        lastWakeupTime = System.nanoTime();
        Set<SelectionKey> selectedKeys = impl.selectedKeys();
        Iterator<Session> result = iteratorFor(selectedKeys);
        selectedKeys.clear();
        return result;
    }

    @Override
    public long lastWakeupTime() {
        return lastWakeupTime;
    }

    private void registerPendingSessions() throws ClosedChannelException {
        for (Session session; (session = pendingSessions.poll()) != null; ) {
            try {
                ((SelectableJavaSocket) session.socket).getSelectableChannel().register(impl, session.eventsToListen, session);
            } catch (CancelledKeyException key) {
                log.warn("Cannot register session: " + session.toString(), key);
            }
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
