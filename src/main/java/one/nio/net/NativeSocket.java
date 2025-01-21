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

import one.nio.mem.DirectMemory;
import one.nio.os.Mem;
import one.nio.util.JavaInternals;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

class NativeSocket extends Socket {
    private static final int INET_FAMILY = initNatives(Boolean.getBoolean("java.net.preferIPv4Stack"));

    private static final long ARRAY_FIELD = JavaInternals.fieldOffset(ByteBuffer.class, "hb");
    private static final long OFFSET_FIELD = JavaInternals.fieldOffset(ByteBuffer.class, "offset");

    int fd;

    NativeSocket(int domain, int type) throws IOException {
        this.fd = socket0(domain != 0 ? domain : INET_FAMILY, type);
    }

    NativeSocket(int fd) {
        this.fd = fd;
    }

    @Override
    public final boolean isOpen() {
        return fd >= 0;
    }

    @Override
    public NativeSocket accept() throws IOException {
        int fd = accept0(false);
        return fd >= 0 ? new NativeSocket(fd) : null;
    }

    @Override
    public NativeSocket acceptNonBlocking() throws IOException {
        int fd = accept0(true);
        return fd >= 0 ? new NativeSocket(fd) : null;
    }

    @Override
    public final native InetSocketAddress getLocalAddress();

    @Override
    public final native InetSocketAddress getRemoteAddress();

    @Override
    public Socket sslWrap(SslContext context) throws IOException {
        return new NativeSslSocket(fd, (NativeSslContext) context, false);
    }

    @Override
    public Socket sslUnwrap() {
        return this;
    }

    @Override
    public SslContext getSslContext() {
        return null;
    }

    @Override
    public <T> T getSslOption(SslOption<T> option) {
        return null;
    }

    @Override
    public void connect(InetAddress address, int port) throws IOException {
        connect0(address.getAddress(), port);
    }

    @Override
    public void connect(String host, int port) throws IOException {
        connect0(toNativeAddr(host, port), port);
    }

    @Override
    public void bind(InetAddress address, int port, int backlog) throws IOException {
        bind0(address.getAddress(), port);
    }

    @Override
    public void bind(String host, int port, int backlog) throws IOException {
        bind0(toNativeAddr(host, port), port);
    }

    @Override
    public native void listen(int backlog) throws IOException;

    @Override
    public native void close();

    @Override
    public native int writeRaw(long buf, int count, int flags) throws IOException;

    @Override
    public int send(ByteBuffer src, int flags, InetAddress address, int port) throws IOException {
        return sendTo(src, flags, address.getAddress(), port);
    }

    @Override
    public int send(ByteBuffer data, int flags, String host, int port) throws IOException {
        return sendTo(data, flags, toNativeAddr(host, port), port);
    }

    private int sendTo(ByteBuffer src, int flags, Object address, int port) throws IOException {
        int result;
        if (src.isDirect()) {
            result = sendTo1(DirectMemory.getAddress(src) + src.position(), src.remaining(), flags, address, port);
        } else if (src.hasArray()) {
            result = sendTo0(src.array(), src.arrayOffset() + src.position(), src.remaining(), flags, address, port);
        } else if (src.isReadOnly()) {
            try {
                result = sendTo0(getArray(src), getOffset(src) + src.position(), src.remaining(), flags, address, port);
            } catch (IllegalAccessException e) {
                throw new IOException("Failed to access array in readonly ByteBuffer", e);
            }
        } else {
            throw new IOException("Cannot handle ByteBuffer " + src);
        }

        if (result > 0) {
            src.position(src.position() + result);
        }

        return result;
    }

    private byte[] getArray(ByteBuffer src) throws IllegalAccessException {
        return (byte[]) JavaInternals.unsafe.getObject(src, ARRAY_FIELD);
    }

    private int getOffset(ByteBuffer src) throws IllegalAccessException {
        return JavaInternals.unsafe.getInt(src, OFFSET_FIELD);
    }

