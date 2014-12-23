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

    protected Socket socket;
    protected Selector selector;
    protected int slot;
    protected int events;
    protected int eventsToListen;
    protected boolean closing;
    protected WriteQueue writeQueue;
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

    public boolean isActive() {
        return writeQueue != null;
    }

    @Override
    public synchronized void close() {
        if (socket.isOpen()) {
            closing = true;
            writeQueue = null;
            selector.unregister(this);
            socket.close();
        }
    }

    public synchronized void scheduleClose() {
        if (writeQueue == null) {
            close();
        } else {
            closing = true;
        }
    }

    public synchronized void getQueueStats(long[] stats) {
        int length = 0;
        long bytes = 0;
        for (WriteQueue head = writeQueue; head != null; head = head.next) {
            length++;
            bytes += head.count;
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

    public synchronized void write(byte[] data, int offset, int count) throws IOException {
        if (writeQueue == null) {
            int bytesWritten = socket.write(data, offset, count);
            if (bytesWritten < count) {
                int newEventsToListen;
                if (bytesWritten >= 0) {
                    offset += bytesWritten;
                    count -= bytesWritten;
                    newEventsToListen = WRITEABLE;
                } else {
                    newEventsToListen = SSL | READABLE;
                }
                writeQueue = new WriteQueue(data, offset, count);
                listen(newEventsToListen);
            }
        } else if (!closing) {
            WriteQueue tail = writeQueue;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = new WriteQueue(data, offset, count);
        } else {
            throw new SocketException("Socket closed");
        }
    }

    protected synchronized void processWrite() throws Exception {
        for (WriteQueue head = writeQueue; head != null; head = head.next) {
            int bytesWritten = socket.write(head.data, head.offset, head.count);
            if (bytesWritten < head.count) {
                int newEventsToListen;
                if (bytesWritten >= 0) {
                    head.offset += bytesWritten;
                    head.count -= bytesWritten;
                    newEventsToListen = WRITEABLE;
                } else {
                    newEventsToListen = SSL | READABLE;
                }
                writeQueue = head;
                listen(newEventsToListen);
                return;
            } else if (closing) {
                close();
                return;
            }
        }
        writeQueue = null;
        listen(READABLE);
    }

    protected void processRead(byte[] buffer) throws Exception {
        read(buffer, 0, buffer.length);
    }

    public void process(byte[] buffer) throws Exception {
        lastAccessTime = 0;

        if (eventsToListen >= SSL) {
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

    private static class WriteQueue {
        byte[] data;
        int offset;
        int count;
        WriteQueue next;

        WriteQueue(byte[] data, int offset, int count) {
            this.data = data;
            this.offset = offset;
            this.count = count;
        }
    }
}
