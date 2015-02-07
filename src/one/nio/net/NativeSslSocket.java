package one.nio.net;

import java.io.IOException;
import java.io.RandomAccessFile;

class NativeSslSocket extends NativeSocket {
    NativeSslContext context;
    long ssl;

    NativeSslSocket(int fd, NativeSslContext context, boolean serverMode) throws IOException {
        super(fd);
        this.context = context;
        this.ssl = sslNew(fd, context.ctx, serverMode);
    }

    @Override
    public void close() {
        if (ssl != 0) {
            sslFree(ssl);
            ssl = 0;
        }
        super.close();
    }

    @Override
    public NativeSocket accept() throws IOException {
        return new NativeSslSocket(accept0(), context, true);
    }

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        throw new IOException("Cannot use sendFile with SSL");
    }

    @Override
    public native int writeRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int write(byte[] data, int offset, int count) throws IOException;

    @Override
    public native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public native int readRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int read(byte[] data, int offset, int count) throws IOException;

    @Override
    public native void readFully(byte[] data, int offset, int count) throws IOException;

    static native long sslNew(int fd, long ctx, boolean serverMode) throws IOException;
    static native void sslFree(long ssl);
}
