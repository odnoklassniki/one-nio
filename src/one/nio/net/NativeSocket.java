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

import one.nio.os.Mem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

class NativeSocket extends Socket {
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
    public NativeSocket accept() throws IOException {
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
    public final Socket ssl(SslContext context) throws IOException {
        return new NativeSslSocket(fd, (NativeSslContext) context, false);
    }

    @Override
    public SslContext getSslContext() {
        return null;
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
    public native void close();

    @Override
    public native int writeRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int write(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public native int readRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int read(byte[] data, int offset, int count) throws IOException;

    @Override
    public native void readFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return sendFile0(Mem.getFD(file.getFD()), offset, count);
    }

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
    public final native void setRecvBuffer(int recvBuf);

    @Override
    public final native void setSendBuffer(int sendBuf);

    @Override
    public final native byte[] getOption(int level, int option);

    @Override
    public final native boolean setOption(int level, int option, byte[] value);

    static native int socket0() throws IOException;
    native int accept0() throws IOException;
    native void connect0(byte[] address, int port) throws IOException;
    native void bind0(byte[] address, int port, int backlog) throws IOException;
    native long sendFile0(int sourceFD, long offset, long count) throws IOException;
    native int getsockname(byte[] buffer);
    native int getpeername(byte[] buffer);
}
