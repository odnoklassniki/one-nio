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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import one.nio.util.JavaInternals;

final class JavaServerSocket extends SelectableJavaSocket {
    private static final SocketOption<Boolean> SO_REUSEPORT_COMPAT = findReusePortOption();

    final ServerSocketChannel ch;

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
        SocketChannel accepted = ch.accept();
        return accepted != null ? new JavaSocket(accepted) : null;
    }

    @Override
    public final void connect(InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
        ch.bind(new InetSocketAddress(address, port), backlog);
    }

    @Override
    public final void listen(int backlog) throws IOException {
        // Java Socket starts listening in bind()
    }

    @Override
    public final int writeRaw(long buf, int count, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int write(byte[] data, int offset, int count, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void writeFully(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int send(ByteBuffer src, int flags, InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int readRaw(long buf, int count, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int read(byte[] data, int offset, int count, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final InetSocketAddress recv(ByteBuffer dst, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void readFully(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int read(ByteBuffer dst) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sendMsg(Msg msg, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int recvMsg(Msg msg, int flags) throws IOException {
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
    public boolean isBlocking() {
        return ch.isBlocking();
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
    public int getTimeout() {
        try {
            return ch.socket().getSoTimeout();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public final void setKeepAlive(boolean keepAlive) {
        // Ignore
    }

    @Override
    public boolean getKeepAlive() {
        return false;
    }

    @Override
    public final void setNoDelay(boolean noDelay) {
        // Ignore
    }

    @Override
    public boolean getNoDelay() {
        return false;
    }

    @Override
    public final void setTcpFastOpen(boolean tcpFastOpen) {
        // Ignore
    }

    @Override
    public boolean getTcpFastOpen() {
        return false;
    }

    @Override
    public final void setDeferAccept(boolean deferAccept) {
        // Ignore
    }

    @Override
    public boolean getDeferAccept() {
        return false;
    }

    @Override
    public final void setReuseAddr(boolean reuseAddr, boolean reusePort) {
        try {
            ch.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddr);
            if (SO_REUSEPORT_COMPAT != null && ch.supportedOptions().contains(SO_REUSEPORT_COMPAT)) {
                ch.setOption(SO_REUSEPORT_COMPAT, reusePort);
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public boolean getReuseAddr() {
        try {
            return ch.getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean getReusePort() {
        try {
            return SO_REUSEPORT_COMPAT != null && ch.supportedOptions().contains(SO_REUSEPORT_COMPAT)
                    ? ch.getOption(SO_REUSEPORT_COMPAT)
                    : false;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public final void setRecvBuffer(int recvBuf) {
        try {
            ch.setOption(StandardSocketOptions.SO_RCVBUF, recvBuf);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public int getRecvBuffer() {
        try {
            return ch.getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public final void setTos(int tos) {
        try {
            ch.setOption(StandardSocketOptions.IP_TOS, tos);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public int getTos() {
        try {
            return ch.getOption(StandardSocketOptions.IP_TOS);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public final void setSendBuffer(int sendBuf) {
        // See sun.nio.ch.ServerSocketChannelImpl.supportedOptions
    }

    @Override
    public int getSendBuffer() {
        // See sun.nio.ch.ServerSocketChannelImpl.supportedOptions
        return 0;
    }

    @Override
    public final byte[] getOption(int level, int option) {
        return null;
    }

    @Override
    public final boolean setOption(int level, int option, byte[] value) {
        return false;
    }

    @Override
    public final InetSocketAddress getLocalAddress() {
        try {
            return (InetSocketAddress) ch.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket sslWrap(SslContext context) {
        return this;
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
    public SelectableChannel getSelectableChannel() {
        return ch;
    }

    private static SocketOption<Boolean> findReusePortOption() {
        try {
            Field reusePortField = JavaInternals.findField(StandardSocketOptions.class, "SO_REUSEPORT");
            if (reusePortField != null) {
                return (SocketOption<Boolean>) reusePortField.get(null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
