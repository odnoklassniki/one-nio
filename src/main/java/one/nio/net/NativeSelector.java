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

package one.nio.net;

import one.nio.mem.DirectMemory;

import java.util.Arrays;
import java.util.Iterator;

import static one.nio.util.JavaInternals.unsafe;

final class NativeSelector extends Selector {
    private static final int EPOLL_CTL_ADD = 1;
    private static final int EPOLL_CTL_DEL = 2;
    private static final int EPOLL_CTL_MOD = 3;
    private static final int EPOLL_HEADER_SIZE = 16;
    private static final int EPOLL_MAX_EVENTS = 1000;
    private static final int EPOLL_STRUCT_SIZE = 12;
    private static final int EPOLL_BUF_SIZE = EPOLL_HEADER_SIZE + EPOLL_MAX_EVENTS * EPOLL_STRUCT_SIZE;

    private static native int epollCreate();
    private static native void epollClose(int epollFD);
    private static native int epollWait(int epollFD, long epollStruct, int epollElements);
    private static native void epollCtl(int epollFD, int op, int fd, int data, int events);

    private final int epollFD;
    private final long epollStruct;
    private Session[] sessions;
    private int size;
    private volatile int closeFlag;

    NativeSelector() {
        this.epollFD = epollCreate();
        this.epollStruct = DirectMemory.allocate(EPOLL_BUF_SIZE, this) + EPOLL_HEADER_SIZE;
        this.sessions = new Session[1024];  // must be power of 2, see add()
    }

    @Override
    public final int size() {
        return size;
    }

    @Override
    public boolean isOpen() {
        return closeFlag == 0;
    }

    @Override
    public final synchronized void close() {
        if (closeFlag == 0) {
            closeFlag = -1;
            size = 0;
            epollClose(epollFD);
        }
    }

    @Override
    public final void register(Session session) {
        add(session);
        enable(session);
    }

    @Override
    public final void unregister(Session session) {
        remove(session);
        disable(session);
    }

    @Override
    public final void enable(Session session) {
        epollCtl(epollFD, EPOLL_CTL_ADD, ((NativeSocket) session.socket).fd, session.slot, session.eventsToListen);
    }

    @Override
    public final void disable(Session session) {
        epollCtl(epollFD, EPOLL_CTL_DEL, ((NativeSocket) session.socket).fd, session.slot, 0);
    }

    @Override
    public final void listen(Session session, int events) {
        epollCtl(epollFD, EPOLL_CTL_MOD, ((NativeSocket) session.socket).fd, session.slot, events);
    }

    @Override
    public final Iterator<Session> iterator() {
        return new Iterator<Session>() {
            private Session next = findNext(0);

            private Session findNext(int slot) {
                for (Session[] sessions = NativeSelector.this.sessions; slot < sessions.length; slot++) {
                    Session session = sessions[slot];
                    if (session != null) {
                        return session;
                    }
                }
                return null;
            }

            @Override
            public final boolean hasNext() {
                return next != null;
            }

            @Override
            public final Session next() {
                Session session = next;
                next = findNext(session.slot + 1);
                return session;
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public final Iterator<Session> select() {
        final int count = epollWait(epollFD, epollStruct, EPOLL_MAX_EVENTS) | closeFlag;

        return new Iterator<Session>() {
            private long nextAddr = epollStruct;
            private long lastAddr = nextAddr + count * EPOLL_STRUCT_SIZE;
            private Session next = findNext();

            private Session findNext() {
                for (long currentAddr = nextAddr; currentAddr < lastAddr; currentAddr = nextAddr) {
                    Session session = sessions[unsafe.getInt(currentAddr + 4)];
                    nextAddr = currentAddr + EPOLL_STRUCT_SIZE;
                    if (session != null) {
                        session.events = unsafe.getInt(currentAddr);
                        return session;
                    }
                }
                return null;
            }

            @Override
            public final boolean hasNext() {
                return next != null;
            }

            @Override
            public final Session next() {
                Session handle = next;
                next = findNext();
                return handle;
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public long lastWakeupTime() {
        return unsafe.getLong(epollStruct - EPOLL_HEADER_SIZE);
    }

    private synchronized void add(Session session) {
        if (++size > sessions.length) {
            sessions = Arrays.copyOf(sessions, sessions.length * 2);
        }

        final int mask = sessions.length - 1;
        for (int slot = session.hashCode() & mask; ; slot = (slot + 1) & mask) {
            if (sessions[slot] == null) {
                session.selector = this;
                session.slot = slot;
                sessions[slot] = session;
                return;
            }
        }
    }

    private synchronized void remove(Session session) {
        if (sessions[session.slot] == session) {
            sessions[session.slot] = null;
            session.selector = null;
            size--;
        }
    }
}
