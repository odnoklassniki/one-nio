/*
 * Copyright 2015-2017 Odnoklassniki Ltd, Mail.Ru Group
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
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;

/**
 * @author ivan.grigoryev
 */
// cannot have a single class for UDP/TCP due to DatagramChannel and SocketChannel not having a suitable shared base
final class JavaDatagramSocket extends SelectableJavaSocket {
    final DatagramChannel ch;

    JavaDatagramSocket() throws IOException {
        this.ch = DatagramChannel.open();
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
        ch.connect(new InetSocketAddress(address, port));
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
        return ch.write(DirectMemory.wrap(buf, count));
    }

    @Override
    public final int write(byte[] data, int offset, int count, int flags) throws IOException {
        return ch.write(ByteBuffer.wrap(data, offset, count));
    }

    @Override
    public final void writeFully(byte[] data, int offset, int count) throws IOException {
        ch.write(ByteBuffer.wrap(data, offset, count));
    }

    @Override
    public final int send(ByteBuffer data, int flags, InetAddress address, int port) throws IOException {
        return ch.send(data, new InetSocketAddress(address, port));
    }

    @Override
    public final int readRaw(long buf, int count, int flags) throws IOException {
        int result = ch.read(DirectMemory.wrap(buf, count));
        if (result < 0) {
            throw new SocketClosedException();
        }
        return result;
    }

    @Override
    public final int read(byte[] data, int offset, int count, int flags) throws IOException {
        int result = ch.read(ByteBuffer.wrap(data, offset, count));
        if (result < 0) {
            throw new SocketClosedException();
        }
        return result;
    }

    @Override
    public final InetSocketAddress recv(ByteBuffer buffer, int flags) throws IOException {
        return (InetSocketAddress) ch.receive(buffer);
    }

    @Override
    public final void readFully(byte[] data, int offset, int count) throws IOException {
        ByteBuffer dst = ByteBuffer.wrap(data, offset, count);
        while (dst.hasRemaining()) {
            int bytes = ch.read(dst);
            if (bytes < 0) {
                throw new SocketClosedException();
            }
        }
    }

    @Override
    public final long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return file.getChannel().transferTo(offset, count, ch);
    }

    @Override
    public final int read(ByteBuffer dst) throws IOException {
        return ch.read(dst);
    }

    @Override
    public final int write(ByteBuffer src) throws IOException {
        return ch.write(src);
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
    public final void setTcpFastOpen(boolean tcpFastOpen) {
        // Ignore
    }

    @Override
    public final void setDeferAccept(boolean deferAccept) {
        // Ignore
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
    public final void setRecvBuffer(int recvBuf) {
        try {
            ch.setOption(StandardSocketOptions.SO_RCVBUF, recvBuf);
        } catch (IOException e) {
            // Ignore
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
    public final void setSendBuffer(int sendBuf) {
        try {
            ch.setOption(StandardSocketOptions.SO_SNDBUF, sendBuf);
        } catch (IOException e) {
            // Ignore
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
    public SelectableChannel getSelectableChannel() {
        return ch;
    }
}
