package one.nio.net;

import java.io.IOException;

class NativeSslSocket extends NativeSocket {
    private static final long GLOBAL_SSL_CTX = sslInit();

    long ssl;

    NativeSslSocket(int fd, boolean serverMode) throws IOException {
        super(fd);
        this.ssl = sslNew(fd, serverMode, GLOBAL_SSL_CTX);
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

    private static native long sslInit();
    private static native long sslNew(int fd, boolean serverMode, long ctx) throws IOException;
    private static native void sslFree(long ssl);
}
