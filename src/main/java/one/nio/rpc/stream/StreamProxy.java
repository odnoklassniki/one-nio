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

package one.nio.rpc.stream;

import one.nio.net.Socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public final class StreamProxy<S, R> implements RpcStream, BidiStream<S, R> {
    public final StreamHandler handler;

    public long bytesRead;
    public long bytesWritten;

    StreamProxy(StreamHandler handler) {
        this.handler = handler;
    }

    // BaseStream

    public final Socket socket() {
        return null;
    }

    public final InetSocketAddress getLocalAddress() {
        return null;
    }

    public final InetSocketAddress getRemoteAddress() {
        return null;
    }

    public final long getBytesRead() {
        return bytesRead;
    }

    public final long getBytesWritten() {
        return bytesWritten;
    }

    public final void invalidate() {
        // Nothing to do
    }

    // ObjectInput

    public final void read(ByteBuffer buf) throws IOException {
        throw exception();
    }

    public final Object readObject() throws IOException {
        throw exception();
    }

    public final int read() throws IOException {
        throw exception();
    }

    public final int read(byte[] b) throws IOException {
        throw exception();
    }

    public final int read(byte[] b, int off, int len) throws IOException {
        throw exception();
    }

    public final long skip(long n) throws IOException {
        throw exception();
    }

    public final int available() {
        return 0;
    }

    public final void readFully(byte[] b) throws IOException {
        throw exception();
    }

    public final void readFully(byte[] b, int off, int len) throws IOException {
        throw exception();
    }

    public final int skipBytes(int n) throws IOException {
        throw exception();
    }

    public final boolean readBoolean() throws IOException {
        throw exception();
    }

    public final byte readByte() throws IOException {
        throw exception();
    }

    public final int readUnsignedByte() throws IOException {
        throw exception();
    }

    public final short readShort() throws IOException {
        throw exception();
    }

    public final int readUnsignedShort() throws IOException {
        throw exception();
    }

    public final char readChar() throws IOException {
        throw exception();
    }

    public final int readInt() throws IOException {
        throw exception();
    }

    public final long readLong() throws IOException {
        throw exception();
    }

    public final float readFloat() throws IOException {
        throw exception();
    }

    public final double readDouble() throws IOException {
        throw exception();
    }

    public final String readLine() throws IOException {
        throw exception();
    }

    public final String readUTF() throws IOException {
        throw exception();
    }

    // ObjectOutput

    public final void write(ByteBuffer buf) throws IOException {
        throw exception();
    }

    public final void writeObject(Object obj) throws IOException {
        throw exception();
    }

    public final void write(int b) throws IOException {
        throw exception();
    }

    public final void write(byte[] b) throws IOException {
        throw exception();
    }

    public final void write(byte[] b, int off, int len) throws IOException {
        throw exception();
    }

    public final void writeBoolean(boolean v) throws IOException {
        throw exception();
    }

    public final void writeByte(int v) throws IOException {
        throw exception();
    }

    public final void writeShort(int v) throws IOException {
        throw exception();
    }

    public final void writeChar(int v) throws IOException {
        throw exception();
    }

    public final void writeInt(int v) throws IOException {
        throw exception();
    }

    public final void writeLong(long v) throws IOException {
        throw exception();
    }

    public final void writeFloat(float v) throws IOException {
        throw exception();
    }

    public final void writeDouble(double v) throws IOException {
        throw exception();
    }

    public final void writeBytes(String s) throws IOException {
        throw exception();
    }

    public final void writeChars(String s) throws IOException {
        throw exception();
    }

    public final void writeUTF(String s) throws IOException {
        throw exception();
    }

    public final void flush() throws IOException {
        throw exception();
    }

    public final void close() {
        // Nothing to do
    }

    // BidiStream

    public final R receive() throws IOException {
        throw exception();
    }

    public final void send(S object) throws IOException {
        throw exception();
    }

    public final R sendAndGet(S object) throws IOException {
        throw exception();
    }

    private static IOException exception() {
        return new IOException("Must not be called");
    }
}
