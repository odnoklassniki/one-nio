/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.os.NativeLibrary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public abstract class Socket implements ByteChannel {
    // Protocol family
    public static final int AF_UNIX  = 1;
    public static final int AF_INET  = 2;
    public static final int AF_INET6 = 10;

    // Socket types
    public static final int SOCK_STREAM    = 1;
    public static final int SOCK_DGRAM     = 2;
    public static final int SOCK_RAW       = 3;
    public static final int SOCK_RDM       = 4;
    public static final int SOCK_SEQPACKET = 5;

    // Use when the address has no port (i.e. for AF_UNIX address)
    public static final int NO_PORT = -1;

    // Levels for getOption()
    public static final int SOL_SOCKET = 1;
    public static final int SOL_IP     = 0;
    public static final int SOL_IPV6   = 41;
    public static final int SOL_TCP    = 6;
    public static final int SOL_UDP    = 17;

    // Flags for readRaw / writeRaw
    public static final int MSG_OOB       = 0x01;
    public static final int MSG_PEEK      = 0x02;
    public static final int MSG_DONTROUTE = 0x04;
    public static final int MSG_TRUNC     = 0x20;
    public static final int MSG_DONTWAIT  = 0x40;
    public static final int MSG_WAITALL   = 0x100;
    public static final int MSG_MORE      = 0x8000;

    // Options for setTos
    public static final int IPTOS_MINCOST     = 0x02;
    public static final int IPTOS_RELIABILITY = 0x04;
    public static final int IPTOS_THROUGHPUT  = 0x08;
    public static final int IPTOS_LOWDELAY    = 0x10;

    // Socket options
    public static final int SO_DEBUG     = 1;
    public static final int SO_REUSEADDR = 2;
    public static final int SO_TYPE      = 3;
    public static final int SO_ERROR     = 4;
    public static final int SO_DONTROUTE = 5;
    public static final int SO_BROADCAST = 6;
    public static final int SO_SNDBUF    = 7;
    public static final int SO_RCVBUF    = 8;
    public static final int SO_KEEPALIVE = 9;
    public static final int SO_OOBINLINE = 10;
    public static final int SO_NO_CHECK  = 11;
    public static final int SO_PRIORITY  = 12;
    public static final int SO_LINGER    = 13;
    public static final int SO_BSDCOMPAT = 14;
    public static final int SO_REUSEPORT = 15;
    public static final int SO_PASSCRED  = 16;
    public static final int SO_PEERCRED  = 17;
    public static final int SO_RCVLOWAT  = 18;
    public static final int SO_SNDLOWAT  = 19;
    public static final int SO_RCVTIMEO  = 20;
    public static final int SO_SNDTIMEO  = 21;

    // TCP socket options
    public static final int TCP_NODELAY      = 1;
    public static final int TCP_MAXSEG       = 2;
    public static final int TCP_CORK         = 3;
    public static final int TCP_KEEPIDLE     = 4;
    public static final int TCP_KEEPINTVL    = 5;
    public static final int TCP_KEEPCNT      = 6;
    public static final int TCP_SYNCNT       = 7;
    public static final int TCP_LINGER2      = 8;
    public static final int TCP_DEFER_ACCEPT = 9;
    public static final int TCP_WINDOW_CLAMP = 10;
    public static final int TCP_INFO         = 11;
    public static final int TCP_QUICKACK     = 12;
    public static final int TCP_CONGESTION   = 13;
    public static final int TCP_USER_TIMEOUT = 18;

    public abstract boolean isOpen();
    public abstract void close();
    public abstract Socket accept() throws IOException;
    public abstract void connect(InetAddress address, int port) throws IOException;
    public abstract void bind(InetAddress address, int port, int backlog) throws IOException;
    public abstract void listen(int backlog) throws IOException;
    public abstract int writeRaw(long buf, int count, int flags) throws IOException;
    public abstract int write(byte[] data, int offset, int count, int flags) throws IOException;
    public abstract void writeFully(byte[] data, int offset, int count) throws IOException;
    public abstract int send(ByteBuffer src, int flags, InetAddress address, int port) throws IOException;
    public abstract int readRaw(long buf, int count, int flags) throws IOException;
    public abstract int read(byte[] data, int offset, int count, int flags) throws IOException;
    public abstract void readFully(byte[] data, int offset, int count) throws IOException;
    public abstract InetSocketAddress recv(ByteBuffer dst, int flags) throws IOException;
    public abstract long sendFile(RandomAccessFile file, long offset, long count) throws IOException;
    public abstract int sendMsg(Msg msg, int flags) throws IOException;
    public abstract int recvMsg(Msg msg, int flags) throws IOException;
    public abstract void setBlocking(boolean blocking);
    public abstract boolean isBlocking();
    public abstract void setTimeout(int timeout);
    public abstract int getTimeout();
    public abstract void setKeepAlive(boolean keepAlive);
    public abstract boolean getKeepAlive();
    public abstract void setNoDelay(boolean noDelay);
    public abstract boolean getNoDelay();
    public abstract void setTcpFastOpen(boolean tcpFastOpen);
    public abstract boolean getTcpFastOpen();
    public abstract void setDeferAccept(boolean deferAccept);
    public abstract boolean getDeferAccept();
    public abstract void setReuseAddr(boolean reuseAddr, boolean reusePort);
    public abstract boolean getReuseAddr();
    public abstract boolean getReusePort();
    public abstract void setRecvBuffer(int recvBuf);
    public abstract int getRecvBuffer();
    public abstract void setSendBuffer(int sendBuf);
    public abstract int getSendBuffer();
    public abstract void setTos(int tos);
    public abstract int getTos();
    public void setNotsentLowat(int lowat) {}
    public int getNotsentLowat() {return 0;}
    public void setThinLinearTimeouts(boolean thinLto) {}
    public boolean getThinLinearTimeouts(){return false;}
    public abstract byte[] getOption(int level, int option);
    public abstract boolean setOption(int level, int option, byte[] value);
    public abstract InetSocketAddress getLocalAddress();
    public abstract InetSocketAddress getRemoteAddress();
    public abstract Socket sslWrap(SslContext context) throws IOException;
    public abstract Socket sslUnwrap();
    public abstract SslContext getSslContext();
    public abstract <T> T getSslOption(SslOption<T> option);

    public Socket acceptNonBlocking() throws IOException {
        Socket s = accept();
        if (s != null) {
            s.setBlocking(false);
        }
        return s;
    }

    public void connect(String host, int port) throws IOException {
        connect(InetAddress.getByName(host), port);
    }

    public void bind(String host, int port, int backlog) throws IOException {
        bind(InetAddress.getByName(host), port, backlog);
    }

    public int send(ByteBuffer data, int flags, String host, int port) throws IOException {
        return send(data, flags, InetAddress.getByName(host), port);
    }

    public void handshake(String sniHostname) throws IOException {
        // Only for SSL sockets
    }

    public int write(byte[] data, int offset, int count) throws IOException {
        return write(data, offset, count, 0);
    }

    public int read(byte[] data, int offset, int count) throws IOException {
        return read(data, offset, count, 0);
    }

    @Deprecated
    public static Socket create() throws IOException {
        return createClientSocket(null);
    }

    public static Socket createClientSocket() throws IOException {
        return createClientSocket(null);
    }

    public static Socket createClientSocket(SslContext sslContext) throws IOException {
        Socket socket;
        if (NativeLibrary.IS_SUPPORTED) {
            socket = new NativeSocket(0, SOCK_STREAM);
        } else {
            socket = sslContext == null ? new JavaSocket() : new JavaSslClientSocket((JavaSslClientContext) sslContext);
        }
        return socket;
    }

    public static Socket createServerSocket() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSocket(0, SOCK_STREAM) : new JavaServerSocket();
    }

    public static Socket createDatagramSocket() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSocket(0, SOCK_DGRAM) : new JavaDatagramSocket();
    }

    public static Socket createUnixSocket(int type) throws IOException {
        if (!NativeLibrary.IS_SUPPORTED) {
            throw new IOException("Unix sockets are supported in native mode only");
        }

        return new NativeSocket(AF_UNIX, type);
    }

    public static Socket connectInet(InetAddress address, int port) throws IOException {
        Socket sock = createClientSocket();
        sock.connect(address, port);
        return sock;
    }

    public static Socket bindInet(InetAddress address, int port, int backlog) throws IOException {
        Socket sock = createServerSocket();
        sock.bind(address, port, backlog);
        sock.listen(backlog);
        return sock;
    }

    public static Socket connectUnix(File unixPath) throws IOException {
        if (!NativeLibrary.IS_SUPPORTED) {
            throw new IOException("Unix sockets are supported in native mode only");
        }

        NativeSocket sock = new NativeSocket(AF_UNIX, SOCK_STREAM);
        sock.connect(unixPath.getAbsolutePath(), NO_PORT);
        return sock;
    }

    public static Socket bindUnix(File unixPath, int backlog) throws IOException {
        if (!NativeLibrary.IS_SUPPORTED) {
            throw new IOException("Unix sockets are supported in native mode only");
        }

        NativeSocket sock = new NativeSocket(AF_UNIX, SOCK_STREAM);
        sock.bind(unixPath.getAbsolutePath(), NO_PORT, backlog);
        sock.listen(backlog);
        return sock;
    }

    public static Socket fromFD(int fd) throws IOException {
        if (NativeLibrary.IS_SUPPORTED) {
            return new NativeSocket(fd);
        }
        throw new IOException("Operation is not supported");
    }
}