    @Override
    public native int write(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public native int readRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int read(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public InetSocketAddress recv(ByteBuffer dst, int flags) throws IOException {
        AddressHolder holder = new AddressHolder();

        int result;
        if (dst.hasArray()) {
            result = recvFrom0(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining(), flags, holder);
        } else {
            result = recvFrom1(DirectMemory.getAddress(dst) + dst.position(), dst.remaining(), flags, holder);
        }

        if (result > 0) {
            dst.position(dst.position() + result);
            return holder.address;
        }
        return null;
    }

    @Override
    public native void readFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return sendFile0(Mem.getFD(file.getFD()), offset, count);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int bytes;
        if (dst.isDirect()) {
            bytes = readRaw(DirectMemory.getAddress(dst) + dst.position(), dst.remaining(), 0);
        } else {
            bytes = read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining(), 0);
        }
        dst.position(dst.position() + bytes);
        return bytes;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int bytes;

        if (src.isDirect()) {
            bytes = writeRaw(DirectMemory.getAddress(src) + src.position(), src.remaining(), 0);
        } else if (src.hasArray()) {
            bytes = write(src.array(), src.arrayOffset() + src.position(), src.remaining(), 0);
        } else if (src.isReadOnly()) {
            try {
                bytes = write(getArray(src), getOffset(src) + src.position(), src.remaining(), 0);
            } catch (IllegalAccessException e) {
                throw new IOException("Failed to access array in readonly ByteBuffer", e);
            }
        } else {
            throw new IOException("Cannot handle ByteBuffer " + src);
        }
        src.position(src.position() + bytes);
        return bytes;
    }

    @Override
    public int sendMsg(Msg msg, int flags) throws IOException {
        return sendMsg0(msg.data(), msg.cmsgType(), msg.cmsgData(), flags);
    }

    @Override
    public int recvMsg(Msg msg, int flags) throws IOException {
        return recvMsg0(msg.data(), msg, flags);
    }

    @Override
    public final native void setBlocking(boolean blocking);

    @Override
    public final native boolean isBlocking();

    @Override
    public final native void setTimeout(int timeout);

    @Override
    public final native int getTimeout();

    @Override
    public final native void setKeepAlive(boolean keepAlive);

    @Override
    public final native boolean getKeepAlive();

    @Override
    public final native void setNoDelay(boolean noDelay);

    @Override
    public final native boolean getNoDelay();

    @Override
    public final native void setTcpFastOpen(boolean tcpFastOpen);

    @Override
    public final native boolean getTcpFastOpen();

    @Override
    public final native void setDeferAccept(boolean deferAccept);

    @Override
    public final native boolean getDeferAccept();

    @Override
    public final native void setReuseAddr(boolean reuseAddr, boolean reusePort);

    @Override
    public final native boolean getReuseAddr();

    @Override
    public final native boolean getReusePort();

    @Override
    public final native void setRecvBuffer(int recvBuf);

    @Override
    public final native int getRecvBuffer();

    @Override
    public final native void setSendBuffer(int sendBuf);

    @Override
    public final native int getSendBuffer();

    @Override
    public final native void setTos(int tos);

    @Override
    public final native int getTos();

    @Override
    public final native void setNotsentLowat(int lowat);

    @Override
    public final native int getNotsentLowat();

    @Override
    public final native void setThinLinearTimeouts(boolean thinLto);

    @Override
    public final native boolean getThinLinearTimeouts();

    @Override
    public native byte[] getOption(int level, int option);

    @Override
    public native boolean setOption(int level, int option, byte[] value);

    static Object toNativeAddr(String host, int port) throws UnknownHostException {
        return port == NO_PORT ? host : InetAddress.getByName(host).getAddress();
    }

    private static native int initNatives(boolean preferIPv4);

    private static native int socket0(int domain, int type) throws IOException;

    final native void connect0(Object address, int port) throws IOException;
    final native void bind0(Object address, int port) throws IOException;
    final native int accept0(boolean nonblock) throws IOException;
    native long sendFile0(int sourceFD, long offset, long count) throws IOException;
    final native int sendTo0(byte[] data, int offset, int size, int flags, Object address, int port) throws IOException;
    final native int sendTo1(long buf, int size, int flags, Object address, int port) throws IOException;
    final native int recvFrom0(byte[] data, int offset, int maxSize, int flags, AddressHolder holder) throws IOException;
    final native int recvFrom1(long buf, int maxSize, int flags, AddressHolder holder) throws IOException;
    final native int sendMsg0(byte[] data, int cmsgType, int[] cmsgData, int flags) throws IOException;
    final native int recvMsg0(byte[] data, Msg msg, int flags) throws IOException;
}
