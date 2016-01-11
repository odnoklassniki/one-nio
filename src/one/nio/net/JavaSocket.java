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
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

final class JavaSocket extends Socket {
    SocketChannel ch;

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
        ch.connect(new InetSocketAddress(address, port));
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
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
    public final int readRaw(long buf, int count, int flags) throws IOException {
        int result = ch.read(DirectMemory.wrap(buf, count));
        if (result < 0) {
            throw new SocketException("Socket closed");
        }
        return result;
    }

    @Override
    public final int read(byte[] data, int offset, int count) throws IOException {
        int result = ch.read(ByteBuffer.wrap(data, offset, count));
        if (result < 0) {
            throw new SocketException("Socket closed");
        }
        return result;
    }

    @Override
    public final void readFully(byte[] data, int offset, int count) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, count);
        while (buffer.hasRemaining()) {
            if (ch.read(buffer) < 0) {
                throw new SocketException("Socket closed");
            }
        }
    }

    @Override
    public final long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return file.getChannel().transferTo(offset, count, ch);
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
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public final void setKeepAlive(boolean keepAlive) {
        try {
            ch.socket().setKeepAlive(keepAlive);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final void setNoDelay(boolean noDelay) {
        try {
            ch.socket().setTcpNoDelay(noDelay);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final void setDeferAccept(boolean deferAccept) {
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
    public final void setRecvBuffer(int recvBuf) {
        try {
            ch.socket().setReceiveBufferSize(recvBuf);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public final void setSendBuffer(int sendBuf) {
        try {
            ch.socket().setSendBufferSize(sendBuf);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public byte[] getOption(int level, int option) {
        return null;
    }

    @Override
    public boolean setOption(int level, int option, byte[] value) {
        return false;
    }

    @Override
    public final InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) ch.socket().getLocalSocketAddress();
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) ch.socket().getRemoteSocketAddress();
    }

    @Override
    public Socket ssl(SslContext context) {
        return this;
    }

    @Override
    public SslContext getSslContext() {
        return null;
    }
}
