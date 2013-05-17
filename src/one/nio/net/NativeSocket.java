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
        byte[] buffer = new byte[24];
        return makeAddress(buffer, getsockname(buffer));
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        byte[] buffer = new byte[24];
        return makeAddress(buffer, getpeername(buffer));
    }

    private InetSocketAddress makeAddress(byte[] buffer, int length) {
        byte[] address;
        if (length == 8) {
            address = new byte[4];
            System.arraycopy(buffer, 4, address, 0, 4);
        } else if (length == 24) {
            address = new byte[16];
            System.arraycopy(buffer, 8, address, 0, 16);
        } else {
            return null;
        }

        int port = (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff);

        try {
            return new InetSocketAddress(InetAddress.getByAddress(address), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public final void connect(InetAddress address, int port) throws IOException {
        connect0(address.getAddress(), port);
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
        bind0(address.getAddress(), port, backlog);
    }

    @Override
    public final native void close();

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
    public final native void setDeferAccept(boolean deferAccept);

    @Override
    public final native void setReuseAddr(boolean reuseAddr);

    @Override
    public final native void setBufferSize(int recvBuf, int sendBuf);

    private static native int socket0() throws IOException;
    private native int accept0() throws IOException;
    private native void connect0(byte[] address, int port) throws IOException;
    private native void bind0(byte[] address, int port, int backlog) throws IOException;
    private native int getsockname(byte[] buffer);
    private native int getpeername(byte[] buffer);
}
