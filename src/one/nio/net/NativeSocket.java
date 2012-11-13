package one.nio.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

final class NativeSocket extends Socket {
    int fd;

    NativeSocket() throws IOException {
        this.fd = socket0();
    }

    NativeSocket(int fd) {
        this.fd = fd;
    }

    @Override
    public final boolean isOpen() {
        return fd >= 0;
    }
    
    @Override
    public final NativeSocket accept() throws IOException {
        return new NativeSocket(accept0());
    }

    @Override
    public final InetSocketAddress getLocalAddress() {
        byte[] address = new byte[4];
        int port = getsockname(address);
        try {
            return port >= 0 ? new InetSocketAddress(InetAddress.getByAddress(address), port) : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        byte[] address = new byte[4];
        int port = getpeername(address);
        try {
            return port >= 0 ? new InetSocketAddress(InetAddress.getByAddress(address), port) : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public final native void close();

    @Override
    public final native void connect(InetAddress address, int port) throws IOException;

    @Override
    public final native void bind(InetAddress address, int port, int backlog) throws IOException;

    @Override
    public final native int writeRaw(long buf, int count) throws IOException;

    @Override
    public final native int write(byte[] data, int offset, int count) throws IOException;

    @Override
    public final native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public final native int readRaw(long buf, int count) throws IOException;

    @Override
    public final native int read(byte[] data, int offset, int count) throws IOException;

    @Override
    public final native void readFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public final native void setBlocking(boolean blocking);

    @Override
    public final native void setTimeout(int timeout);

    @Override
    public final native void setKeepAlive(boolean keepAlive);

    @Override
    public final native void setNoDelay(boolean noDelay);

    @Override
    public final native void setReuseAddr(boolean reuseAddr);

    @Override
    public final native void setBufferSize(int recvBuf, int sendBuf);

    private static native int socket0() throws IOException;
    private native int accept0() throws IOException;
    private native int getsockname(byte[] address);
    private native int getpeername(byte[] address);
}
