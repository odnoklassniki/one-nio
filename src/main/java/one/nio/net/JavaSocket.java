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

import one.nio.mem.DirectMemory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

final class JavaSocket extends SelectableJavaSocket {
    final SocketChannel ch;
    int timeout;

    JavaSocket() throws IOException {
        this.ch = SocketChannel.open();
    }

    JavaSocket(SocketChannel ch) {
        this.ch = ch;
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
    public final Socket accept() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void connect(InetAddress address, int port) throws IOException {
        ch.socket().connect(new InetSocketAddress(address, port), ch.socket().getSoTimeout());
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
        ch.bind(new InetSocketAddress(address, port));
    }

    @Override
    public final void listen(int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int writeRaw(long buf, int count, int flags) throws IOException {
        checkTimeout(POLL_WRITE, timeout);
        return ch.write(DirectMemory.wrap(buf, count));
    }

    @Override
    public final int write(byte[] data, int offset, int count, int flags) throws IOException {
        checkTimeout(POLL_WRITE, timeout);
        return ch.write(ByteBuffer.wrap(data, offset, count));
    }

    @Override
    public final void writeFully(byte[] data, int offset, int count) throws IOException {
        ch.socket().getOutputStream().write(data, offset, count);
    }

    @Override
    public final int send(ByteBuffer src, int flags, InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int readRaw(long buf, int count, int flags) throws IOException {
        checkTimeout(POLL_READ, timeout);
        int result = ch.read(DirectMemory.wrap(buf, count));
        if (result < 0) {
            throw new SocketClosedException();
        }
        return result;
    }

    @Override
    public final int read(byte[] data, int offset, int count, int flags) throws IOException {
        checkTimeout(POLL_READ, timeout);
        int result = ch.read(ByteBuffer.wrap(data, offset, count));
        if (result < 0) {
            throw new SocketClosedException();
        }
        return result;
    }

    @Override
    public final InetSocketAddress recv(ByteBuffer dst, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void readFully(byte[] data, int offset, int count) throws IOException {
        InputStream in = ch.socket().getInputStream();
        while (count > 0) {
            int bytes = in.read(data, offset, count);
            if (bytes < 0) {
                throw new SocketClosedException();
            }
            offset += bytes;
            count -= bytes;
        }
    }

    @Override
    public final long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return file.getChannel().transferTo(offset, count, ch);
    }

    @Override
    public final int read(ByteBuffer dst) throws IOException {
        checkTimeout(POLL_READ, timeout);
        return ch.read(dst);
    }

    @Override
    public final int write(ByteBuffer src) throws IOException {
        checkTimeout(POLL_WRITE, timeout);
        return ch.write(src);
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
        this.timeout = timeout;
    }

    @Override
    public int getTimeout() {
        try {
            return ch.socket().getSoTimeout();
        } catch (SocketException e) {
            return 0;
        }
    }

    @Override
    public final void setKeepAlive(boolean keepAlive) {
        try {
            ch.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public boolean getKeepAlive() {
        try {
            return ch.getOption(StandardSocketOptions.SO_KEEPALIVE);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public final void setNoDelay(boolean noDelay) {
        try {
            ch.setOption(StandardSocketOptions.TCP_NODELAY, noDelay);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public boolean getNoDelay() {
        try {
            return ch.getOption(StandardSocketOptions.TCP_NODELAY);
        } catch (IOException e) {
            return false;
        }
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
        return false;
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
    public final void setSendBuffer(int sendBuf) {
        try {
            ch.setOption(StandardSocketOptions.SO_SNDBUF, sendBuf);
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public int getSendBuffer() {
        try {
            return ch.getOption(StandardSocketOptions.SO_SNDBUF);
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
        try {
            return (InetSocketAddress) ch.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
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
}
