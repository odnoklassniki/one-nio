package one.nio.serial;

import java.io.IOException;
import java.io.OutputStream;

public class PersistOutputStream extends OutputStream {
    private final InternalPersistStream stream;
    private final OutputStream out;

    public PersistOutputStream(OutputStream out) {
        this.stream = new InternalPersistStream();
        this.out = out;
    }

    public PersistOutputStream(OutputStream out, int initBufferSize) {
        this.stream = new InternalPersistStream(initBufferSize);
        this.out = out;
    }

    @Override
    public void write(int val) throws IOException {
        out.write(val);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        out.write(buf, off, len);
    }

    public void writeObject(Object obj) throws IOException {
        stream.ensureOpen();
        stream.writeObject(obj);
        stream.flushArray();
    }

    @Override
    public void flush() throws IOException {
        stream.flushArray();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        try (OutputStream out = this.out; InternalPersistStream stream = this.stream) {
            stream.flushArray();
        }
    }

    private class InternalPersistStream extends PersistStream {

        public InternalPersistStream() {
        }

        public InternalPersistStream(int capacity) {
            super(capacity);
        }

        @Override
        protected long alloc(int size) throws IOException {
            if (offset + size > limit) {
                if (flushArray()) {
                    return alloc(size);
                } else {
                    return super.alloc(size);
                }
            }
            long currentOffset = offset;
            offset = currentOffset + size;
            return currentOffset;
        }

        private boolean flushArray() throws IOException {
            if (array == null || offset == address) return false;
            int position = (int) (offset - address);
            out.write(array, 0, position);
            offset = address;
            return true;
        }

        private void ensureOpen() throws IOException {
            if (array == null) throw new IOException("Stream closed");
        }

        @Override
        public void close() {
            super.close();
            array = null;
        }
    }
}
