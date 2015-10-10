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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;

public class Session implements Closeable {
    public static final int READABLE   = SelectionKey.OP_READ;
    public static final int WRITEABLE  = SelectionKey.OP_WRITE;
    public static final int CLOSING    = 0x18;
    public static final int EVENT_MASK = 0xff;
    public static final int SSL        = 0x100;

    public static final int ACTIVE = 0;
    public static final int IDLE   = 1;
    public static final int STALE  = 2;

    protected Socket socket;
    protected Selector selector;
    protected int slot;
    protected int events;
    protected int eventsToListen;
    protected boolean closing;
    protected QueueItem queueHead;
    protected volatile long lastAccessTime;

    public Session(Socket socket) {
        this.socket = socket;
        this.eventsToListen = READABLE;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public final String getRemoteHost() {
        InetSocketAddress address = socket.getRemoteAddress();
        return address == null ? null : address.getAddress().getHostAddress();
    }

    public final long lastAccessTime() {
        return lastAccessTime;
    }

    public int checkStatus(long currentTime, long keepAlive) {
        long lastAccessTime = this.lastAccessTime;
        if (lastAccessTime < currentTime - keepAlive) {
            if (queueHead == null) {
                return IDLE;
            } else if (lastAccessTime < currentTime - keepAlive * 8) {
                return STALE;
            }
        }
        return ACTIVE;
    }

    @Override
    public synchronized void close() {
        for (QueueItem item = queueHead; item != null; item = item.next) {
            item.release();
        }
        queueHead = null;

        if (socket.isOpen()) {
            closing = true;
            selector.unregister(this);
            socket.close();
        }
    }

    public synchronized void scheduleClose() {
        if (queueHead == null) {
            close();
        } else {
            closing = true;
        }
    }

    public synchronized void getQueueStats(long[] stats) {
        int length = 0;
        long bytes = 0;
        for (QueueItem item = queueHead; item != null; item = item.next) {
            length++;
            bytes += item.remaining();
        }
        stats[0] = length;
        stats[1] = bytes;
    }

    public void listen(int newEventsToListen) {
        if (newEventsToListen != eventsToListen) {
            eventsToListen = newEventsToListen;
            selector.listen(this, newEventsToListen & EVENT_MASK);
        }
    }

    public int read(byte[] data, int offset, int count) throws IOException {
        int bytesRead = socket.read(data, offset, count);
        if (bytesRead >= 0) {
            listen(READABLE);
            return bytesRead;
        } else {
            listen(SSL | WRITEABLE);
            return 0;
        }
    }

    public void write(byte[] data, int offset, int count) throws IOException {
        write(data, offset, count, 0);
    }

    public void write(byte[] data, int offset, int count, int flags) throws IOException {
        write(new ArrayQueueItem(data, offset, count, flags));
    }

    protected synchronized void write(QueueItem item) throws IOException {
        if (closing) {
            throw new SocketException("Socket closed");
        }

        if (queueHead == null) {
            int written = item.write(socket);
            if (item.remaining() > 0) {
                queueHead = item;
                listen(written >= 0 ? WRITEABLE : SSL | READABLE);
            } else {
                item.release();
            }
        } else {
            QueueItem tail = queueHead;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = item;
        }
    }

    protected void processRead(byte[] buffer) throws Exception {
        read(buffer, 0, buffer.length);
    }

    protected synchronized void processWrite() throws Exception {
        for (QueueItem item = queueHead; item != null; queueHead = item = item.next) {
            int written = item.write(socket);
            if (item.remaining() > 0) {
                listen(written >= 0 ? WRITEABLE : SSL | READABLE);
                return;
            }
            item.release();
        }

        if (closing) {
            close();
        } else {
            listen(READABLE);
        }
    }

    public void process(byte[] buffer) throws Exception {
        lastAccessTime = Long.MAX_VALUE;

        if (eventsToListen >= SSL) {
            // At any time during SSL connection a renegotiation may occur, that is,
            // a write operation may require a readable socket, and a read operation
            // may require a writable socket. In this case eventsToListen will have SSL flag set.
            if ((events & READABLE) != 0) processWrite();
            if ((events & WRITEABLE) != 0) processRead(buffer);
        } else {
            if ((events & WRITEABLE) != 0) processWrite();
            if ((events & READABLE) != 0) processRead(buffer);
        }

        if ((events & CLOSING) != 0) {
            close();
        }

        lastAccessTime = System.currentTimeMillis();
    }

    public static abstract class QueueItem {
        protected QueueItem next;

        public int remaining() {
            return 0;
        }

        public void release() {
            // Override in subclasses to release associated resources
        }

        public abstract int write(Socket socket) throws IOException;
    }

    public static class ArrayQueueItem extends QueueItem {
        protected byte[] data;
        protected int offset;
        protected int count;
        protected int written;
        protected int flags;
        
        public ArrayQueueItem(byte[] data, int offset, int count, int flags) {
            this.data = data;
            this.offset = offset;
            this.count = count;
            this.flags = flags;
        }

        @Override
        public int remaining() {
            return count - written;
        }

        @Override
        public int write(Socket socket) throws IOException {
            int bytes = socket.write(data, offset + written, count - written, flags);
            if (bytes > 0) {
                written += bytes;
            }
            return bytes;
        }
    }
}
