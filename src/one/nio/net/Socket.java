package one.nio.net;

import one.nio.os.NativeLibrary;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public abstract class Socket implements Closeable {
    public abstract boolean isOpen();
    public abstract void close();
    public abstract Socket accept() throws IOException;
    public abstract void connect(InetAddress address, int port) throws IOException;
    public abstract void bind(InetAddress address, int port, int backlog) throws IOException;
    public abstract int writeRaw(long buf, int count) throws IOException;
    public abstract int write(byte[] data, int offset, int count) throws IOException;
    public abstract void writeFully(byte[] data, int offset, int count) throws IOException;
    public abstract int readRaw(long buf, int count) throws IOException;
    public abstract int read(byte[] data, int offset, int count) throws IOException;
    public abstract void readFully(byte[] data, int offset, int count) throws IOException;
    public abstract void setBlocking(boolean blocking);
    public abstract void setTimeout(int timeout);
    public abstract void setKeepAlive(boolean keepAlive);
    public abstract void setNoDelay(boolean noDelay);
    public abstract void setReuseAddr(boolean reuseAddr);
    public abstract void setBufferSize(int recvBuf, int sendBuf);
    public abstract InetSocketAddress getLocalAddress();
    public abstract InetSocketAddress getRemoteAddress();

    public void connect(String host, int port) throws IOException {
        connect(InetAddress.getByName(host), port);
    }

    public void bind(String host, int port, int backlog) throws IOException {
        bind(InetAddress.getByName(host), port, backlog);
    }

    public static Socket create() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSocket() : new JavaSocket();
    }

    public static Socket createServerSocket() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSocket() : new JavaServerSocket();
    }
}
