/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public abstract class Socket implements Closeable {
    public static final int SOL_SOCKET    = 1;
    public static final int SOL_IP        = 0;
    public static final int SOL_IPV6      = 41;
    public static final int SOL_TCP       = 6;
    public static final int SOL_UDP       = 17;

    public static final int MSG_OOB       = 0x01;
    public static final int MSG_PEEK      = 0x02;
    public static final int MSG_DONTROUTE = 0x04;
    public static final int MSG_DONTWAIT  = 0x40;
    public static final int MSG_WAITALL   = 0x100;
    public static final int MSG_MORE      = 0x8000;

    public abstract boolean isOpen();
    public abstract void close();
    public abstract Socket accept() throws IOException;
    public abstract void connect(InetAddress address, int port) throws IOException;
    public abstract void bind(InetAddress address, int port, int backlog) throws IOException;
    public abstract int writeRaw(long buf, int count, int flags) throws IOException;
    public abstract int write(byte[] data, int offset, int count, int flags) throws IOException;

    public int write(byte[] data, int offset, int count) throws IOException {
        return write(data, offset, count, 0);
    }

    public abstract void writeFully(byte[] data, int offset, int count) throws IOException;
    public abstract int readRaw(long buf, int count, int flags) throws IOException;
    public abstract int read(byte[] data, int offset, int count) throws IOException;
    public abstract void readFully(byte[] data, int offset, int count) throws IOException;
    public abstract long sendFile(RandomAccessFile file, long offset, long count) throws IOException;
    public abstract void setBlocking(boolean blocking);
    public abstract void setTimeout(int timeout);
    public abstract void setKeepAlive(boolean keepAlive);
    public abstract void setNoDelay(boolean noDelay);
    public abstract void setDeferAccept(boolean deferAccept);
    public abstract void setReuseAddr(boolean reuseAddr);
    public abstract void setRecvBuffer(int recvBuf);
    public abstract void setSendBuffer(int sendBuf);
    public abstract byte[] getOption(int level, int option);
    public abstract boolean setOption(int level, int option, byte[] value);
    public abstract InetSocketAddress getLocalAddress();
    public abstract InetSocketAddress getRemoteAddress();
    public abstract Socket ssl(SslContext context) throws IOException;

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

    public static Socket fromFD(int fd) throws IOException {
        if (NativeLibrary.IS_SUPPORTED) {
            return new NativeSocket(fd);
        }
        throw new IOException("Operation is not supported");
    }
}
