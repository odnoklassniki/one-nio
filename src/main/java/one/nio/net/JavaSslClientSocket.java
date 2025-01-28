/*
 * Copyright 2025 VK
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import javax.net.ssl.SSLSocket;

import one.nio.mem.DirectMemory;

public final class JavaSslClientSocket extends Socket {
    private final SSLSocket socket;
    private final JavaSslClientContext sslContext;
    private volatile WritableByteChannel outCh;
    private volatile ReadableByteChannel inCh;
    private volatile OutputStream outputStream;
    private volatile InputStream inputStream;

    public JavaSslClientSocket(JavaSslClientContext sslContext) {
        try {
            this.sslContext = sslContext;
            this.socket = this.sslContext.createSocket();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Socket accept() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(InetAddress address, int port) throws IOException {
        this.socket.connect(new InetSocketAddress(address, port));
        this.outputStream = socket.getOutputStream();
        this.outCh = Channels.newChannel(outputStream);
        this.inputStream = socket.getInputStream();
        this.inCh = Channels.newChannel(inputStream);
    }

    @Override
    public void bind(InetAddress address, int port, int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeRaw(long buf, int count, int flags) throws IOException {
        return outCh.write(DirectMemory.wrap(buf, count));
    }

    @Override
    public int write(byte[] data, int offset, int count, int flags) throws IOException {
        return outCh.write(ByteBuffer.wrap(data, offset, count));
    }

    @Override
    public void writeFully(byte[] data, int offset, int count) throws IOException {
        outputStream.write(data, offset, count);
    }

    @Override
    public int send(ByteBuffer src, int flags, InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int readRaw(long buf, int count, int flags) throws IOException {
        return inCh.read(DirectMemory.wrap(buf, count));
    }

    @Override
    public int read(byte[] data, int offset, int count, int flags) throws IOException {
        return inCh.read(ByteBuffer.wrap(data, offset, count));
    }

    @Override
    public void readFully(byte[] data, int offset, int count) throws IOException {
        while (count > 0) {
            int bytes = inputStream.read(data, offset, count);
            if (bytes < 0) {
                throw new SocketClosedException();
            }
            offset += bytes;
            count -= bytes;
        }
    }

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return file.getChannel().transferTo(offset, count, outCh);
    }

    @Override
    public InetSocketAddress recv(ByteBuffer dst, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sendMsg(Msg msg, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int recvMsg(Msg msg, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlocking(boolean blocking) {
        // Ignore
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public void setTimeout(int timeout) {
        try {
            socket.setSoTimeout(timeout);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public int getTimeout() {
        try {
            return socket.getSoTimeout();
        } catch (SocketException e) {
            return 0;
        }
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        try {
            socket.setKeepAlive(keepAlive);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public boolean getKeepAlive() {
        try {
            return socket.getKeepAlive();
        } catch (SocketException e) {
            return false;
        }
    }

    @Override
    public void setNoDelay(boolean noDelay) {
        try {
            socket.setTcpNoDelay(noDelay);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public boolean getNoDelay() {
        try {
            return socket.getTcpNoDelay();
        } catch (SocketException e) {
            return false;
        }
    }

    @Override
    public void setTcpFastOpen(boolean tcpFastOpen) {
        // Ignore
    }

    @Override
    public boolean getTcpFastOpen() {
        return false;
    }

    @Override
    public void setDeferAccept(boolean deferAccept) {
        // Ignore
    }

    @Override
    public boolean getDeferAccept() {
        return false;
    }

    @Override
    public void setReuseAddr(boolean reuseAddr, boolean reusePort) {
        try {
            socket.setReuseAddress(reuseAddr);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public boolean getReuseAddr() {
        try {
            return socket.getReuseAddress();
        } catch (SocketException e) {
            return false;
        }
    }

    @Override
    public boolean getReusePort() {
        return false;
    }

    @Override
    public void setRecvBuffer(int recvBuf) {
        try {
            socket.setReceiveBufferSize(recvBuf);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public int getRecvBuffer() {
        try {
            return socket.getReceiveBufferSize();
        } catch (SocketException e) {
            return 0;
        }
    }

    @Override
    public void setSendBuffer(int sendBuf) {
        try {
            socket.setSendBufferSize(sendBuf);
        } catch (SocketException e) {
            // Ignore
        }
    }

    @Override
    public int getSendBuffer() {
        try {
            return socket.getSendBufferSize();
        } catch (SocketException e) {
            return 0;
        }
    }

    @Override
    public void setTos(int tos) {
        // Ignore
    }

    @Override
    public int getTos() {
        return 0;
    }

    @Override
    public byte[] getOption(int level, int option) {
        return new byte[0];
    }

    @Override
    public boolean setOption(int level, int option, byte[] value) {
        return false;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress(socket.getLocalAddress(), socket.getPort());
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    @Override
    public Socket sslWrap(SslContext context) {
        return this;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return inCh.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return outCh.write(src);
    }

    @Override
    public Socket sslUnwrap() {
        return this;
    }

    @Override
    public SslContext getSslContext() {
        return sslContext;
    }

    @Override
    public <T> T getSslOption(SslOption<T> option) {
        return null;
    }

    @Override
    public void listen(int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }
}
