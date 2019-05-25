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

import one.nio.http.ResponseListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;

public class Session implements Closeable {
    protected static final Log log = LogFactory.getLog(Session.class);

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
    protected boolean wasSelected;
    protected boolean closing;
    protected QueueItem queueHead;
    protected volatile long lastAccessTime;

    public Session(Socket socket) {
        this(socket, READABLE);
    }

    public Session(Socket socket, int eventsToListen) {
        this.socket = socket;
        this.eventsToListen = eventsToListen;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public final String getRemoteHost() {
        InetSocketAddress address = socket.getRemoteAddress();
        return address == null ? null : address.getAddress().getHostAddress();
    }

    public final Socket socket() {
        return socket;
    }

    public final long lastAccessTime() {
        return lastAccessTime;
    }

    public boolean isSsl() {
        return socket.getSslContext() != null;
    }

    public int checkStatus(long currentTime, long keepAlive) {
        long lastAccessTime = this.lastAccessTime;
        if (lastAccessTime < currentTime - keepAlive && queueHead == null) {
            return IDLE;
        }
        return ACTIVE;
    }

    @Override
    public synchronized void close() {
        close(null);
    }

    public synchronized void close(Throwable t) {
        QueueItem.releaseChain(queueHead, t);
        queueHead = null;

        if (socket.isOpen()) {
            closing = true;
            if (selector != null) {
                selector.unregister(this);
            }
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
        int bytesRead = socket.read(data, offset, count, 0);
        if (bytesRead >= 0) {
            listen(READABLE);
            return bytesRead;
        } else {
            listen(SSL | WRITEABLE);
            return 0;
        }
    }

    public int readRaw(long address, int count) throws IOException {
        int bytesRead = socket.readRaw(address, count, 0);
        if (bytesRead >= 0) {
            listen(READABLE);
            return bytesRead;
        } else {
            listen(SSL | WRITEABLE);
            return 0;
        }
    }

    public final void write(byte[] data, int offset, int count) throws IOException {
        write(new ArrayQueueItem(data, offset, count, 0));
    }

    public final void write(byte[] data, int offset, int count, int flags) throws IOException {
        write(new ArrayQueueItem(data, offset, count, flags));
    }

    public final synchronized void write(QueueItem item) throws IOException {
        try {
            if (closing) {
                throw new SocketException("Socket closed");
            }
            if (queueHead == null) {
                while (item != null) {
                    int written = item.write(socket);
                    if (item.remaining() > 0) {
                        queueHead = item;
                        listen(written >= 0 ? WRITEABLE : SSL | READABLE);
                        break;
                    } else {
                        item.release(null);
                    }
                    item = item.next;
                }
                lastAccessTime = System.currentTimeMillis();
            } else {
                queueHead.append(item);
            }
        } catch (IOException e) {
            QueueItem.releaseChain(item, e);
            throw e;
        }
    }

    protected void processRead(byte[] buffer) throws Exception {
        read(buffer, 0, buffer.length);
    }

    protected void processWrite() throws Exception {
        if (eventsToListen == READABLE || eventsToListen == (SSL | WRITEABLE)) {
            throw new IOException("Illegal subscription state: " + eventsToListen);
        }

        for (QueueItem item = queueHead; item != null; queueHead = item = item.next) {
            int written = item.write(socket);
            if (item.remaining() > 0) {
                listen(written >= 0 ? WRITEABLE : SSL | READABLE);
                return;
            }
            item.release(null);
        }

        if (closing) {
            close();
        } else {
            listen(READABLE);
        }
    }

    public synchronized void process(byte[] buffer) throws Exception {
        lastAccessTime = Long.MAX_VALUE;

        if ((events & CLOSING) != 0) {
            close();
        } else if (eventsToListen >= SSL) {
            // At any time during SSL connection a renegotiation may occur, that is,
            // a write operation may require a readable socket, and a read operation
            // may require a writable socket. In this case eventsToListen will have SSL flag set.
            if ((events & READABLE) != 0) processWrite();
            if ((events & WRITEABLE) != 0) processRead(buffer);
        } else {
            if ((events & WRITEABLE) != 0) processWrite();
            if ((events & READABLE) != 0) processRead(buffer);
        }

        wasSelected = true;
        lastAccessTime = System.currentTimeMillis();
    }

    public void handleException(Throwable e) {
        if (e instanceof SocketException) {
            if (log.isDebugEnabled()) log.debug("Connection closed: " + getRemoteHost());
        } else if (e instanceof SSLException) {
            if (log.isDebugEnabled()) log.debug("SSL/TLS failure: " + getRemoteHost());
        } else {
            log.error("Cannot process session from " + getRemoteHost(), e);
        }
        close(e);
    }

    public static abstract class QueueItem {
        protected QueueItem next;

        public QueueItem append(QueueItem next) {
            QueueItem tail = this;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = next;
            return this;
        }

        public QueueItem next() {
            return next;
        }

        public int remaining() {
            return 0;
        }

        public void release(Throwable problem) {
            // Override in subclasses to release associated resources
        }

        public static void releaseChain(QueueItem item, Throwable problem) {
            for (; item != null; item = item.next) {
                item.release(problem);
            }
        }

        public abstract int write(Socket socket) throws IOException;
    }

    public static class ArrayQueueItem extends QueueItem {
        protected final byte[] data;
        protected final int offset;
        protected final int count;
        protected int written;
        protected final int flags;

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

    public static class ArrayAndNativeQueueItem extends QueueItem {
        protected final byte[] array;
        protected final int arrayLength;
        protected final long nativeAddr;
        protected final int nativeCount;
        protected final ResponseListener listener;
        protected final int totalCount;
        protected int written;

        public ArrayAndNativeQueueItem(byte[] array, int arrayLength, long nativeAddr, int nativeLength, ResponseListener listener){
            this.array = array;
            this.arrayLength = arrayLength;
            this.nativeAddr = nativeAddr;
            this.nativeCount = nativeLength;
            this.listener = listener;
            this.totalCount = arrayLength + nativeLength;
        }

        @Override
        public int remaining() {
            return totalCount - written;
        }

        @Override
        public int write(Socket socket) throws IOException {
            int arrayWritten = 0;
            if (written < arrayLength) {
                arrayWritten = socket.write(array, written, arrayLength - written, 0);
                if (arrayWritten > 0) {
                    written += arrayWritten;
                }
                if (written < arrayLength | nativeAddr <= 0) {
                    return arrayWritten;
                }
            }

            int nativeWritten = socket.writeRaw(nativeAddr, totalCount - written, 0);
            if (nativeWritten > 0) {
                written += nativeWritten;
            }
            return arrayWritten + nativeWritten;
        }

        @Override
        public void release(Throwable problem) {
            if (listener != null) {
                listener.onDone(written, totalCount, problem);
            }
        }
    }
}
