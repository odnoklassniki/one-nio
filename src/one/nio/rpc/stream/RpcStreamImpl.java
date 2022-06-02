/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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
import one.nio.serial.ObjectInputChannel;
import one.nio.serial.ObjectOutputChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RpcStreamImpl<S, R> implements RpcStream, BidiStream<S, R> {
    protected final Socket socket;
    protected final ObjectInputChannel in;
    protected final ObjectOutputChannel out;
    protected boolean error;

    public RpcStreamImpl(Socket socket) {
        this.socket = socket;
        this.in = new ObjectInputChannel(socket);
        this.out = new ObjectOutputChannel(socket);
    }

    // BaseStream

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public long getBytesRead() {
        return in.getBytesRead();
    }

    @Override
    public long getBytesWritten() {
        return out.getBytesWritten();
    }

    @Override
    public void invalidate() {
        error = true;
    }

    // ObjectInput

    @Override
    public void read(ByteBuffer buf) throws IOException {
        try {
            in.read(buf);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public Object readObject() throws IOException, ClassNotFoundException {
        try {
            Object result = in.readObject();
            in.reset();
            return result;
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int read() throws IOException {
        try {
            return in.read();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        try {
            return in.read(b);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            return in.read(b, off, len);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        try {
            return in.skip(n);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int available() {
        return in.available();
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        try {
            in.readFully(b);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        try {
            in.readFully(b, off, len);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        try {
            return in.skipBytes(n);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public boolean readBoolean() throws IOException {
        try {
            return in.readBoolean();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public byte readByte() throws IOException {
        try {
            return in.readByte();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int readUnsignedByte() throws IOException {
        try {
            return in.readUnsignedByte();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public short readShort() throws IOException {
        try {
            return in.readShort();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int readUnsignedShort() throws IOException {
        try {
            return in.readUnsignedShort();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public char readChar() throws IOException {
        try {
            return in.readChar();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public int readInt() throws IOException {
        try {
            return in.readInt();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public long readLong() throws IOException {
        try {
            return in.readLong();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public float readFloat() throws IOException {
        try {
            return in.readFloat();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public double readDouble() throws IOException {
        try {
            return in.readDouble();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public String readLine() throws IOException {
        try {
            return in.readLine();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public String readUTF() throws IOException {
        try {
            return in.readUTF();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    // ObjectOutput

    @Override
    public void write(ByteBuffer buf) throws IOException {
        try {
            out.write(buf);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeObject(Object obj) throws IOException {
        try {
            out.writeObject(obj);
            out.reset();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            out.write(b);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            out.write(b);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            out.write(b, off, len);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        try {
            out.writeBoolean(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeByte(int v) throws IOException {
        try {
            out.writeByte(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeShort(int v) throws IOException {
        try {
            out.writeShort(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeChar(int v) throws IOException {
        try {
            out.writeChar(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeInt(int v) throws IOException {
        try {
            out.writeInt(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeLong(long v) throws IOException {
        try {
            out.writeLong(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeFloat(float v) throws IOException {
        try {
            out.writeFloat(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeDouble(double v) throws IOException {
        try {
            out.writeDouble(v);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeBytes(String s) throws IOException {
        try {
            out.writeBytes(s);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeChars(String s) throws IOException {
        try {
            out.writeChars(s);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void writeUTF(String s) throws IOException {
        try {
            out.writeUTF(s);
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            out.flush();
        } catch (Throwable e) {
            invalidate();
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            in.close();
            out.close();
        } catch (IOException e) {
            invalidate();
        }
    }

    // BidiStream

    @Override
    @SuppressWarnings("unchecked")
    public R receive() throws IOException, ClassNotFoundException {
        return (R) readObject();
    }

    @Override
    public void send(S object) throws IOException {
        writeObject(object);
    }

    @Override
    @SuppressWarnings("unchecked")
    public R sendAndGet(S object) throws IOException, ClassNotFoundException {
        writeObject(object);
        flush();
        return (R) readObject();
    }
}
