package one.nio.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

final class JavaServerSocket extends Socket {
    ServerSocketChannel ch;

    JavaServerSocket() throws IOException {
        this.ch = ServerSocketChannel.open();
    }

    @Override
    public final boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public final void close() {
        try {
            ch.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public final JavaSocket accept() throws IOException {
        return new JavaSocket(ch.accept());
    }

    @Override
    public final void connect(InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
        ch.socket().bind(new InetSocketAddress(address, port), backlog);
    }

    @Override
    public final int writeRaw(long buf, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int write(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void writeFully(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int readRaw(long buf, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int read(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void readFully(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void setBlocking(boolean blocking) {
        try {
            ch.configureBlocking(blocking);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public final void setTimeout(int timeout) {
        try {
            ch.socket().setSoTimeout(timeout);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final void setKeepAlive(boolean keepAlive) {
        // Ignore
    }

    @Override
    public final void setNoDelay(boolean noDelay) {
        // Ignore
    }

    @Override
    public final void setReuseAddr(boolean reuseAddr) {
        try {
            ch.socket().setReuseAddress(reuseAddr);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final void setBufferSize(int recvBuf, int sendBuf) {
        try {
            ch.socket().setReceiveBufferSize(recvBuf);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) ch.socket().getLocalSocketAddress();
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException();
    }
}
