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
public class JavaDatagramSocket extends SelectableJavaSocket {
    DatagramChannel ch;

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
    public final JavaSocket accept() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void connect(InetAddress address, int port) throws IOException {
        // It will be quite easy to add support for "connected" mode if it is needed in future
        throw new UnsupportedOperationException();
    }

    @Override
    public final void bind(InetAddress address, int port, int backlog) throws IOException {
        ch.bind(new InetSocketAddress(address, port));
    }

    @Override
    public final void listen(int backlog) throws IOException {
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
    public int send(ByteBuffer data, int flags, InetAddress address, int port) throws IOException {
        return ch.send(data, new InetSocketAddress(address, port));
    }

    @Override
    public final int readRaw(long buf, int count, int flags) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int read(byte[] data, int offset, int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetSocketAddress recv(ByteBuffer buffer, int flags) throws IOException {
        return (InetSocketAddress)ch.receive(buffer);
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
        // Ignore
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
    public Socket ssl(SslContext context) {
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
