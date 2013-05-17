package one.nio.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Session implements Closeable {
    public static final int READABLE  = 1;
    public static final int WRITEABLE = 4;
    public static final int CLOSING   = 0x18;

    protected final Socket socket;
    protected Selector selector;
    protected int slot;
    protected int events;
    protected WriteQueue writeQueue;
    protected volatile long lastAccessTime;

    public Session(Socket socket) {
        this.socket = socket;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public final String clientIp() {
        InetSocketAddress address = socket.getRemoteAddress();
        return address == null ? "<unconnected>" : address.getAddress().getHostAddress();
    }

    public final long lastAccessTime() {
        return lastAccessTime;
    }

    public final boolean writePending() {
        return writeQueue != null;
    }

    @Override
    public synchronized void close() {
        if (socket.isOpen()) {
            writeQueue = null;
            selector.unregister(this);
            socket.close();
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

    public synchronized void write(byte[] data, int offset, int count, boolean close) throws IOException {
        if (writeQueue != null) {
            WriteQueue tail = writeQueue;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = new WriteQueue(data, offset, count, close);
        } else if (socket.isOpen()) {
            int bytesWritten = socket.write(data, offset, count);
            if (bytesWritten < count) {
                offset += bytesWritten;
                count -= bytesWritten;
                writeQueue = new WriteQueue(data, offset, count, close);
                selector.listen(this, WRITEABLE);
            } else if (close) {
                close();
            }
        }
    }

    protected synchronized void processWrite() throws Exception {
        for (WriteQueue head = writeQueue; head != null; head = head.next) {
            int bytesWritten = socket.write(head.data, head.offset, head.count);
            if (bytesWritten < head.count) {
                head.offset += bytesWritten;
                head.count -= bytesWritten;
                writeQueue = head;
                return;
            } else if (head.close) {
                close();
                return;
            }
        }
        writeQueue = null;
        selector.listen(this, READABLE);
    }

    protected void processRead(byte[] buffer) throws Exception {
       socket.read(buffer, 0, buffer.length);
    }

    public void process(byte[] buffer) throws Exception {
        lastAccessTime = 0;
        if ((events & WRITEABLE) != 0) {
            processWrite();
        }
        if ((events & (READABLE | CLOSING)) != 0) {
            processRead(buffer);
        }
        lastAccessTime = System.currentTimeMillis();
    }

    private static class WriteQueue {
        byte[] data;
        int offset;
        int count;
        boolean close;
        WriteQueue next;

        WriteQueue(byte[] data, int offset, int count, boolean close) {
            this.data = data;
            this.offset = offset;
            this.count = count;
            this.close = close;
        }
    }
}
